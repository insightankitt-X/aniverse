package app.anilab.source.api

/**
 * Version of the extension API. An extension APK declares the version it was built against in
 * `assets/anilab-extension.json`; a mismatch is rejected with a readable message instead of
 * failing later with an obscure class-cast error.
 */
const val EXTENSION_API_VERSION: Int = 1

/** Intent action every extension APK declares, used for package visibility (no QUERY_ALL_PACKAGES). */
const val EXTENSION_ACTION: String = "app.anilab.source.api.EXTENSION"

/** Entry path inside the extension APK listing `SourceFactory` implementations, one per line. */
const val FACTORY_SERVICE_PATH: String = "META-INF/services/app.anilab.source.api.SourceFactory"

/** Optional descriptor inside the extension APK. */
const val EXTENSION_META_PATH: String = "assets/anilab-extension.json"

/** Optional plain-text fallback for the factory class name. */
const val FACTORY_FALLBACK_PATH: String = "assets/anilab-factory.txt"

/**
 * A single extension-provided source. Implementations are supplied by third-party APKs and are
 * never shipped with the app; every call is made off the UI thread and can be cancelled.
 */
interface Source {
    val sourceId: Long
    val name: String
    val lang: String
    val versionId: Int

    suspend fun getPopular(page: Int): SeriesPage

    suspend fun getLatest(page: Int): SeriesPage

    suspend fun search(query: String, filters: FilterList, page: Int): SeriesPage

    suspend fun getDetails(series: RemoteSeries): RemoteSeries

    suspend fun getEpisodes(series: RemoteSeries): List<RemoteEpisode>

    suspend fun getVideoList(episode: RemoteEpisode): List<Video>

    suspend fun getFilterList(): FilterList
}

/** The entry point an extension APK exposes. */
interface SourceFactory {
    fun create(): Source

    fun apiVersion(): Int = EXTENSION_API_VERSION
}

/** Thrown by the runtime when an extension was built against a different API version. */
class ExtensionApiMismatchException(
    val requiredVersion: Int,
    val actualVersion: Int,
) : IllegalStateException(
    "This extension targets API v$actualVersion, but this version of AniLab Local supports API v$requiredVersion",
)
