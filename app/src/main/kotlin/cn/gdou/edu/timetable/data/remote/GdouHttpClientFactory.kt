package cn.gdou.edu.timetable.data.remote

import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object GdouHttpClientFactory {
    // Desktop Chrome UA ensures authserver.gdou.edu.cn serves the standard PC graphic-captcha flow
    // (mobile UA forces slider captchaSwitch="2") and matches jw.gdou.edu.cn's recommended browser.
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    fun create(cookieJar: CookieJar): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(DefaultHeadersAndHttpsUpgradeInterceptor())
            .addInterceptor(TransientRetryInterceptor(maxRetries = 2))
            .build()
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
     * GDOU's ADC load balancer occasionally terminates idle TLS connections abruptly;
     * retry idempotent / safe requests cleanly up to [maxRetries] times.
     */
    private class TransientRetryInterceptor(private val maxRetries: Int) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var attempt = 0
            var lastError: IOException? = null
            while (attempt <= maxRetries) {
                try {
                    return chain.proceed(chain.request())
                } catch (e: IOException) {
                    lastError = e
                    attempt++
                    if (attempt > maxRetries) break
                    try {
                        Thread.sleep(350L * attempt)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            throw lastError ?: IOException("网络连接异常，请检查校园网或网络状态")
        }
    }
}
