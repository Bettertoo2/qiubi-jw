package cn.gdou.edu.timetable.data.remote

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object GdouHttpClientFactory {
    // Desktop Chrome UA ensures authserver.gdou.edu.cn serves the standard PC graphic-captcha flow
    // (mobile UA forces slider captchaSwitch="2") and matches jw.gdou.edu.cn's recommended browser.
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    /**
     * jw.gdou.edu.cn sits behind a legacy Sangfor AD load balancer + Java 8 Servlet container
     * that rejects TLS 1.3 (and chokes on modern Android Conscrypt's oversized post-quantum
     * X25519MLKEM768 ClientHello) and only negotiates TLS 1.2.
     * authserver.gdou.edu.cn also supports TLS 1.2 cleanly.
     */
    private val TLS_1_2_COMPAT_SPEC: ConnectionSpec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
        .tlsVersions(TlsVersion.TLS_1_2)
        .build()

    @Volatile
    private var lastAttemptedTarget: String = ""

    fun lastTargetHint(): String = lastAttemptedTarget

    fun create(cookieJar: CookieJar): OkHttpClient {
        val pool = ConnectionPool(3, 15, TimeUnit.SECONDS)
        return OkHttpClient.Builder()
            .dns(Ipv4PreferredDns)
            .cookieJar(cookieJar)
            .connectionPool(pool)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionSpecs(listOf(TLS_1_2_COMPAT_SPEC, ConnectionSpec.CLEARTEXT))
            .eventListenerFactory {
                object : EventListener() {
                    override fun connectStart(
                        call: Call,
                        inetSocketAddress: InetSocketAddress,
                        proxy: Proxy
                    ) {
                        val host = call.request().url.host
                        val ip = inetSocketAddress.address?.hostAddress ?: inetSocketAddress.hostString
                        lastAttemptedTarget = "$host -> $ip:${inetSocketAddress.port}"
                    }
                }
            }
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(DefaultHeadersAndHttpsUpgradeInterceptor())
            .addInterceptor(TransientRetryInterceptor(pool = pool, maxRetries = 3))
            .build()
    }

    /**
     * Strictly uses IPv4 for gdou.edu.cn hosts: both `jw.gdou.edu.cn` and `authserver.gdou.edu.cn`
     * publish broken IPv6 AAAA records (2001:250:300a:100::...) that fail TLS handshakes with
     * `SSLHandshakeException: connection closed` on China 5G/5GA cellular networks.
     *
     * Resolution order for `*.gdou.edu.cn`:
     * 1. System DNS IPv4 (`Inet4Address`) — returns IPv4 ONLY so OkHttp never fails over to dead IPv6.
     * 2. AliDNS DoH (`dns.alidns.com` / `223.5.5.5`) type=A when 5G DNS omits A records.
     * 3. Known-good static IPv4 fallback for core GDOU hosts if both system DNS and DoH returned no IPv4.
     * 4. Original system DNS addresses only if none of the above produced an IPv4 address.
     */
    internal object Ipv4PreferredDns : Dns {
        private data class CachedIpv4(val addresses: List<InetAddress>, val expiresAtMs: Long)

        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private val dohCache = ConcurrentHashMap<String, CachedIpv4>()
        private val json = Json { ignoreUnknownKeys = true }

        private val KNOWN_GDOU_IPV4: Map<String, ByteArray> = mapOf(
            "jw.gdou.edu.cn" to byteArrayOf(210.toByte(), 38, 137.toByte(), 108),
            "authserver.gdou.edu.cn" to byteArrayOf(210.toByte(), 38, 136.toByte(), 93)
        )

        private val dohClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()
        }

        override fun lookup(hostname: String): List<InetAddress> {
            val systemAddrs = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
            if (!hostname.endsWith("gdou.edu.cn", ignoreCase = true)) {
                if (systemAddrs.isNotEmpty()) return systemAddrs
                throw java.net.UnknownHostException("Unable to resolve $hostname")
            }

            // 1. Prefer system IPv4 ONLY (never append broken GDOU IPv6!)
            val ipv4Only = filterIpv4OnlyForGdou(hostname, systemAddrs)
            if (ipv4Only.isNotEmpty()) {
                return ipv4Only
            }

            // 2. Carrier 5G DNS returned only IPv6 AAAA records: resolve IPv4 via AliDNS DoH
            val dohIpv4 = resolveIpv4ViaDoh(hostname)
            if (dohIpv4.isNotEmpty()) {
                runCatching {
                    Log.d("GdouAuth", "DoH IPv4 resolved for $hostname -> ${dohIpv4.joinToString { it.hostAddress.orEmpty() }}")
                }
                return dohIpv4
            }

            // 3. Verified static IPv4 fallback for core hosts when both system DNS & DoH yielded no IPv4
            val staticFallback = staticFallbackIpv4(hostname)
            if (staticFallback.isNotEmpty()) {
                runCatching {
                    Log.d("GdouAuth", "Static IPv4 fallback for $hostname -> ${staticFallback.first().hostAddress}")
                }
                return staticFallback
            }

            if (systemAddrs.isNotEmpty()) return systemAddrs
            throw java.net.UnknownHostException("Unable to resolve $hostname")
        }

        internal fun filterIpv4OnlyForGdou(hostname: String, addresses: List<InetAddress>): List<InetAddress> {
            if (!hostname.endsWith("gdou.edu.cn", ignoreCase = true)) {
                return addresses
            }
            return addresses.filterIsInstance<Inet4Address>()
        }

        internal fun staticFallbackIpv4(hostname: String): List<InetAddress> {
            val bytes = KNOWN_GDOU_IPV4[hostname.lowercase()] ?: return emptyList()
            return listOf(InetAddress.getByAddress(hostname, bytes))
        }

        internal fun parseDohIpv4Json(hostname: String, body: String): List<InetAddress> {
            return runCatching {
                val root = json.parseToJsonElement(body).jsonObject
                val answers = root["Answer"]?.jsonArray.orEmpty()
                answers.mapNotNull { el ->
                    val obj = el.jsonObject
                    val type = obj["type"]?.jsonPrimitive?.intOrNull
                    val data = obj["data"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (type == 1 && data.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
                        val octets = data.split('.').map { it.toInt().toByte() }.toByteArray()
                        InetAddress.getByAddress(hostname, octets)
                    } else null
                }
            }.getOrDefault(emptyList())
        }

        private fun resolveIpv4ViaDoh(hostname: String): List<InetAddress> {
            val key = hostname.lowercase()
            val now = System.currentTimeMillis()
            dohCache[key]?.takeIf { it.expiresAtMs > now }?.let { return it.addresses }

            val endpoints = listOf(
                "https://dns.alidns.com/resolve?name=$key&type=A",
                "https://223.5.5.5/resolve?name=$key&type=A"
            )
            for (url in endpoints) {
                val resolved = runCatching {
                    val req = Request.Builder()
                        .url(url)
                        .header("Accept", "application/dns-json")
                        .get()
                        .build()
                    dohClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) emptyList()
                        else parseDohIpv4Json(hostname, resp.body?.string().orEmpty())
                    }
                }.getOrDefault(emptyList())

                if (resolved.isNotEmpty()) {
                    dohCache[key] = CachedIpv4(resolved, now + CACHE_TTL_MS)
                    return resolved
                }
            }
            return emptyList()
        }
    }

    /**
     * Ensures browser-compatible headers and upgrades intermediate HTTP redirects on gdou.edu.cn
     * (observed live from jw.gdou.edu.cn/sso/jziotlogin) to HTTPS cleanly.
     */
    private class DefaultHeadersAndHttpsUpgradeInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val url = if (original.url.scheme == "http" &&
                original.url.host.endsWith("gdou.edu.cn", ignoreCase = true)
            ) {
                original.url.newBuilder().scheme("https").build()
            } else {
                original.url
            }

            val builder = original.newBuilder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            if (original.header("Accept") == null) {
                builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8")
            }
            return chain.proceed(builder.build())
        }
    }

    /**
     * GDOU's Sangfor ADC load balancer occasionally closes TLS connections abruptly (~15% observed);
     * evict pooled sockets and retry safe requests on a fresh TCP/TLS connection with `Connection: close`.
     */
    private class TransientRetryInterceptor(
        private val pool: ConnectionPool,
        private val maxRetries: Int
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var attempt = 0
            var lastError: IOException? = null
            while (attempt <= maxRetries) {
                val req = if (attempt == 0) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .header("Connection", "close")
                        .build()
                }
                try {
                    return chain.proceed(req)
                } catch (e: IOException) {
                    lastError = e
                    runCatching {
                        Log.w(
                            "GdouAuth",
                            "HTTP attempt #${attempt + 1} failed (${e.javaClass.simpleName}: ${e.message}) target=${lastAttemptedTarget} url=${req.url.host}${req.url.encodedPath}"
                        )
                    }
                    pool.evictAll()
                    attempt++
                    if (attempt > maxRetries) break
                    try {
                        Thread.sleep(250L * attempt)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            throw lastError ?: IOException("网络连接异常，请检查校园网或网络状态")
        }
    }
}
