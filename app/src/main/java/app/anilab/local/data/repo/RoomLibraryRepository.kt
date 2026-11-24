package app.anilab.local.data.repo

import app.anilab.local.data.db.CategoryDao
import app.anilab.local.data.db.CategoryEntity
import app.anilab.local.data.db.EpisodeDao
import app.anilab.local.data.db.ExtensionDao
import app.anilab.local.data.db.ProgressDao
import app.anilab.local.data.db.SeriesCategoryCrossRef
import app.anilab.local.data.db.SeriesDao
import app.anilab.local.data.db.UpdateDao
import app.anilab.local.data.db.UpdateEntryEntity
import app.anilab.local.data.source.EpisodeStore
import app.anilab.local.data.source.SourceRegistry
import app.anilab.local.data.source.userMessage
import app.anilab.local.data.toDomain
import app.anilab.local.data.toEntity
import app.anilab.local.domain.model.Category
import app.anilab.local.domain.model.Episode
import app.anilab.local.domain.model.Series
import app.anilab.local.domain.model.UpdateGroup
import app.anilab.local.domain.model.UpdateItem
import app.anilab.local.domain.repository.LibraryRefreshReport
import app.anilab.local.domain.repository.LibraryRepository
import app.anilab.local.domain.model.UpdateFailure
import app.anilab.source.api.RemoteSeries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLibraryRepository @Inject constructor(
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val progressDao: ProgressDao,
    private val categoryDao: CategoryDao,
    private val updateDao: UpdateDao,
    private val extensionDao: ExtensionDao,
    private val registry: SourceRegistry,
    private val episodeStore: EpisodeStore,
) : LibraryRepository {

    override fun observeLibrary(): Flow<List<Series>> =
        seriesDao.observeLibrary().map { rows -> rows.map { it.toDomain() } }

    override fun observeRecentlyAdded(): Flow<List<Series>> =
        seriesDao.observeRecentlyAdded(20).map { rows -> rows.map { it.toDomain() } }

    override fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll().map { rows ->
        rows.map { Category(id = it.id, name = it.name, order = it.sortOrder) }
    }

    override fun observeUpdates(): Flow<List<UpdateGroup>> = updateDao.observeAll().map { entries ->
        if (entries.isEmpty()) return@map emptyList()
        val episodeRows = entries.mapNotNull { entry -> episodeDao.byId(entry.episodeId) }
        val seriesRows = seriesDao.byIds(episodeRows.map { it.seriesId }.distinct()).associateBy { it.id }
        val episodesById = episodeRows.associateBy { it.id }
        val sourceNames = extensionDao.installed().associate { it.pkg to it.name }

        entries.mapNotNull { entry ->
            val episode = episodesById[entry.episodeId] ?: return@mapNotNull null
            val series = seriesRows[episode.seriesId] ?: return@mapNotNull null
            UpdateItem(
                episodeId = episode.id,
                seriesId = series.id,
                seriesTitle = series.title,
                thumbnailUrl = series.thumbnailUrl,
                episodeName = episode.name,
                sourceName = sourceNames[series.sourcePkg] ?: series.sourcePkg,
                discoveredAt = entry.discoveredAt,
            )
        }
            .groupBy { it.seriesId }
            .map { (seriesId, items) -> UpdateGroup(seriesId, items.first().seriesTitle, items) }
            .sortedByDescending { group -> group.items.maxOf { it.discoveredAt } }
    }

    override fun observeUpdateCount(): Flow<Int> = updateDao.observeUnseenCount()

    override fun observeSeries(seriesId: Long): Flow<Series?> =
        seriesDao.observeById(seriesId).map { it?.toDomain() }

    override fun observeEpisodes(seriesId: Long): Flow<List<Episode>> =
        episodeDao.observeForSeries(seriesId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun isInLibrary(series: Series): Boolean =
        seriesDao.find(series.sourcePkg, series.sourceId, series.remoteId)?.inLibrary == true

    override suspend fun toggleLibrary(series: Series, categoryIds: List<Long>): Boolean {
        val existing = seriesDao.find(series.sourcePkg, series.sourceId, series.remoteId)
        if (existing != null && existing.inLibrary) {
            seriesDao.removeFromLibrary(existing.id)
            categoryDao.clearForSeries(existing.id)
            return false
        }
        val id = if (existing != null) {
            seriesDao.insert(series.toEntity(existing))
            existing.id
        } else {
            seriesDao.insert(series.toEntity())
        }
        seriesDao.addToLibrary(id, System.currentTimeMillis())
        if (categoryIds.isNotEmpty()) {
            categoryDao.clearForSeries(id)
            categoryDao.link(categoryIds.map { SeriesCategoryCrossRef(id, it) })
        }
        return true
    }

    override suspend fun setCategories(seriesId: Long, categoryIds: List<Long>) {
        categoryDao.clearForSeries(seriesId)
        if (categoryIds.isNotEmpty()) {
            categoryDao.link(categoryIds.map { SeriesCategoryCrossRef(seriesId, it) })
        }
    }

    override suspend fun createCategory(name: String): Long {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Category name cannot be empty" }
        val order = (categoryDao.all().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val id = categoryDao.insert(CategoryEntity(name = clean, sortOrder = order))
        if (id <= 0L) throw IllegalArgumentException("A category with that name already exists")
        return id
    }

    override suspend fun renameCategory(categoryId: Long, name: String) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Category name cannot be empty" }
        categoryDao.rename(categoryId, clean)
    }

    override suspend fun deleteCategory(categoryId: Long) = categoryDao.delete(categoryId)

    override suspend fun reorderCategories(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> categoryDao.setOrder(id, index) }
    }

    override suspend fun markWatched(episodeId: Long, watched: Boolean) {
        val episode = episodeDao.byId(episodeId) ?: return
        episodeDao.setWatched(episodeId, watched, if (watched) System.currentTimeMillis() else 0L)
        if (!watched) progressDao.delete(episodeId)
        // Keep the series timestamp fresh without touching the library flags.
        seriesDao.touch(episode.seriesId, System.currentTimeMillis())
    }

    override suspend fun bulkMarkWatched(episodeIds: List<Long>, watched: Boolean) {
        if (episodeIds.isEmpty()) return
        episodeDao.setWatchedMany(episodeIds, watched, if (watched) System.currentTimeMillis() else 0L)
        if (!watched) episodeIds.forEach { progressDao.delete(it) }
    }

    override suspend fun removeFromLibrary(seriesIds: List<Long>) {
        if (seriesIds.isEmpty()) return
        seriesDao.removeManyFromLibrary(seriesIds)
    }

    override suspend fun refreshLibrary(onProgress: (Int, Int) -> Unit): LibraryRefreshReport {
        val library = seriesDao.librarySeries()
        val failures = mutableListOf<UpdateFailure>()
        var newEpisodes = 0
        library.forEachIndexed { index, entity ->
            onProgress(index + 1, library.size)
            val handle = registry.handle(entity.sourcePkg, entity.sourceId)
            if (handle == null) {
                failures += UpdateFailure(entity.id, entity.title, entity.sourcePkg, "This source is unavailable")
                return@forEachIndexed
            }
            try {
                val remote = RemoteSeries(remoteId = entity.remoteId, title = entity.title, url = entity.url)
                val discovered = episodeStore.storeAndFindNew(entity.id, handle.source.getEpisodes(remote))
                val now = System.currentTimeMillis()
                discovered.forEach { episode ->
                    updateDao.insert(UpdateEntryEntity(seriesId = entity.id, episodeId = episode.id, discoveredAt = now))
                }
                if (discovered.isNotEmpty()) newEpisodes += discovered.size
                seriesDao.touch(entity.id, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failures += UpdateFailure(entity.id, entity.title, handle.ref.name, e.userMessage())
            }
        }
        return LibraryRefreshReport(checked = library.size, newEpisodes = newEpisodes, failures = failures)
    }

    override suspend fun lastCheckAt(): Long = updateDao.all().maxOfOrNull { it.discoveredAt } ?: 0L

    override suspend fun clearUpdates() = updateDao.clear()

    override suspend fun cacheEpisodes(series: Series): Result<List<Episode>> {
        val handle = registry.handle(series.sourcePkg, series.sourceId)
            ?: return Result.failure(IllegalStateException("This source is unavailable"))
        return try {
            val entity = seriesDao.find(series.sourcePkg, series.sourceId, series.remoteId)
                ?: seriesDao.byId(seriesDao.insert(series.toEntity())).let { seriesDao.find(series.sourcePkg, series.sourceId, series.remoteId) }
            val seriesId = entity?.id ?: return Result.failure(IllegalStateException("This series could not be saved locally"))
            val stored = episodeStore.store(seriesId, handle.source.getEpisodes(RemoteSeries(series.remoteId, series.title, series.url)))
            val progress = progressDao.forSeries(seriesId).associateBy { it.episodeId }
            Result.success(stored.map { it.toDomain(progress = progress[it.id]) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
