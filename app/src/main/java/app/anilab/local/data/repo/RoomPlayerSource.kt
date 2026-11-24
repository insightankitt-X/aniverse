package app.anilab.local.data.repo

import app.anilab.local.data.db.EpisodeDao
import app.anilab.local.data.db.ProgressDao
import app.anilab.local.data.db.SeriesDao
import app.anilab.local.data.toDomain
import app.anilab.local.domain.repository.BrowseRepository
import app.anilab.local.domain.repository.EpisodeContext
import app.anilab.local.domain.repository.PlayableVideo
import app.anilab.local.domain.repository.PlayerSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPlayerSource @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val seriesDao: SeriesDao,
    private val progressDao: ProgressDao,
    private val browseRepository: BrowseRepository,
) : PlayerSource {

    override suspend fun context(episodeId: Long): EpisodeContext? {
        val episodeEntity = episodeDao.byId(episodeId) ?: return null
        val seriesEntity = seriesDao.byId(episodeEntity.seriesId) ?: return null
        val progress = progressDao.byEpisode(episodeId)
        val episode = episodeEntity.toDomain(progress = progress)
        val queue = episodeDao.forSeries(episodeEntity.seriesId).map { entity ->
            entity.toDomain(progress = progressDao.byEpisode(entity.id))
        }
        return EpisodeContext(series = seriesEntity.toDomain(), episode = episode, queue = queue)
    }

    override suspend fun videos(context: EpisodeContext): Result<List<PlayableVideo>> =
        browseRepository.videos(context.series, context.episode)
}
