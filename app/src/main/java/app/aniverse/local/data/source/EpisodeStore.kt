package app.aniverse.local.data.source

import app.aniverse.local.data.db.EpisodeDao
import app.aniverse.local.data.db.EpisodeEntity
import app.aniverse.local.data.toEntity
import app.aniverse.source.api.RemoteEpisode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists an episode list without losing local state: existing rows keep their id, watched flag and
 * watched timestamp, which is what keeps progress, history and downloads attached across refreshes.
 */
@Singleton
class EpisodeStore @Inject constructor(
    private val episodeDao: EpisodeDao,
) {
    suspend fun store(seriesId: Long, episodes: List<RemoteEpisode>): List<EpisodeEntity> {
        if (episodes.isEmpty()) return emptyList()
        val existing = episodeDao.forSeries(seriesId).associateBy { it.remoteId }
        val entities = episodes.map { remote -> remote.toEntity(seriesId, existing[remote.remoteId]) }
        episodeDao.insertAll(entities)
        return episodeDao.forSeries(seriesId)
    }

    /** Returns the episodes that did not exist before, used for the Updates screen. */
    suspend fun storeAndFindNew(seriesId: Long, episodes: List<RemoteEpisode>): List<EpisodeEntity> {
        if (episodes.isEmpty()) return emptyList()
        val known = episodeDao.forSeries(seriesId).map { it.remoteId }.toSet()
        return store(seriesId, episodes).filter { it.remoteId !in known }
    }
}
