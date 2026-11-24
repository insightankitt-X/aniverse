package app.anilab.local.data.repo

import app.anilab.local.data.db.DownloadDao
import app.anilab.local.data.db.DownloadEntity
import app.anilab.local.data.db.EpisodeDao
import app.anilab.local.data.db.SeriesDao
import app.anilab.local.data.media.DownloadStorage
import app.anilab.local.domain.model.DownloadGroup
import app.anilab.local.domain.model.DownloadItem
import app.anilab.local.domain.model.DownloadState
import app.anilab.local.domain.model.Episode
import app.anilab.local.domain.model.Series
import app.anilab.local.domain.model.StorageSummary
import app.anilab.local.domain.repository.DownloadRepository
import app.anilab.local.domain.repository.SettingsRepository
import app.anilab.local.work.WorkScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val storage: DownloadStorage,
    private val scheduler: WorkScheduler,
    private val settings: SettingsRepository,
) : DownloadRepository {

    override fun observeDownloads(): Flow<List<DownloadGroup>> = downloadDao.observeAll().map { rows ->
        if (rows.isEmpty()) return@map emptyList()
        val seriesRows = seriesDao.byIds(rows.map { it.seriesId }.distinct()).associateBy { it.id }
        rows.groupBy { it.seriesId }
            .mapNotNull { (seriesId, items) ->
                val seriesRow = seriesRows[seriesId] ?: return@mapNotNull null
                DownloadGroup(
                    seriesId = seriesId,
                    seriesTitle = seriesRow.title,
                    thumbnailUrl = seriesRow.thumbnailUrl,
                    items = items.map { item ->
                        DownloadItem(
                            episodeId = item.episodeId,
                            seriesId = seriesId,
                            episodeName = episodeDao.byId(item.episodeId)?.name ?: "Episode",
                            seriesTitle = seriesRow.title,
                            thumbnailUrl = seriesRow.thumbnailUrl,
                            state = runCatching { DownloadState.valueOf(item.state) }.getOrDefault(DownloadState.QUEUED),
                            progress = item.progress,
                            bytesDownloaded = item.bytesDownloaded,
                            totalBytes = item.totalBytes,
                            quality = item.quality,
                            errorMessage = item.errorMessage,
                            filePath = item.filePath,
                        )
                    },
                )
            }
    }

    override fun observeStorage(): Flow<StorageSummary> = downloadDao.observeAll().map { rows ->
        val completed = rows.filter { it.state == DownloadState.COMPLETED.name }
        StorageSummary(
            usedBytes = completed.sumOf { it.totalBytes },
            freeBytes = storage.freeBytes(),
            episodeCount = completed.size,
        )
    }

    override suspend fun enqueue(series: Series, episode: Episode, quality: String?): Boolean {
        if (episode.id <= 0L) return false
        val existing = downloadDao.byEpisode(episode.id)
        if (existing?.state == DownloadState.COMPLETED.name && existing.filePath != null && storage.exists(existing.filePath)) {
            return false
        }
        val now = System.currentTimeMillis()
        downloadDao.upsert(
            DownloadEntity(
                episodeId = episode.id,
                seriesId = episode.seriesId,
                state = DownloadState.QUEUED.name,
                quality = quality,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        scheduler.enqueueDownload(episode.id, settings.current().downloadWifiOnly)
        return true
    }

    override suspend fun pause(episodeId: Long) {
        scheduler.cancelDownload(episodeId)
        val row = downloadDao.byEpisode(episodeId) ?: return
        downloadDao.updateState(
            episodeId,
            DownloadState.PAUSED.name,
            row.progress,
            row.bytesDownloaded,
            row.totalBytes,
            null,
            System.currentTimeMillis(),
        )
    }

    override suspend fun resume(episodeId: Long) {
        val row = downloadDao.byEpisode(episodeId) ?: return
        downloadDao.updateState(
            episodeId,
            DownloadState.QUEUED.name,
            row.progress,
            row.bytesDownloaded,
            row.totalBytes,
            null,
            System.currentTimeMillis(),
        )
        scheduler.enqueueDownload(episodeId, settings.current().downloadWifiOnly)
    }

    override suspend fun cancel(episodeId: Long) {
        scheduler.cancelDownload(episodeId)
        downloadDao.byEpisode(episodeId)?.filePath?.let { storage.delete(it) }
        downloadDao.delete(episodeId)
    }

    override suspend fun remove(episodeId: Long, deleteFile: Boolean) {
        scheduler.cancelDownload(episodeId)
        val row = downloadDao.byEpisode(episodeId)
        if (deleteFile) row?.filePath?.let { storage.delete(it) }
        downloadDao.delete(episodeId)
    }

    override suspend fun reconcile() {
        val completed = downloadDao.completed()
        val now = System.currentTimeMillis()
        completed.forEach { row ->
            val path = row.filePath
            if (path.isNullOrBlank() || !storage.exists(path)) {
                downloadDao.updateState(
                    row.episodeId,
                    DownloadState.MISSING.name,
                    row.progress,
                    row.bytesDownloaded,
                    row.totalBytes,
                    "The file is no longer in the download folder",
                    now,
                )
            }
        }
    }

    override suspend fun localFile(episodeId: Long): String? {
        val row = downloadDao.byEpisode(episodeId) ?: return null
        if (row.state != DownloadState.COMPLETED.name) return null
        val path = row.filePath ?: return null
        return if (storage.exists(path)) path else null
    }

    override suspend fun clearAll() {
        scheduler.cancelAllDownloads()
        downloadDao.all().forEach { row -> row.filePath?.let { storage.delete(it) } }
        downloadDao.deleteAll()
    }
}
