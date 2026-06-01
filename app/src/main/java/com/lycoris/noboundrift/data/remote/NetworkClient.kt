package com.lycoris.noboundrift.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Factory for the shared [OkHttpClient]. All scraper network calls go through this
 * single client so that:
 * - Cookies persist across requests (session-based sites).
 * - A realistic User-Agent is sent so sites don't block the app.
 * - Timeouts are tuned for manga image loading (larger payloads than typical API calls).
 */
object NetworkClient {

    /**
     * A realistic User-Agent based on Android Chrome — many manga sites block
     * requests without a browser UA. Update periodically to stay current.
     */
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    // ConcurrentHashMap prevents data races when multiple IO coroutines save/load cookies
    // concurrently (e.g. parallel chapter fetches across different sources).
    private val cookieStore = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostMap = cookieStore.getOrPut(url.host) { ConcurrentHashMap() }
            val now = System.currentTimeMillis()
            for (cookie in cookies) {
                // Drop expired persistent cookies; non-persistent (session) cookies have
                // expiresAt = Long.MAX_VALUE so they always pass this check.
                if (cookie.persistent && cookie.expiresAt < now) continue
                // Key by name+domain to deduplicate: a newer Set-Cookie for the same
                // name on the same domain replaces the old value rather than appending.
                hostMap["${cookie.name}@${cookie.domain}"] = cookie
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host]?.values?.toList() ?: emptyList()
    }

    fun build(enableLogging: Boolean = false): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .apply {
                if (enableLogging) {
                    addNetworkInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.HEADERS
                        }
                    )
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
