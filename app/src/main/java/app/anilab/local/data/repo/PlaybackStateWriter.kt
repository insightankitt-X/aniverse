package app.anilab.local.data.repo

import app.anilab.local.data.db.EpisodeDao
import app.anilab.local.data.db.HistoryDao
import app.anilab.local.data.db.HistoryEntity
import app.anilab.local.data.db.ProgressDao
import app.anilab.local.data.db.ProgressEntity
import app.anilab.local.domain.model.PlaybackThresholds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single writer for playback state so the player, home rail, library, history and the watched flag
 * can never disagree about the same episode.
 */
@Singleton
class PlaybackStateWriter @Inject constructor(
    private val progressDao: ProgressDao,
    private val historyDao: HistoryDao,
    private val episodeDao: EpisodeDao,
) {
    suspend fun save(
        seriesId: Long,
        episodeId: Long,
        positionMs: Long,
        durationMs: Long,
        recordHistory: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        if (episodeId <= 0L || seriesId <= 0L) return
        progressDao.upsert(
            ProgressEntity(
                episodeId = episodeId,
                seriesId = seriesId,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                updatedAt = now,
            ),
        )
        if (recordHistory) {
            historyDao.upsert(HistoryEntity(episodeId = episodeId, seriesId = seriesId, watchedAt = now))
        }
        // Watched is sticky: seeking back inside an episode never un-watches it.
        if (PlaybackThresholds.isWatched(positionMs, durationMs)) {
            val episode = episodeDao.byId(episodeId)
            if (episode != null && !episode.watched) {
                episodeDao.setWatched(episodeId, true, now)
            }
        }
    }
}
