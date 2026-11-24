package app.aniverse.local.domain.repository

import app.aniverse.local.domain.model.Episode
import app.aniverse.local.domain.model.Series

/** Everything the player needs to turn an episode id into something playable. */
data class EpisodeContext(
    val series: Series,
    val episode: Episode,
    val queue: List<Episode>,
) {
    val next: Episode? get() = queue.getOrNull(queue.indexOfFirst { it.id == episode.id } + 1)
}

interface PlayerSource {
    suspend fun context(episodeId: Long): EpisodeContext?

    suspend fun videos(context: EpisodeContext): Result<List<PlayableVideo>>
}
