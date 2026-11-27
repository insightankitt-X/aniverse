package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Host-side network service for Aniyomi extensions.
 *
 * The class name, constructor and members are part of the extension ABI: extensions get this
 * instance through `AnimeHttpSource.network` and read [client], [cloudflareClient] and
 * [defaultUserAgentProvider]. The implementation is AniVerse's own OkHttp stack.
 */
class NetworkHelper(context: Context) {

    private val cookieJar = MemoryCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .apply {
            val cacheDir = File(context.cacheDir, "extension_http_cache")
            runCatching { cache(Cache(cacheDir, 5L * 1024 * 1024)) }
        }
        .addInterceptor { chain ->
            val request = chain.request()
            val withAgent = if (request.header("User-Agent") == null) {
                request.newBuilder().header("User-Agent", DEFAULT_USER_AGENT).build()
            } else {
                request
            }
            chain.proceed(withAgent)
        }
        .build()

    /** Extensions no longer get a separate Cloudflare client; one client handles both. */
    val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider(): String = DEFAULT_USER_AGENT

    companion object {
        /**
         * A current mobile Chrome agent. Some sites serve a stripped "unsupported browser" page
         * when the UA looks like a plain OkHttp client.
         */
        const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

/** Session-scoped cookie storage shared by every request an extension makes. */
private class MemoryCookieJar : CookieJar {
    private val byHost = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val jar = byHost.getOrPut(url.host) { mutableListOf() }
        synchronized(jar) {
            cookies.forEach { cookie ->
                jar.removeAll { it.name == cookie.name && it.path == cookie.path }
                jar.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val jar = byHost[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(jar) {
            jar.removeAll { it.expiresAt < now }
            return jar.filter { url.encodedPath.startsWith(it.path) }
        }
    }
}
