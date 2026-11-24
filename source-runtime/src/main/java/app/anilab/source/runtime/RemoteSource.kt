package app.anilab.source.runtime

import app.anilab.source.api.FilterList
import app.anilab.source.api.RemoteEpisode
import app.anilab.source.api.RemoteSeries
import app.anilab.source.api.SeriesPage
import app.anilab.source.api.Source
import app.anilab.source.api.Video
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A [Source] backed by the `:extension` process. Every call passes the circuit breaker and every
 * returned URL passes [UrlGuard].
 */
class RemoteSource(
    override val sourceId: Long,
    override val name: String,
    override val lang: String,
    override val versionId: Int,
    private val pkg: String,
    private val client: ExtensionClient,
    private val json: Json,
    private val breaker: CircuitBreaker,
    private val onCallFailed: (Throwable) -> Unit,
) : Source {

    override suspend fun getPopular(page: Int): SeriesPage = guarded {
        decode(
            SeriesPage.serializer(),
            client.call(pkg, sourceId, Protocol.METHOD_POPULAR, json.encodeToString(PageRequest.serializer(), PageRequest(page))),
        )
    }

    override suspend fun getLatest(page: Int): SeriesPage = guarded {
        decode(
            SeriesPage.serializer(),
            client.call(pkg, sourceId, Protocol.METHOD_LATEST, json.encodeToString(PageRequest.serializer(), PageRequest(page))),
        )
    }

    override suspend fun search(query: String, filters: FilterList, page: Int): SeriesPage = guarded {
        decode(
            SeriesPage.serializer(),
            client.call(
                pkg,
                sourceId,
                Protocol.METHOD_SEARCH,
                json.encodeToString(SearchRequest.serializer(), SearchRequest(query, filters, page)),
            ),
        )
    }

    override suspend fun getDetails(series: RemoteSeries): RemoteSeries = guarded {
        decode(
            RemoteSeries.serializer(),
            client.call(pkg, sourceId, Protocol.METHOD_DETAILS, json.encodeToString(DetailsRequest.serializer(), DetailsRequest(series))),
        )
    }

    override suspend fun getEpisodes(series: RemoteSeries): List<RemoteEpisode> = guarded {
        decode(
            ListSerializer(RemoteEpisode.serializer()),
            client.call(pkg, sourceId, Protocol.METHOD_EPISODES, json.encodeToString(EpisodesRequest.serializer(), EpisodesRequest(series))),
        )
    }

    override suspend fun getVideoList(episode: RemoteEpisode): List<Video> = guarded {
        val raw = decode(
            ListSerializer(Video.serializer()),
            client.call(pkg, sourceId, Protocol.METHOD_VIDEOS, json.encodeToString(VideosRequest.serializer(), VideosRequest(episode))),
        )
        val safe = raw.mapNotNull { video ->
            if (!UrlGuard.isAllowed(video.url)) return@mapNotNull null
            video.copy(
                subtitles = video.subtitles.filter { UrlGuard.isAllowed(it.url) },
                headers = video.headers.filterKeys { it.isNotBlank() },
            )
        }
        if (safe.isEmpty()) {
            throw SourceCallException(Protocol.ERROR_RUNTIME, "No playable links were returned for this episode")
        }
        safe
    }

    override suspend fun getFilterList(): FilterList = guarded {
        decode(FilterList.serializer(), client.call(pkg, sourceId, Protocol.METHOD_FILTERS, "{}"))
    }

    private suspend fun <T> guarded(block: suspend () -> T): T {
        breaker.ensureClosed()
        return try {
            val value = block()
            breaker.recordSuccess()
            value
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            breaker.recordFailure()
            onCallFailed(e)
            throw e
        }
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, text: String): T = try {
        json.decodeFromString(deserializer, text)
    } catch (e: Exception) {
        throw SourceCallException(Protocol.ERROR_RUNTIME, "The extension returned data this app could not read", e)
    }
}
