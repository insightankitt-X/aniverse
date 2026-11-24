package app.aniverse.local.data.repo

import app.aniverse.local.data.db.EpisodeDao
import app.aniverse.local.data.db.ExtensionDao
import app.aniverse.local.data.db.ProgressDao
import app.aniverse.local.data.db.RepoDao
import app.aniverse.local.data.db.SeriesDao
import app.aniverse.local.data.db.SourceCacheDao
import app.aniverse.local.data.db.SourceCacheEntity
import app.aniverse.local.data.source.EpisodeStore
import app.aniverse.local.data.source.SourceRegistry
import app.aniverse.local.data.source.SourceUnavailableException
import app.aniverse.local.data.source.userMessage
import app.aniverse.local.data.toDomain
import app.aniverse.local.data.toEntity
import app.aniverse.local.domain.model.Episode
import app.aniverse.local.domain.model.Series
import app.aniverse.local.domain.model.SourceRef
import app.aniverse.local.domain.repository.BrowseRepository
import app.aniverse.local.domain.repository.PlayableSubtitle
import app.aniverse.local.domain.repository.PlayableVideo
import app.aniverse.local.domain.repository.SourcePage
import app.aniverse.source.api.FilterList
import app.aniverse.source.api.RemoteEpisode
import app.aniverse.source.api.RemoteSeries
import app.aniverse.source.api.SeriesPage
import app.aniverse.source.runtime.CircuitOpenException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBrowseRepository @Inject constructor(
    private val registry: SourceRegistry,
    private val sourceCacheDao: SourceCacheDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val progressDao: ProgressDao,
    private val extensionDao: ExtensionDao,
    private val repoDao: RepoDao,
    private val episodeStore: EpisodeStore,
    private val json: Json,
) : BrowseRepository {

    override fun observeSources(): Flow<List<SourceRef>> = combine(
        extensionDao.observeEnabled(),
        repoDao.observeAll(),
    ) { _, _ -> registry.enabled().map { it.ref } }

    override suspend fun popular(sourcePkg: String, sourceId: Long, page: Int): Result<SourcePage> =
        pageCall(CACHE_POPULAR, sourcePkg, sourceId, page, query = null) { it.getPopular(page) }

    override suspend fun latest(sourcePkg: String, sourceId: Long, page: Int): Result<SourcePage> =
        pageCall(CACHE_LATEST, sourcePkg, sourceId, page, query = null) { it.getLatest(page) }

    override suspend fun search(
        sourcePkg: String,
        sourceId: Long,
        query: String,
        filters: FilterList,
        page: Int,
    ): Result<SourcePage> = pageCall(CACHE_SEARCH, sourcePkg, sourceId, page, query) { it.search(query, filters, page) }

    override suspend fun filters(sourcePkg: String, sourceId: Long): Result<FilterList> {
        val handle = registry.handle(sourcePkg, sourceId)
            ?: return Result.failure(SourceUnavailableException("This source is unavailable. Re-enable its extension."))
        return try {
            Result.success(handle.source.getFilterList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            noteFailure(sourcePkg, e)
            val cached = sourceCacheDao.byKey(cacheKey(sourcePkg, sourceId, CACHE_FILTERS, 0, null))
            val decoded = cached?.let { runCatching { json.decodeFromString(FilterList.serializer(), it.payload) }.getOrNull() }
            if (decoded != null) Result.success(decoded) else Result.failure(e)
        }
    }

    override suspend fun details(series: Series): Result<Series> {
        val handle = registry.handle(series.sourcePkg, series.sourceId)
            ?: return Result.failure(SourceUnavailableException("This source is unavailable. Re-enable its extension."))
        return try {
            val remote = RemoteSeries(
                remoteId = series.remoteId,
                title = series.title,
                url = series.url,
                thumbnailUrl = series.thumbnailUrl,
                description = series.description,
                genres = series.genres,
                initialized = true,
            )
            val detailed = handle.source.getDetails(remote).toDomain(series.sourcePkg, series.sourceId)
            val existing = seriesDao.find(series.sourcePkg, series.sourceId, series.remoteId)
            val rowId = seriesDao.insert(
                detailed.toEntity(existing).copy(
                    lastUpdatedAt = System.currentTimeMillis(),
                    lastFetchedAt = System.currentTimeMillis(),
                ),
            )
            extensionDao.clearFailures(series.sourcePkg)
            Result.success(detailed.copy(id = if (rowId > 0) rowId else existing?.id ?: 0L))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            noteFailure(series.sourcePkg, e)
            Result.failure(e)
        }
    }

    override suspend fun episodes(series: Series): Result<List<Episode>> {
        val handle = registry.handle(series.sourcePkg, series.sourceId)
            ?: return Result.failure(SourceUnavailableException("This source is unavailable. Re-enable its extension."))
        return try {
            val remote = RemoteSeries(remoteId = series.remoteId, title = series.title, url = series.url)
            val seriesId = ensureSeriesRow(series)
            val stored = episodeStore.store(seriesId, handle.source.getEpisodes(remote))
            val progress = progressDao.forSeries(seriesId).associateBy { it.episodeId }
            Result.success(stored.map { it.toDomain(progress = progress[it.id]) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            noteFailure(series.sourcePkg, e)
            val seriesId = seriesDao.find(series.sourcePkg, series.sourceId, series.remoteId)?.id ?: return Result.failure(e)
            val stored = episodeDao.forSeries(seriesId)
            if (stored.isEmpty()) Result.failure(e) else {
                val progress = progressDao.forSeries(seriesId).associateBy { it.episodeId }
                Result.success(stored.map { it.toDomain(progress = progress[it.id]) })
            }
        }
    }

    override suspend fun videos(series: Series, episode: Episode): Result<List<PlayableVideo>> {
        val handle = registry.handle(series.sourcePkg, series.sourceId)
            ?: return Result.failure(SourceUnavailableException("This source is unavailable. Re-enable its extension."))
        return try {
            val remoteEpisode = RemoteEpisode(
                remoteId = episode.remoteId,
                name = episode.name,
                url = episode.url,
                episodeNumber = episode.number,
                filler = episode.filler,
                uploadDate = episode.uploadDate,
            )
            val videos = handle.source.getVideoList(remoteEpisode).map { video ->
                PlayableVideo(
                    url = video.url,
                    quality = video.quality,
                    kind = video.type.name,
                    headers = video.headers,
                    subtitles = video.subtitles.map { subtitle ->
                        PlayableSubtitle(
                            url = subtitle.url,
                            lang = subtitle.lang,
                            label = subtitle.label ?: subtitle.lang,
                            embedded = subtitle.embedded,
                        )
                    },
                )
            }
            if (videos.isEmpty()) {
                Result.failure(SourceUnavailableException("No playable links were returned for this episode"))
            } else {
                extensionDao.clearFailures(series.sourcePkg)
                Result.success(videos)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            noteFailure(series.sourcePkg, e)
            Result.failure(e)
        }
    }

    /** v1 of the extension API has no related-series call, so nothing is offered. */
    override suspend fun related(series: Series): List<Series> = emptyList()

    private suspend fun pageCall(
        kind: String,
        sourcePkg: String,
        sourceId: Long,
        page: Int,
        query: String?,
        load: suspend (app.aniverse.source.api.Source) -> SeriesPage,
    ): Result<SourcePage> {
        val handle = registry.handle(sourcePkg, sourceId)
            ?: return Result.failure(SourceUnavailableException("This source is unavailable. Re-enable its extension."))
        return try {
            val loaded = load(handle.source)
            sourceCacheDao.upsert(
                SourceCacheEntity(
                    cacheKey = cacheKey(sourcePkg, sourceId, kind, page, query),
                    sourcePkg = sourcePkg,
                    sourceId = sourceId,
                    kind = kind,
                    payload = json.encodeToString(SeriesPage.serializer(), loaded),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            extensionDao.clearFailures(sourcePkg)
            Result.success(
                SourcePage(
                    items = loaded.series.map { it.toDomain(sourcePkg, sourceId) },
                    hasNextPage = loaded.hasNextPage,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            noteFailure(sourcePkg, e)
            val fallback = sourceCacheDao.byKey(cacheKey(sourcePkg, sourceId, kind, page, query))
            val decoded = fallback?.let { runCatching { json.decodeFromString(SeriesPage.serializer(), it.payload) }.getOrNull() }
            if (decoded != null) {
                Result.success(
                    SourcePage(
                        items = decoded.series.map { it.toDomain(sourcePkg, sourceId) },
                        hasNextPage = decoded.hasNextPage,
                    ),
                )
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun ensureSeriesRow(series: Series): Long {
        seriesDao.find(series.sourcePkg, series.sourceId, series.remoteId)?.let { return it.id }
        val inserted = seriesDao.insert(series.toEntity())
        return if (inserted > 0) inserted else {
            seriesDao.find(series.sourcePkg, series.sourceId, series.remoteId)?.id
                ?: throw SourceUnavailableException("This series could not be saved locally")
        }
    }

    private suspend fun noteFailure(pkg: String, error: Throwable) {
        if (error is CircuitOpenException) return
        val extension = extensionDao.byPkg(pkg) ?: return
        if (extension.failureCount + 1 >= MAX_FAILURES) {
            extensionDao.disable(pkg, "Disabled after repeated failures: ${error.userMessage()}")
        } else {
            extensionDao.recordFailure(pkg, error.userMessage())
        }
    }

    private fun cacheKey(pkg: String, sourceId: Long, kind: String, page: Int, query: String?): String =
        "$pkg|$sourceId|$kind|$page|${query.orEmpty()}"

    private companion object {
        const val CACHE_POPULAR = "popular"
        const val CACHE_LATEST = "latest"
        const val CACHE_SEARCH = "search"
        const val CACHE_FILTERS = "filters"
        const val MAX_FAILURES = 5
    }
}
