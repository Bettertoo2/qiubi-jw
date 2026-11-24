package cn.gdou.edu.timetable.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class CasLoginFormState(
    val lt: String,
    val execution: String,
    val eventId: String,
    val cllt: String,
    val dllt: String,
    val pwdEncryptSalt: String,
    val needCaptcha: Boolean = false,
    val alreadyAuthenticated: Boolean = false
)

sealed interface CasLoginResult {
    data class Success(val finalUrl: String) : CasLoginResult
    data class CaptchaRequired(
        val message: String,
        val captchaBytes: ByteArray,
        val nextFormState: CasLoginFormState
    ) : CasLoginResult

    data class MfaRequired(
        val message: String,
        val mfaUrl: String
    ) : CasLoginResult

    data class Failure(
        val message: String,
        val nextFormState: CasLoginFormState? = null
    ) : CasLoginResult
}

/**
 * Handles Guangdong Ocean University (GDOU) Unified Identity Authentication (CAS) login
 * targeting:
 * https://authserver.gdou.edu.cn/authserver/login?service=https%3A%2F%2Fjw.gdou.edu.cn%2Fsso%2Fjziotlogin
 */
class GdouCasAuthClient(
    private val httpClient: OkHttpClient,
    private val cookieJar: PersistentCookieJar
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun prepareLoginPage(): Result<CasLoginFormState> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(CAS_LOGIN_URL)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val html = response.body?.string().orEmpty()
                if (isAlreadyInJwSystem(finalUrl, html)) {
                    return@runCatching CasLoginFormState(
                        lt = "",
                        execution = "",
                        eventId = "submit",
                        cllt = "userNameLogin",
                        dllt = "generalLogin",
                        pwdEncryptSalt = "",
                        needCaptcha = false,
                        alreadyAuthenticated = true
                    )
                }
                parseCasForm(html)
            }
        }
    }

    suspend fun checkNeedCaptcha(username: String): Boolean = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext false
        runCatching {
            val url = "$CAS_CHECK_CAPTCHA_URL?username=${
                java.net.URLEncoder.encode(username.trim(), "UTF-8")
            }&_=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", CAS_LOGIN_URL)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val element = json.parseToJsonElement(body).jsonObject
                element["isNeed"]?.jsonPrimitive?.booleanOrNull == true
            }
        }.getOrDefault(false)
    }

    suspend fun fetchCaptchaImage(): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$CAS_CAPTCHA_IMG_URL?${System.currentTimeMillis()}")
                .header("Referer", CAS_LOGIN_URL)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("获取验证码失败 (HTTP ${response.code})")
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.isEmpty()) throw IllegalStateException("验证码图片为空")
                bytes
            }
        }
    }

    /**
     * Exchanges a CAS SSO ticket URL (e.g. `https://jw.gdou.edu.cn/sso/jziotlogin?ticket=ST-...`)
     * directly via our IPv4-preferred OkHttpClient so jw.gdou.edu.cn's broken IPv6 endpoint on
     * 5G cellular networks never breaks WebView login with ERR_CONNECTION_REFUSED.
     */
    suspend fun exchangeSsoTicket(ssoUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = if (ssoUrl.startsWith("http://", ignoreCase = true) &&
                ssoUrl.contains("gdou.edu.cn", ignoreCase = true)
            ) {
                "https://" + ssoUrl.substringAfter("://")
            } else {
                ssoUrl
            }
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("Referer", CAS_LOGIN_URL)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val body = response.body?.string().orEmpty()
                runCatching {
                    Log.d("GdouAuth", "SSO ticket exchange -> code=${response.code} finalUrl=$finalUrl inJw=${isAlreadyInJwSystem(finalUrl, body)}")
                }
                finalUrl
            }
        }
    }

    /**
     * Best-effort, cancellable server-side CAS & JW logout so old TGT/JSESSIONID tickets are
     * invalidated. Uses an immutable [cookieSnapshot] and a read-only CookieJar view so logout
     * redirects can never read or mutate the live [cookieJar]. Never throws.
     */
    suspend fun serverLogout(cookieSnapshot: List<Cookie> = cookieJar.snapshotAll()) = withContext(Dispatchers.IO) {
        if (cookieSnapshot.isEmpty()) return@withContext
        val readOnlyJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                cookieSnapshot.filter { it.matches(url) }
        }
        val logoutClient = httpClient.newBuilder()
            .cookieJar(readOnlyJar)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(2500, TimeUnit.MILLISECONDS)
            .build()

        coroutineScope {
            listOf(JW_LOGOUT_URL, CAS_LOGOUT_URL).map { url ->
                async {
                    runCatching {
                        val call = logoutClient.newCall(Request.Builder().url(url).get().build())
                        suspendCancellableCoroutine<Unit> { cont ->
                            cont.invokeOnCancellation { call.cancel() }
                            call.enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    if (cont.isActive) cont.resume(Unit)
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    response.close()
                                    if (cont.isActive) cont.resume(Unit)
                                }
                            })
                        }
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun login(
        username: String,
        password: String,
        captcha: String = "",
        existingForm: CasLoginFormState? = null,
        clearOldCookies: Boolean = false
    ): CasLoginResult = withContext(Dispatchers.IO) {
        val trimmedUser = username.trim()
        if (trimmedUser.isEmpty() || password.isEmpty()) {
            return@withContext CasLoginResult.Failure("请输入学号和密码")
        }

        val canReuseExistingForm = existingForm != null &&
            !clearOldCookies &&
            existingForm.execution.isNotBlank() &&
            existingForm.pwdEncryptSalt.isNotBlank() &&
            cookieJar.hasCookieForHost("authserver.gdou.edu.cn")

        if (clearOldCookies || !canReuseExistingForm) {
            // Preserve MULTIFACTOR_BROWSER_FINGERPRINT so trusted devices don't keep re-triggering MFA
            cookieJar.clearSessionCookies(preserveMfaFingerprint = true)
        }

        val formState = if (canReuseExistingForm) {
            existingForm
        } else {
            prepareLoginPage().getOrElse { err ->
                return@withContext CasLoginResult.Failure("无法连接统一身份认证平台：${err.message ?: "网络异常"}")
            }
        }

        if (formState.alreadyAuthenticated) {
            return@withContext CasLoginResult.Success(JW_HOME_URL)
        }

        if (formState.execution.isBlank() || formState.pwdEncryptSalt.isBlank()) {
            return@withContext CasLoginResult.Failure("认证页面初始化异常，请重试或改用网页认证登录")
        }

        if (captcha.isBlank()) {
            val needCaptcha = formState.needCaptcha || checkNeedCaptcha(trimmedUser)
            if (needCaptcha) {
                val captchaBytes = fetchCaptchaImage().getOrNull() ?: ByteArray(0)
                return@withContext CasLoginResult.CaptchaRequired(
                    message = "教务系统要求输入图形验证码",
                    captchaBytes = captchaBytes,
                    nextFormState = formState.copy(needCaptcha = true)
                )
            }
        }

        val encryptedPassword = CasPasswordEncryptor.encryptPassword(
            plainPassword = password,
            pwdEncryptSalt = formState.pwdEncryptSalt
        )

        val formBody = FormBody.Builder()
            .add("username", trimmedUser)
            .add("password", encryptedPassword)
            .add("captcha", captcha.trim())
            .add("_eventId", formState.eventId.ifBlank { "submit" })
            .add("cllt", "userNameLogin")
            .add("dllt", formState.dllt.ifBlank { "generalLogin" })
            .add("lt", formState.lt)
            .add("execution", formState.execution)
            .build()

        runCatching {
            Log.d(
                "GdouAuth",
                "POST login cllt=userNameLogin dllt=${formState.dllt} exec=${formState.execution} hasSalt=${formState.pwdEncryptSalt.isNotBlank()} captchaLen=${captcha.trim().length}"
            )
        }

        val postRequest = Request.Builder()
            .url(CAS_LOGIN_URL)
            .header("Referer", CAS_LOGIN_URL)
            .header("Origin", "https://authserver.gdou.edu.cn")
            .post(formBody)
            .build()

        // Do NOT auto-follow redirects on the POST so a transient drop on jw.gdou.edu.cn
        // never replays the consumed CAS `execution` form — instead hand the 302 Location
        // to our idempotent GET redirect/SSO ticket exchange.
        val noRedirectClient = httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        try {
            var redirectLocation: String? = null
            var initialCode = 0
            var initialFinalUrl = CAS_LOGIN_URL
            var initialBody = ""

            noRedirectClient.newCall(postRequest).execute().use { response ->
                initialCode = response.code
                initialFinalUrl = response.request.url.toString()
                if (response.isRedirect) {
                    val rawLoc = response.header("Location").orEmpty().trim()
                    if (rawLoc.isNotBlank()) {
                        redirectLocation = response.request.url.resolve(rawLoc)?.toString() ?: rawLoc
                    }
                } else {
                    initialBody = response.body?.string().orEmpty()
                }
            }

            if (!redirectLocation.isNullOrBlank()) {
                val targetUrl = redirectLocation
                runCatching {
                    Log.d("GdouAuth", "POST login 302 -> $targetUrl")
                }
                if (isMfaChallengePage(targetUrl, "")) {
                    return@withContext CasLoginResult.MfaRequired(
                        message = "学校统一身份认证要求完成多因子/二次认证，正在为您打开网页认证页面",
                        mfaUrl = targetUrl
                    )
                }
                val followReq = Request.Builder()
                    .url(targetUrl)
                    .header("Referer", CAS_LOGIN_URL)
                    .get()
                    .build()
                httpClient.newCall(followReq).execute().use { followResp ->
                    initialCode = followResp.code
                    initialFinalUrl = followResp.request.url.toString()
                    initialBody = followResp.body?.string().orEmpty()
                }
            }

            runCatching {
                Log.d("GdouAuth", "POST login resolved code=$initialCode finalUrl=$initialFinalUrl")
            }

            if (isAlreadyInJwSystem(initialFinalUrl, initialBody)) {
                return@withContext CasLoginResult.Success(initialFinalUrl)
            }

            // Detect GDOU Multi-Factor Authentication (reAuthCheck / multifactor / mfa)
            if (isMfaChallengePage(initialFinalUrl, initialBody)) {
                return@withContext CasLoginResult.MfaRequired(
                    message = "学校统一身份认证要求完成多因子/二次认证，正在为您打开网页认证页面",
                    mfaUrl = initialFinalUrl
                )
            }

            // Sometimes CAS authenticates TGT and lands on authserver portal; trigger jziotlogin SSO explicitly
            if (initialFinalUrl.contains("authserver.gdou.edu.cn") &&
                !initialFinalUrl.contains("/authserver/login")
            ) {
                val ssoRequest = Request.Builder().url(JW_SSO_URL).get().build()
                httpClient.newCall(ssoRequest).execute().use { ssoResp ->
                    val ssoFinal = ssoResp.request.url.toString()
                    val ssoBody = ssoResp.body?.string().orEmpty()
                    if (isAlreadyInJwSystem(ssoFinal, ssoBody)) {
                        return@withContext CasLoginResult.Success(ssoFinal)
                    }
                    if (isMfaChallengePage(ssoFinal, ssoBody)) {
                        return@withContext CasLoginResult.MfaRequired(
                            message = "学校统一身份认证要求完成多因子/二次认证，正在为您打开网页认证页面",
                            mfaUrl = ssoFinal
                        )
                    }
                }
            }

            val errorMessage = extractCasErrorMessage(initialBody).ifBlank {
                "登录失败（HTTP $initialCode），请核对学号、密码或验证码"
            }
            val refreshedForm = runCatching { parseCasForm(initialBody) }.getOrNull()
            val nowNeedsCaptcha = (refreshedForm?.needCaptcha == true) ||
                errorMessage.contains("验证码") ||
                checkNeedCaptcha(trimmedUser)

            if (nowNeedsCaptcha && refreshedForm != null) {
                val captchaBytes = fetchCaptchaImage().getOrNull() ?: ByteArray(0)
                CasLoginResult.CaptchaRequired(
                    message = errorMessage,
                    captchaBytes = captchaBytes,
                    nextFormState = refreshedForm.copy(needCaptcha = true)
                )
            } else {
                CasLoginResult.Failure(
                    message = errorMessage,
                    nextFormState = refreshedForm
                )
            }
        } catch (e: Exception) {
            val target = GdouHttpClientFactory.lastTargetHint()
            val targetSuffix = if (target.isNotBlank()) " @ $target" else ""
            CasLoginResult.Failure("登录请求失败：${e.message ?: "请检查网络连接"}（${e.javaClass.simpleName}$targetSuffix）")
        }
    }

    internal fun isMfaChallengePage(finalUrl: String, html: String): Boolean =
        Companion.isMfaChallengePage(finalUrl, html)

    internal fun isAlreadyInJwSystem(finalUrl: String, html: String): Boolean =
        Companion.isAlreadyInJwSystem(finalUrl, html)

    internal fun parseCasForm(html: String): CasLoginFormState =
        Companion.parseCasForm(html)

    internal fun extractCasErrorMessage(html: String): String =
        Companion.extractCasErrorMessage(html)

    companion object {
        const val JW_BASE_URL = "https://jw.gdou.edu.cn"
        const val JW_SSO_URL = "https://jw.gdou.edu.cn/sso/jziotlogin"
        const val JW_HOME_URL = "https://jw.gdou.edu.cn/xtgl/index_initMenu.html"
        const val JW_LOGOUT_URL = "https://jw.gdou.edu.cn/logout"
        const val CAS_LOGIN_URL =
            "https://authserver.gdou.edu.cn/authserver/login?service=https%3A%2F%2Fjw.gdou.edu.cn%2Fsso%2Fjziotlogin"
        const val CAS_LOGOUT_URL = "https://authserver.gdou.edu.cn/authserver/logout"
        const val CAS_CHECK_CAPTCHA_URL = "https://authserver.gdou.edu.cn/authserver/checkNeedCaptcha.htl"
        const val CAS_CAPTCHA_IMG_URL = "https://authserver.gdou.edu.cn/authserver/getCaptcha.htl"

        internal fun isMfaChallengePage(finalUrl: String, html: String): Boolean {
            val urlLower = finalUrl.lowercase()
            if (urlLower.contains("reauth") ||
                urlLower.contains("/mfa") ||
                urlLower.contains("multifactor") ||
                urlLower.contains("secondauth") ||
                urlLower.contains("safeauth")
            ) {
                return true
            }
            return (html.contains("多因子认证") || html.contains("二次认证") || html.contains("reAuth")) &&
                !html.contains("pwdEncryptSalt")
        }

        internal fun isAlreadyInJwSystem(finalUrl: String, html: String): Boolean {
            val lower = finalUrl.lowercase()
            val isJwContentUrl = (lower.startsWith("https://jw.gdou.edu.cn") || lower.startsWith("http://jw.gdou.edu.cn")) &&
                !lower.contains("login_slogin") &&
                !lower.contains("login_") &&
                !lower.contains("/sso/") &&
                !lower.contains("logout") &&
                !lower.contains("authserver")
            if (isJwContentUrl && !html.contains("pwdEncryptSalt") && !html.contains("login_slogin")) {
                return true
            }
            return (html.contains("index_initMenu") || html.contains("教学管理信息服务平台")) &&
                !html.contains("pwdEncryptSalt") &&
                !lower.contains("login_slogin") &&
                !lower.contains("logout")
        }

        internal fun parseCasForm(html: String): CasLoginFormState {
            val doc = Jsoup.parse(html)
            // Always target the password login form (#pwdFromId on PC, #pwdLoginDiv form on mobile)
            // and NEVER pick #phoneLoginDiv's dynamicLogin form!
            val pwdForm = doc.selectFirst("#pwdFromId")
                ?: doc.selectFirst("#pwdLoginDiv form")
                ?: doc.select("form.loginFromClass").firstOrNull { f ->
                    f.selectFirst("input[name=cllt]")?.attr("value") == "userNameLogin" ||
                        f.selectFirst("#pwdEncryptSalt") != null
                }
                ?: doc
            val lt = pwdForm.selectFirst("input[name=lt]")?.attr("value").orEmpty()
            val execution = pwdForm.selectFirst("input[name=execution]")?.attr("value")
                ?: doc.selectFirst("input[name=execution]")?.attr("value").orEmpty()
            val eventId = pwdForm.selectFirst("input[name=_eventId]")?.attr("value").orEmpty()
                .ifBlank { "submit" }
            val dllt = pwdForm.selectFirst("input[name=dllt]")?.attr("value").orEmpty()
                .ifBlank { "generalLogin" }
            val salt = pwdForm.selectFirst("#pwdEncryptSalt")?.attr("value")
                ?: doc.selectFirst("#pwdEncryptSalt")?.attr("value").orEmpty()

            val needCaptchaScript = Regex("""var\s+needCaptcha\s*=\s*"([^"]*)"""")
                .find(html)?.groupValues?.getOrNull(1)
            val needCaptcha = needCaptchaScript.equals("true", ignoreCase = true)

            return CasLoginFormState(
                lt = lt,
                execution = execution,
                eventId = eventId,
                cllt = "userNameLogin",
                dllt = dllt,
                pwdEncryptSalt = salt,
                needCaptcha = needCaptcha
            )
        }

        internal fun extractCasErrorMessage(html: String): String {
            val doc = Jsoup.parse(html)
            val candidates = listOf(
                doc.selectFirst("#pwdFromId #showErrorTip")?.text(),
                doc.selectFirst("#showErrorTip")?.text(),
                doc.selectFirst("#formErrorTip2")?.text(),
                doc.selectFirst(".form-error")?.text(),
                doc.selectFirst("#msg")?.text()
            )
            return candidates.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        }
    }
}
