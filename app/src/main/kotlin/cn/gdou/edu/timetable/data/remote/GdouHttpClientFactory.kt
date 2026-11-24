package cn.gdou.edu.timetable.data.remote

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
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
     * X25519MLKEM768 ClientHello) and only negotiates TLS 1.2 (AES-GCM / AES-128-CBC-SHA).
     * authserver.gdou.edu.cn also supports TLS 1.2 cleanly.
     */
    private val TLS_1_2_COMPAT_SPEC: ConnectionSpec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
        .tlsVersions(TlsVersion.TLS_1_2)
        .build()

    fun create(cookieJar: CookieJar): OkHttpClient {
        val pool = ConnectionPool(3, 15, TimeUnit.SECONDS)
        return OkHttpClient.Builder()
            .dns(Ipv4PreferredDns)
            .cookieJar(cookieJar)
            .connectionPool(pool)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionSpecs(listOf(TLS_1_2_COMPAT_SPEC, ConnectionSpec.CLEARTEXT))
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
     * Prefers IPv4 over IPv6 for gdou.edu.cn hosts: China 5G/5GA cellular networks prioritize AAAA
     * records (and sometimes omit A records completely), while jw.gdou.edu.cn's IPv6 endpoint
     * frequently refuses/closes TLS connections. If system DNS returns zero IPv4 addresses for
     * gdou.edu.cn, dynamically resolves A records via AliDNS DoH (223.5.5.5) and places IPv4 first.
     */
    internal object Ipv4PreferredDns : Dns {
        private data class CachedIpv4(val addresses: List<InetAddress>, val expiresAtMs: Long)

        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private val dohCache = ConcurrentHashMap<String, CachedIpv4>()
        private val json = Json { ignoreUnknownKeys = true }

        private val dohClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()
        }

        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (!hostname.endsWith("gdou.edu.cn", ignoreCase = true)) {
                return addresses
            }
            val reordered = reorderForHost(hostname, addresses)
            if (reordered.any { it is Inet4Address }) {
                return reordered
            }
            // Carrier 5G DNS returned only IPv6 AAAA records: resolve IPv4 via AliDNS DoH (literal IPv4)
            val dohIpv4 = resolveIpv4ViaDoh(hostname)
            return if (dohIpv4.isNotEmpty()) {
                runCatching {
                    Log.d("GdouAuth", "DoH IPv4 fallback for $hostname -> ${dohIpv4.joinToString { it.hostAddress.orEmpty() }}")
                }
                dohIpv4 + reordered
            } else {
                reordered
            }
        }

        internal fun reorderForHost(hostname: String, addresses: List<InetAddress>): List<InetAddress> {
            if (!hostname.endsWith("gdou.edu.cn", ignoreCase = true) || addresses.size <= 1) {
                return addresses
            }
            val ipv4 = addresses.filterIsInstance<Inet4Address>()
            val ipv6 = addresses.filterIsInstance<Inet6Address>()
            return if (ipv4.isNotEmpty()) ipv4 + ipv6 else addresses
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

            val resolved = runCatching {
                val req = Request.Builder()
                    .url("https://223.5.5.5/resolve?name=$key&type=A")
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
            }
            return resolved
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
     * evict pooled sockets and retry on a fresh TCP/TLS connection with `Connection: close`.
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
                            "HTTP attempt #${attempt + 1} failed (${e.javaClass.simpleName}: ${e.message}) url=${req.url.host}${req.url.encodedPath}"
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
