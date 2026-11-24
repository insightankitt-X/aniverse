package app.aniverse.local.data.repo

import app.aniverse.local.data.db.EpisodeDao
import app.aniverse.local.data.db.ProgressDao
import app.aniverse.local.data.toDomain
import app.aniverse.local.domain.model.Episode
import app.aniverse.local.domain.model.PlaybackProgress
import app.aniverse.local.domain.model.PlaybackThresholds
import app.aniverse.local.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomProgressRepository @Inject constructor(
    private val progressDao: ProgressDao,
    private val episodeDao: EpisodeDao,
    private val writer: PlaybackStateWriter,
) : ProgressRepository {

    override fun observe(episodeId: Long): Flow<PlaybackProgress?> =
        progressDao.observe(episodeId).map { it?.toDomain() }

    override suspend fun save(seriesId: Long, episodeId: Long, positionMs: Long, durationMs: Long) {
        writer.save(seriesId, episodeId, positionMs, durationMs, recordHistory = true)
    }

    override suspend fun progressFor(seriesId: Long): Map<Long, PlaybackProgress> =
        progressDao.forSeries(seriesId).associate { it.episodeId to it.toDomain() }

    override suspend fun resumeEpisode(seriesId: Long): Episode? {
        val partial = progressDao.forSeries(seriesId)
            .filter { PlaybackThresholds.isContinueWatching(it.positionMs, it.durationMs) }
            .maxByOrNull { it.updatedAt }
        if (partial != null) return episodeDao.byId(partial.episodeId)?.toDomain(progress = partial)

        episodeDao.firstUnwatched(seriesId)?.let { return it.toDomain() }
        val newest = episodeDao.newest(seriesId) ?: return null
        return newest.toDomain()
    }
}
