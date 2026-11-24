package app.anilab.local.data.repo

import app.anilab.local.data.db.EpisodeDao
import app.anilab.local.data.db.ExtensionDao
import app.anilab.local.data.db.HistoryDao
import app.anilab.local.data.db.ProgressDao
import app.anilab.local.data.db.SeriesDao
import app.anilab.local.data.db.UpdateDao
import app.anilab.local.data.toDomain
import app.anilab.local.domain.model.ContinueWatchingItem
import app.anilab.local.domain.model.Episode
import app.anilab.local.domain.model.HistoryDay
import app.anilab.local.domain.model.HistoryEntry
import app.anilab.local.domain.model.PlaybackThresholds
import app.anilab.local.domain.model.Series
import app.anilab.local.domain.model.UpdateItem
import app.anilab.local.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
    private val episodeDao: EpisodeDao,
    private val seriesDao: SeriesDao,
    private val updateDao: UpdateDao,
    private val extensionDao: ExtensionDao,
    private val writer: PlaybackStateWriter,
) : HistoryRepository {

    override fun observeHistory(): Flow<List<HistoryDay>> = historyDao.observeAll().map { rows ->
        if (rows.isEmpty()) return@map emptyList()
        val episodes = rows.mapNotNull { row -> episodeDao.byId(row.episodeId)?.let { row to it } }
        val seriesRows = seriesDao.byIds(episodes.map { it.second.seriesId }.distinct()).associateBy { it.id }
        val entries = episodes.mapNotNull { (row, episode) ->
            val series = seriesRows[episode.seriesId] ?: return@mapNotNull null
            HistoryEntry(
                id = row.episodeId,
                seriesId = series.id,
                episodeId = episode.id,
                seriesTitle = series.title,
                episodeName = episode.name,
                thumbnailUrl = series.thumbnailUrl,
                watchedAt = row.watchedAt,
                sourcePkg = series.sourcePkg,
                sourceId = series.sourceId,
                remoteId = series.remoteId,
            )
        }
        entries.groupBy { dayLabel(it.watchedAt) }.map { (label, list) -> HistoryDay(label, list) }
    }

    override fun observeContinueWatching(): Flow<List<ContinueWatchingItem>> = progressDao.observeRecent().map { rows ->
        rows.filter { PlaybackThresholds.isContinueWatching(it.positionMs, it.durationMs) }
            .mapNotNull { progress ->
                val episode = episodeDao.byId(progress.episodeId) ?: return@mapNotNull null
                val series = seriesDao.byId(episode.seriesId) ?: return@mapNotNull null
                ContinueWatchingItem(
                    series = series.toDomain(),
                    episode = episode.toDomain(progress = progress),
                    progress = progress.toDomain(),
                )
            }
    }

    override fun observeNewEpisodes(): Flow<List<UpdateItem>> = updateDao.observeAll().map { entries ->
        if (entries.isEmpty()) return@map emptyList()
        val names = extensionDao.installed().associate { it.pkg to it.name }
        entries.mapNotNull { entry ->
            val episode = episodeDao.byId(entry.episodeId) ?: return@mapNotNull null
            val series = seriesDao.byId(episode.seriesId) ?: return@mapNotNull null
            UpdateItem(
                episodeId = episode.id,
                seriesId = series.id,
                seriesTitle = series.title,
                thumbnailUrl = series.thumbnailUrl,
                episodeName = episode.name,
                sourceName = names[series.sourcePkg] ?: series.sourcePkg,
                discoveredAt = entry.discoveredAt,
            )
        }
    }

    override suspend fun record(series: Series, episode: Episode, positionMs: Long, durationMs: Long) {
        writer.save(
            seriesId = episode.seriesId,
            episodeId = episode.id,
            positionMs = positionMs,
            durationMs = durationMs,
            recordHistory = true,
        )
    }

    override suspend fun deleteEntry(episodeId: Long) = historyDao.delete(episodeId)

    override suspend fun clearHistory() = historyDao.clear()

    private fun dayLabel(timestamp: Long): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
    }
}
