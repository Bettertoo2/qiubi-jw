package cn.gdou.edu.timetable.ui.login

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.atomic.AtomicInteger
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.gdou.edu.timetable.BuildConfig
import cn.gdou.edu.timetable.data.remote.GdouCasAuthClient

private const val MOBILE_CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

private const val DESKTOP_CHROME_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

private sealed interface JsDialogState {
    data class Alert(val message: String, val result: JsResult) : JsDialogState
    data class Confirm(val message: String, val result: JsResult) : JsDialogState
    data class Prompt(val message: String, val defaultValue: String, val result: JsPromptResult) : JsDialogState
}

data class WebViewCapturedCookies(
    val jwCookies: String?,
    val authRootCookies: String?,
    val authPathCookies: String?,
    val ssoTicketUrl: String? = null
)

internal fun isJwSsoTicketUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    val isJw = lower.startsWith("https://jw.gdou.edu.cn/") || lower.startsWith("http://jw.gdou.edu.cn/")
    return isJw && lower.contains("/sso/jziotlogin") && lower.contains("ticket=")
}

/**
 * Full-featured WebView authentication screen tailored for GDOU CAS & Multi-Factor Authentication (MFA):
 * - Preserves MULTIFACTOR_BROWSER_FINGERPRINT (no async removeAllCookies race condition)
 * - Allows HTTP/HTTPS mixed content for GDOU's backend http:// redirects behind OpenResty
 * - Supports window.open / target="_blank" child popup WebViews for MFA/security flows
 * - Handles wxwork://, weixin://, alipays://, intent:// external app schemes via Android Intent
 * - Renders JS alert/confirm/prompt dialogs natively
 * - Supports one-tap switching between Mobile and Desktop User-Agent (for QR / MFA pages)
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(
    initialUrl: String = GdouCasAuthClient.CAS_LOGIN_URL,
    seedCookies: List<Pair<String, String>> = emptyList(),
    onBack: () -> Unit,
    onLoginCaptured: (WebViewCapturedCookies) -> Unit
) {
    var progress by remember { mutableIntStateOf(0) }
    var isWaitingInitialLoad by remember { mutableStateOf(true) }
    var captured by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("统一身份认证") }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var jsDialog by remember { mutableStateOf<JsDialogState?>(null) }

    var mainWebView by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    fun mergeCookieHeaders(vararg headers: String?): String? {
        val merged = LinkedHashMap<String, String>()
        for (header in headers) {
            if (header.isNullOrBlank()) continue
            for (pair in header.split(";")) {
                val idx = pair.indexOf('=')
                if (idx <= 0) continue
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    merged[name] = value
                }
            }
        }
        return merged.entries.takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun captureAndDeliverCookies(sourceUrl: String? = null, ssoTicketUrl: String? = null) {
        if (captured) return
        captured = true
        val cm = CookieManager.getInstance()
        cm.flush()
        val jwHttp = cm.getCookie("http://jw.gdou.edu.cn")
        val jwHttps = cm.getCookie("https://jw.gdou.edu.cn")
        val jwCurrent = sourceUrl?.takeIf { it.contains("jw.gdou.edu.cn", ignoreCase = true) }
            ?.let { cm.getCookie(it) }
        val combinedJw = mergeCookieHeaders(jwHttp, jwHttps, jwCurrent)
        val authRoot = cm.getCookie("https://authserver.gdou.edu.cn")
        val authPath = cm.getCookie("https://authserver.gdou.edu.cn/authserver")
        Log.d(
            "GdouWebLogin",
            "Captured cookies: hasJw=${!combinedJw.isNullOrBlank()} hasAuthRoot=${!authRoot.isNullOrBlank()} hasAuthPath=${!authPath.isNullOrBlank()} hasSsoTicket=${!ssoTicketUrl.isNullOrBlank()}"
        )
        onLoginCaptured(
            WebViewCapturedCookies(
                jwCookies = combinedJw,
                authRootCookies = authRoot,
                authPathCookies = authPath,
                ssoTicketUrl = ssoTicketUrl
            )
        )
    }

    fun tryRescueJwNavigationError(failedUrl: String?): Boolean {
        if (captured || failedUrl.isNullOrBlank()) return false
        val lower = failedUrl.lowercase()
        val isJw = lower.startsWith("https://jw.gdou.edu.cn") || lower.startsWith("http://jw.gdou.edu.cn")
        if (!isJw) return false
        if (isJwSsoTicketUrl(failedUrl)) {
            Log.d("GdouWebLogin", "Rescuing failed JW SSO ticket URL via IPv4 OkHttp: $failedUrl")
            captureAndDeliverCookies(sourceUrl = failedUrl, ssoTicketUrl = failedUrl)
            return true
        }
        val authPathCookie = CookieManager.getInstance().getCookie("https://authserver.gdou.edu.cn/authserver").orEmpty()
        if (authPathCookie.contains("CASTGC", ignoreCase = true) || authPathCookie.contains("TGC=", ignoreCase = true)) {
            Log.d("GdouWebLogin", "Rescuing failed JW navigation using CASTGC via IPv4 OkHttp")
            captureAndDeliverCookies(sourceUrl = failedUrl, ssoTicketUrl = GdouCasAuthClient.JW_SSO_URL)
            return true
        }
        return false
    }

    fun checkUrlForJwSuccess(url: String?, fromPageFinished: Boolean = false) {
        if (url.isNullOrBlank() || captured) return
        val lower = url.lowercase()
        val isOnJw = lower.startsWith("https://jw.gdou.edu.cn") || lower.startsWith("http://jw.gdou.edu.cn")
        if (!isOnJw) return
        // Never capture during intermediate SSO ticket exchange (/sso/jziotlogin), logout, or login redirect pages
        if (lower.contains("login_slogin") ||
            lower.contains("login_") ||
            lower.contains("authserver") ||
            lower.contains("/sso/") ||
            lower.contains("logout")
        ) return
        // Wait for page finish (or confirmed index_initMenu navigation) with an active JW JSESSIONID
        val cm = CookieManager.getInstance()
        val jwCookie = mergeCookieHeaders(
            cm.getCookie("http://jw.gdou.edu.cn"),
            cm.getCookie("https://jw.gdou.edu.cn"),
            cm.getCookie(url)
        ).orEmpty()
        val hasSession = jwCookie.contains("JSESSIONID", ignoreCase = true)
        if (!hasSession) return
        if (fromPageFinished || lower.contains("index_initmenu")) {
            captureAndDeliverCookies(url)
        }
    }

    fun handleCustomSchemeOrNavigation(context: Context, uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme == "http" || scheme == "https") {
            // Keep all http/https navigation inside our WebView so cookies & MFA state are preserved
            return false
        }
        return try {
            val intent = if (scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            bannerMessage = "未找到可处理「${scheme}://」的应用，请改用短信/账号或点击右上角切换电脑版完成认证"
            true
        }
    }

    fun configureCommonSettings(webView: WebView, useDesktopUa: Boolean) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = if (useDesktopUa) DESKTOP_CHROME_UA else MOBILE_CHROME_UA
        }
    }

    fun buildChromeClient(): WebChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progress = newProgress
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (!title.isNullOrBlank() && !title.startsWith("http")) {
                pageTitle = title
            }
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            if (consoleMessage != null) {
                Log.d(
                    "GdouWebLogin",
                    "CONSOLE [${consoleMessage.messageLevel()}] ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} -> ${consoleMessage.message()}"
                )
            }
            return super.onConsoleMessage(consoleMessage)
        }

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            if (result != null) {
                jsDialog = JsDialogState.Alert(message.orEmpty(), result)
                return true
            }
            return super.onJsAlert(view, url, message, result)
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            if (result != null) {
                jsDialog = JsDialogState.Confirm(message.orEmpty(), result)
                return true
            }
            return super.onJsConfirm(view, url, message, result)
        }

        override fun onJsPrompt(
            view: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult?
        ): Boolean {
            if (result != null) {
                jsDialog = JsDialogState.Prompt(message.orEmpty(), defaultValue.orEmpty(), result)
                return true
            }
            return super.onJsPrompt(view, url, message, defaultValue, result)
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            // Deny camera/mic silently only if unsupported; grant protected media ID if requested by WebView
            val safeResources = request?.resources?.filter {
                it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
            }?.toTypedArray()
            if (!safeResources.isNullOrEmpty()) {
                request.grant(safeResources)
            } else {
                request?.deny()
            }
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            Log.d("GdouWebLogin", "onCreateWindow isDialog=$isDialog isUserGesture=$isUserGesture")
            val ctx = view?.context ?: return false
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false

            val child = WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                configureCommonSettings(this, desktopMode)
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(v: WebView?, newProgress: Int) {
                        progress = newProgress
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        if (consoleMessage != null) {
                            Log.d(
                                "GdouWebLogin",
                                "POPUP_CONSOLE [${consoleMessage.messageLevel()}] ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} -> ${consoleMessage.message()}"
                            )
                        }
                        return super.onConsoleMessage(consoleMessage)
                    }

                    override fun onCloseWindow(window: WebView?) {
                        Log.d("GdouWebLogin", "POPUP onCloseWindow")
                        popupWebView?.destroy()
                        popupWebView = null
                    }

                    override fun onJsAlert(v: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        if (result != null) {
                            jsDialog = JsDialogState.Alert(message.orEmpty(), result)
                            return true
                        }
                        return super.onJsAlert(v, url, message, result)
                    }

                    override fun onJsConfirm(v: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        if (result != null) {
                            jsDialog = JsDialogState.Confirm(message.orEmpty(), result)
                            return true
                        }
                        return super.onJsConfirm(v, url, message, result)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                        val uri = request?.url ?: return false
                        if (request.isForMainFrame && isJwSsoTicketUrl(uri.toString())) {
                            Log.d("GdouWebLogin", "POPUP intercepting JW SSO ticket for IPv4 OkHttp exchange")
                            captureAndDeliverCookies(sourceUrl = uri.toString(), ssoTicketUrl = uri.toString())
                            return true
                        }
                        return handleCustomSchemeOrNavigation(ctx, uri)
                    }

                    override fun onPageFinished(v: WebView?, url: String?) {
                        super.onPageFinished(v, url)
                        if (!url.isNullOrBlank()) currentUrl = url
                        CookieManager.getInstance().flush()
                        checkUrlForJwSuccess(url, fromPageFinished = true)
                    }

                    override fun doUpdateVisitedHistory(v: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(v, url, isReload)
                        if (!url.isNullOrBlank()) currentUrl = url
                        checkUrlForJwSuccess(url, fromPageFinished = false)
                    }

                    override fun onReceivedError(
                        v: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(v, request, error)
                        if (request?.isForMainFrame == true) {
                            tryRescueJwNavigationError(request.url?.toString())
                        }
                    }
                }
            }

            popupWebView?.destroy()
            popupWebView = child
            transport.webView = child
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            if (window == popupWebView) {
                popupWebView?.destroy()
                popupWebView = null
            }
        }
    }

    // Intercept hardware Back button: close popup first, then WebView history, then exit
    BackHandler {
        val popup = popupWebView
        val main = mainWebView
        when {
            popup != null && popup.canGoBack() -> popup.goBack()
            popup != null -> {
                popup.destroy()
                popupWebView = null
            }
            main != null && main.canGoBack() -> main.goBack()
            else -> onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            popupWebView?.destroy()
            popupWebView = null
            mainWebView?.destroy()
            mainWebView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (popupWebView != null) "多因子认证窗口" else pageTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "教务统一身份认证页",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val popup = popupWebView
                        val main = mainWebView
                        when {
                            popup != null -> {
                                popup.destroy()
                                popupWebView = null
                            }
                            main != null && main.canGoBack() -> main.goBack()
                            else -> onBack()
                        }
                    }) {
                        Icon(
                            imageVector = if (popupWebView != null) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // Toggle Mobile / Desktop User-Agent (helpful for GDOU PC MFA / WeCom QR)
                    IconButton(onClick = {
                        desktopMode = !desktopMode
                        val ua = if (desktopMode) DESKTOP_CHROME_UA else MOBILE_CHROME_UA
                        mainWebView?.settings?.userAgentString = ua
                        popupWebView?.settings?.userAgentString = ua
                        bannerMessage = if (desktopMode) "已切换为电脑版网页视图" else "已切换为手机版网页视图"
                        (popupWebView ?: mainWebView)?.loadUrl(
                            if (currentUrl.contains("authserver.gdou.edu.cn/authserver/login")) {
                                GdouCasAuthClient.CAS_LOGIN_URL
                            } else {
                                currentUrl
                            }
                        )
                    }) {
                        Icon(
                            imageVector = if (desktopMode) Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer,
                            contentDescription = if (desktopMode) "切回手机版" else "切换电脑版"
                        )
                    }

                    // Safely reset all cookies & WebStorage (waits for async callback before reloading!)
                    IconButton(onClick = {
                        val cm = CookieManager.getInstance()
                        cm.removeAllCookies {
                            runCatching { WebStorage.getInstance().deleteAllData() }
                            cm.flush()
                            bannerMessage = "已清理网页登录态并重新加载登录页"
                            popupWebView?.destroy()
                            popupWebView = null
                            mainWebView?.clearCache(true)
                            mainWebView?.loadUrl(GdouCasAuthClient.CAS_LOGIN_URL)
                        }
                    }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "清除登录态")
                    }

                    IconButton(onClick = {
                        bannerMessage = null
                        (popupWebView ?: mainWebView)?.reload()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (isWaitingInitialLoad) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Helpful status / error banner (never fails silently)
            if (!bannerMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bannerMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { bannerMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "关闭提示",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Quick manual sync bar ONLY when actually navigated onto jw.gdou.edu.cn (never match ?service= query param!)
            val lowerCurrentUrl = currentUrl.lowercase()
            if ((lowerCurrentUrl.startsWith("https://jw.gdou.edu.cn") ||
                    lowerCurrentUrl.startsWith("http://jw.gdou.edu.cn")) &&
                !lowerCurrentUrl.contains("login_slogin") &&
                !lowerCurrentUrl.contains("login_") &&
                !lowerCurrentUrl.contains("/sso/") &&
                !lowerCurrentUrl.contains("authserver") &&
                !lowerCurrentUrl.contains("logout")
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "已进入教务系统，若未自动跳转请点击同步",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { captureAndDeliverCookies(currentUrl) }) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("同步数据", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        if (BuildConfig.DEBUG) {
                            WebView.setWebContentsDebuggingEnabled(true)
                        }
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)

                        WebView(context).apply {
                            // CRITICAL: Jetpack Compose AndroidView defaults child layoutParams to WRAP_CONTENT,
                            // which causes Chromium WebView to evaluate CSS 100vh = 0px and collapses GDOU's
                            // mobile MFA container (.mobile-page-center-box { height: calc(100vh - 44px) }) to 0px!
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            mainWebView = this
                            configureCommonSettings(this, desktopMode)
                            clearCache(true)
                            clearFormData()
                            clearHistory()
                            webChromeClient = buildChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val uri = request?.url ?: return false
                                    Log.d(
                                        "GdouWebLogin",
                                        "shouldOverrideUrlLoading url=$uri mainFrame=${request.isForMainFrame} redirect=${request.isRedirect}"
                                    )
                                    // Intercept CAS -> jw.gdou.edu.cn/sso/jziotlogin?ticket=... BEFORE WebView connects
                                    // so our IPv4-preferred OkHttpClient exchanges the ticket cleanly on 5G IPv6 networks.
                                    if (request.isForMainFrame && isJwSsoTicketUrl(uri.toString())) {
                                        Log.d("GdouWebLogin", "Intercepting JW SSO ticket redirect for IPv4 OkHttp exchange")
                                        captureAndDeliverCookies(sourceUrl = uri.toString(), ssoTicketUrl = uri.toString())
                                        return true
                                    }
                                    return handleCustomSchemeOrNavigation(context, uri)
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?
                                ) {
                                    super.onPageStarted(view, url, favicon)
                                    Log.d("GdouWebLogin", "onPageStarted url=$url")
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                    }
                                    bannerMessage = null
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isWaitingInitialLoad = false
                                    Log.d("GdouWebLogin", "onPageFinished url=$url")
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                    }
                                    // Guard against zero-height vh collapse on GDOU mobile MFA pages
                                    if (url != null && url.contains("authserver.gdou.edu.cn", ignoreCase = true)) {
                                        view?.evaluateJavascript(
                                            """
                                            (function(){
                                              var c = document.querySelector('.mobile-page-center-box');
                                              if (c && c.getBoundingClientRect().height < 80) {
                                                c.style.height = 'auto';
                                                c.style.minHeight = (window.innerHeight - 44) + 'px';
                                                c.style.overflowY = 'auto';
                                              }
                                              var ct = document.querySelector('.mobile-page-content-box');
                                              if (ct && ct.getBoundingClientRect().height < 80) {
                                                ct.style.maxHeight = 'none';
                                                ct.style.height = 'auto';
                                              }
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    }
                                    CookieManager.getInstance().flush()
                                    checkUrlForJwSuccess(url, fromPageFinished = true)
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView?,
                                    url: String?,
                                    isReload: Boolean
                                ) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    Log.d("GdouWebLogin", "doUpdateVisitedHistory url=$url reload=$isReload")
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                    }
                                    checkUrlForJwSuccess(url, fromPageFinished = false)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    val failedUrl = request?.url?.toString()
                                    Log.e(
                                        "GdouWebLogin",
                                        "onReceivedError url=$failedUrl mainFrame=${request?.isForMainFrame} code=${error?.errorCode} desc=${error?.description}"
                                    )
                                    if (request?.isForMainFrame == true) {
                                        isWaitingInitialLoad = false
                                        if (tryRescueJwNavigationError(failedUrl)) {
                                            return
                                        }
                                        val desc = error?.description?.toString().orEmpty()
                                        bannerMessage = "页面加载遇到问题（$desc），可点击右上角刷新或切换电脑版重试"
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    Log.w(
                                        "GdouWebLogin",
                                        "onReceivedHttpError url=${request?.url} mainFrame=${request?.isForMainFrame} status=${errorResponse?.statusCode}"
                                    )
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    Log.e(
                                        "GdouWebLogin",
                                        "onReceivedSslError url=${error?.url} primaryError=${error?.primaryError}"
                                    )
                                    isWaitingInitialLoad = false
                                    bannerMessage = "证书校验异常（错误码 ${error?.primaryError}）：${error?.url}"
                                    super.onReceivedSslError(view, handler, error)
                                }
                            }

                            // Clear any stale WebView cookies before seeding active MFA/fingerprint cookies
                            // and loading initialUrl — prevents leftover JSESSIONID/CASTGC from breaking re-login.
                            val targetWebView = this
                            cm.removeAllCookies {
                                if (mainWebView !== targetWebView) return@removeAllCookies
                                if (seedCookies.isEmpty()) {
                                    cm.flush()
                                    targetWebView.post {
                                        if (mainWebView === targetWebView) {
                                            Log.d("GdouWebLogin", "Initial loadUrl=$initialUrl seeds=0")
                                            targetWebView.loadUrl(initialUrl)
                                        }
                                    }
                                } else {
                                    val remaining = AtomicInteger(seedCookies.size)
                                    for ((cookieUrl, setCookieHeader) in seedCookies) {
                                        cm.setCookie(cookieUrl, setCookieHeader) {
                                            if (remaining.decrementAndGet() == 0) {
                                                cm.flush()
                                                targetWebView.post {
                                                    if (mainWebView === targetWebView) {
                                                        Log.d("GdouWebLogin", "Initial loadUrl=$initialUrl seeds=${seedCookies.size}")
                                                        targetWebView.loadUrl(initialUrl)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                )

                // Overlay child popup WebView when GDOU MFA uses window.open() or target="_blank"
                val activePopup = popupWebView
                if (activePopup != null) {
                    key(activePopup) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            factory = { activePopup }
                        )
                    }
                }
            }
        }
    }

    // Native Material 3 dialogs for JavaScript alert/confirm/prompt
    when (val dialog = jsDialog) {
        is JsDialogState.Alert -> {
            AlertDialog(
                onDismissRequest = {
                    dialog.result.confirm()
                    jsDialog = null
                },
                title = { Text("网页提示") },
                text = { Text(dialog.message) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog.result.confirm()
                        jsDialog = null
                    }) { Text("确定") }
                }
            )
        }

        is JsDialogState.Confirm -> {
            AlertDialog(
                onDismissRequest = {
                    dialog.result.cancel()
                    jsDialog = null
                },
                title = { Text("网页确认") },
                text = { Text(dialog.message) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog.result.confirm()
                        jsDialog = null
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dialog.result.cancel()
                        jsDialog = null
                    }) { Text("取消") }
                }
            )
        }

        is JsDialogState.Prompt -> {
            var inputValue by remember(dialog) { mutableStateOf(dialog.defaultValue) }
            AlertDialog(
                onDismissRequest = {
                    dialog.result.cancel()
                    jsDialog = null
                },
                title = { Text("网页输入") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dialog.message)
                        OutlinedTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog.result.confirm(inputValue)
                        jsDialog = null
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dialog.result.cancel()
                        jsDialog = null
                    }) { Text("取消") }
                }
            )
        }

        null -> Unit
    }
}
