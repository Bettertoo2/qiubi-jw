package cn.gdou.edu.timetable.data.remote

import cn.gdou.edu.timetable.data.local.SecureCredentialStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean
)

/**
 * Thread-safe persistent CookieJar backed by AndroidKeyStore-encrypted storage,
 * supporting bidirectional cookie synchronization with Android WebView's CookieManager
 * (including MULTIFACTOR_BROWSER_FINGERPRINT so completed MFA sessions persist).
 */
class PersistentCookieJar(
    private val credentialStore: SecureCredentialStore
) : CookieJar {

    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private val cookieStore = LinkedHashMap<String, Cookie>()

    init {
        restoreFromDisk()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            for (cookie in cookies) {
                val key = cookieKey(cookie)
                if (cookie.expiresAt < now || cookie.value.isEmpty()) {
                    cookieStore.remove(key)
                } else {
                    cookieStore[key] = cookie
                }
            }
            persistToDiskLocked()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val expiredKeys = mutableListOf<String>()
            val matched = mutableListOf<Cookie>()
            for ((key, cookie) in cookieStore) {
                if (cookie.expiresAt < now) {
                    expiredKeys.add(key)
                } else if (cookie.matches(url)) {
                    matched.add(cookie)
                }
            }
            if (expiredKeys.isNotEmpty()) {
                expiredKeys.forEach { cookieStore.remove(it) }
                persistToDiskLocked()
            }
            return matched
        }
    }

    fun importWebViewCookieHeader(
        hostUrl: String,
        cookieHeader: String?,
        pathOverride: String = "/",
        skipExactPairs: Set<Pair<String, String>> = emptySet()
    ) {
        if (cookieHeader.isNullOrBlank()) return
        val url = hostUrl.toHttpUrlOrNull() ?: return
        val pairs = cookieHeader.split(";")
        val imported = mutableListOf<Cookie>()
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (name.isEmpty()) continue
            if ((name to value) in skipExactPairs) continue
            val ttlMs = if (name.equals("MULTIFACTOR_BROWSER_FINGERPRINT", ignoreCase = true)) {
                365L * 24 * 60 * 60 * 1000
            } else {
                7L * 24 * 60 * 60 * 1000
            }
            val cookie = Cookie.Builder()
                .name(name)
                .value(value)
                .domain(url.host)
                .path(pathOverride)
                .expiresAt(System.currentTimeMillis() + ttlMs)
                .build()
            imported.add(cookie)
        }
        saveFromResponse(url, imported)
    }

    /**
     * Exports active cookies for a host suffix (e.g. "gdou.edu.cn") as (url, Set-Cookie) pairs
     * so WebView can seamlessly continue an MFA flow started in native OkHttp login.
     */
    fun exportForWebView(hostSuffix: String = "gdou.edu.cn"): List<Pair<String, String>> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            return cookieStore.values
                .filter { it.expiresAt >= now && it.domain.endsWith(hostSuffix, ignoreCase = true) }
                .map { cookie ->
                    val targetUrl = "https://${cookie.domain}${cookie.path}"
                    val setCookie = buildString {
                        append(cookie.name).append("=").append(cookie.value)
                        append("; Path=").append(cookie.path)
                        if (!cookie.hostOnly) append("; Domain=").append(cookie.domain)
                        if (cookie.secure) append("; Secure")
                        if (cookie.httpOnly) append("; HttpOnly")
                    }
                    targetUrl to setCookie
                }
        }
    }

    fun snapshotAll(): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        cookieStore.values.filter { it.expiresAt >= now }
    }

    fun hasCookieForHost(host: String): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        cookieStore.values.any { it.expiresAt >= now && host.endsWith(it.domain, ignoreCase = true) }
    }

    /**
     * Clears session cookies while optionally preserving MULTIFACTOR_BROWSER_FINGERPRINT
     * so trusted devices don't unnecessarily re-trigger MFA.
     */
    fun clearSessionCookies(preserveMfaFingerprint: Boolean = true) {
        synchronized(lock) {
            if (preserveMfaFingerprint) {
                val kept = cookieStore.filterValues {
                    it.name.equals("MULTIFACTOR_BROWSER_FINGERPRINT", ignoreCase = true)
                }
                cookieStore.clear()
                cookieStore.putAll(kept)
                persistToDiskLocked()
            } else {
                cookieStore.clear()
                credentialStore.clearCookies()
            }
        }
    }

    fun clearAll() = clearSessionCookies(preserveMfaFingerprint = false)

    private fun cookieKey(cookie: Cookie): String =
        "${cookie.domain}|${cookie.path}|${cookie.name}"

    private fun persistToDiskLocked() {
        val now = System.currentTimeMillis()
        val list = cookieStore.values
            .filter { it.expiresAt >= now }
            .map {
                StoredCookie(
                    name = it.name,
                    value = it.value,
                    expiresAt = it.expiresAt,
                    domain = it.domain,
                    path = it.path,
                    secure = it.secure,
                    httpOnly = it.httpOnly,
                    hostOnly = it.hostOnly
                )
            }
        runCatching {
            credentialStore.saveCookiesJson(json.encodeToString(list))
        }
    }

    private fun restoreFromDisk() {
        val raw = credentialStore.loadCookiesJson()
        if (raw.isBlank()) return
        runCatching {
            val stored = json.decodeFromString<List<StoredCookie>>(raw)
            val now = System.currentTimeMillis()
            synchronized(lock) {
                for (item in stored) {
                    if (item.expiresAt < now) continue
                    val builder = Cookie.Builder()
                        .name(item.name)
                        .value(item.value)
                        .expiresAt(item.expiresAt)
                        .path(item.path)
                    if (item.hostOnly) builder.hostOnlyDomain(item.domain) else builder.domain(item.domain)
                    if (item.secure) builder.secure()
                    if (item.httpOnly) builder.httpOnly()
                    val cookie = builder.build()
                    cookieStore[cookieKey(cookie)] = cookie
                }
            }
        }
    }
}
