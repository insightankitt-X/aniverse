package app.anilab.local.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only network calls this app makes: repository index files and extension APKs. Everything else
 * (browsing, search, playback) goes through extensions, and library/history/downloads are local.
 */
interface RepoApi {
    @Streaming
    @GET
    suspend fun fetchText(@Url url: String): ResponseBody

    @Streaming
    @GET
    suspend fun fetchApk(@Url url: String, @Header("Range") range: String?): Response<ResponseBody>
}

@Singleton
class RepoIndexFetcher @Inject constructor(
    private val api: RepoApi,
    private val parser: RepoIndexParser,
    private val json: Json,
) {
    suspend fun fetch(indexUrl: String): IndexFetchResult = withContext(Dispatchers.IO) {
        try {
            val body = api.fetchText(indexUrl).use { it.string() }
            val parsed = parser.parse(body, indexUrl)
            if (parsed == null) IndexFetchResult.NotAnIndex else IndexFetchResult.Success(parsed)
        } catch (e: retrofit2.HttpException) {
            IndexFetchResult.NotAnIndex
        } catch (e: IOException) {
            IndexFetchResult.Unreachable
        } catch (e: Exception) {
            IndexFetchResult.NotAnIndex
        }
    }
}

/** Cached index payload also stores when it was last successfully parsed. */
data class CachedIndex(val payload: String, val updatedAt: Long)

fun indexTimeoutOf(current: Long, ttlMs: Long = TimeUnit.MINUTES.toMillis(15L)): Boolean =
    System.currentTimeMillis() - current > ttlMs
