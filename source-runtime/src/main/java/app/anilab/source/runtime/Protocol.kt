package app.anilab.source.runtime

import android.content.Context
import android.net.Uri
import app.anilab.source.api.FilterList
import app.anilab.source.api.RemoteEpisode
import app.anilab.source.api.RemoteSeries
import kotlinx.serialization.Serializable

/** Wire contract between the app process and the `:extension` host process. */
object Protocol {
    const val AUTHORITY_SUFFIX = ".extensions"

    const val METHOD_API_VERSION = "apiVersion"
    const val METHOD_SOURCES = "sources"
    const val METHOD_CALL = "call"

    const val METHOD_POPULAR = "popular"
    const val METHOD_LATEST = "latest"
    const val METHOD_SEARCH = "search"
    const val METHOD_DETAILS = "details"
    const val METHOD_EPISODES = "episodes"
    const val METHOD_VIDEOS = "videos"
    const val METHOD_FILTERS = "filters"

    const val KEY_PKG = "pkg"
    const val KEY_SOURCE_ID = "sourceId"
    const val KEY_CALL_METHOD = "callMethod"
    const val KEY_PAYLOAD = "payload"
    const val KEY_RESULT = "result"
    const val KEY_ERROR_KIND = "errorKind"
    const val KEY_ERROR_MESSAGE = "errorMessage"
    const val KEY_API_VERSION = "apiVersion"

    const val ERROR_DENIED = "denied"
    const val ERROR_LOAD = "load"
    const val ERROR_API = "api"
    const val ERROR_RUNTIME = "runtime"
    const val ERROR_TIMEOUT = "timeout"

    /** Per-call budget. A slower call is abandoned and counts towards the breaker. */
    const val CALL_TIMEOUT_MS = 20_000L

    /** Consecutive failures before an extension is paused for [COOLDOWN_MS]. */
    const val FAILURE_THRESHOLD = 3
    const val COOLDOWN_MS = 10 * 60 * 1000L

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    fun baseUri(context: Context): Uri = Uri.parse("content://" + authority(context))
}

@Serializable
data class SourceDescriptor(
    val pkg: String,
    val sourceId: Long,
    val name: String,
    val lang: String,
    val versionId: Int,
)

/** Optional `assets/anilab-extension.json` shipped inside an extension APK. */
@Serializable
data class ExtensionMeta(
    val apiVersion: Int = 1,
    val name: String? = null,
    val factory: String? = null,
)

@Serializable
data class PageRequest(val page: Int)

@Serializable
data class SearchRequest(val query: String, val filters: FilterList, val page: Int)

@Serializable
data class DetailsRequest(val series: RemoteSeries)

@Serializable
data class EpisodesRequest(val series: RemoteSeries)

@Serializable
data class VideosRequest(val episode: RemoteEpisode)
