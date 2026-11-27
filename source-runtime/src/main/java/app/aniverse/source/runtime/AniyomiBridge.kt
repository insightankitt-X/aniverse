package app.aniverse.source.runtime

import app.aniverse.source.api.Filter
import app.aniverse.source.api.FilterList
import app.aniverse.source.api.RemoteEpisode
import app.aniverse.source.api.RemoteSeries
import app.aniverse.source.api.SelectOption
import app.aniverse.source.api.SeriesPage
import app.aniverse.source.api.SeriesStatus
import app.aniverse.source.api.Source
import app.aniverse.source.api.SubtitleTrack
import app.aniverse.source.api.TriState
import app.aniverse.source.api.Video as ApiVideo
import app.aniverse.source.api.VideoType
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video as AniyomiVideo
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource

/**
 * Presents an Aniyomi `AnimeSource` to AniVerse's UI through the app's own [Source] contract.
 *
 * Model identity matters here: extensions often compare the filter objects they returned from
 * `getFilterList()` with the ones the app hands back to `getSearchAnime()`, so the adapter keeps
 * the extension's own filter instances and only copies the state across.
 */
internal class AniyomiSourceAdapter(
    private val delegate: AnimeSource,
) : Source {

    override val sourceId: Long = delegate.id
    override val name: String = delegate.name
    override val lang: String = (delegate as? AnimeCatalogueSource)?.lang ?: "all"
    override val versionId: Int = (delegate as? AnimeHttpSource)?.versionId ?: 1

    private val catalogue: AnimeCatalogueSource? = delegate as? AnimeCatalogueSource
    private var cachedFilters: AnimeFilterList? = null

    override suspend fun getPopular(page: Int): SeriesPage =
        catalogue?.getPopularAnime(page)?.toSeriesPage() ?: SeriesPage()

    override suspend fun getLatest(page: Int): SeriesPage {
        val source = catalogue ?: return SeriesPage()
        if (!source.supportsLatest) return SeriesPage()
        return source.getLatestUpdates(page).toSeriesPage()
    }

    override suspend fun search(query: String, filters: FilterList, page: Int): SeriesPage {
        val source = catalogue ?: return SeriesPage()
        return source.getSearchAnime(page, query, applyFilters(filters)).toSeriesPage()
    }

    override suspend fun getDetails(series: RemoteSeries): RemoteSeries =
        delegate.getAnimeDetails(series.toSAnime()).toRemoteSeries()

    override suspend fun getEpisodes(series: RemoteSeries): List<RemoteEpisode> =
        delegate.getEpisodeList(series.toSAnime(initialized = true)).map { it.toRemoteEpisode() }

    override suspend fun getVideoList(episode: RemoteEpisode): List<ApiVideo> {
        val target = episode.toSEpisode()
        val videos = mutableListOf<AniyomiVideo>()

        val hosters = runCatching { delegate.getHosterList(target) }.getOrElse { emptyList() }
        hosters.forEach { hoster ->
            val inline = hoster.videoList
            if (inline != null) {
                videos += inline
            } else {
                videos += runCatching { delegate.getVideoList(hoster) }.getOrElse { emptyList() }
            }
        }

        val httpSource = delegate as? AnimeHttpSource
        val resolved = videos.map { video ->
            if (video.initialized || video.videoUrl.isNotBlank()) {
                video
            } else {
                httpSource?.let { source -> runCatching { source.resolveVideo(video) }.getOrNull() } ?: video
            }
        }
        return resolved
            .filter { UrlGuard.isAllowed(it.videoUrl) }
            .map { it.toApiVideo() }
    }

    override suspend fun getFilterList(): FilterList {
        val source = catalogue ?: return FilterList.Empty
        val list = runCatching { source.getFilterList() }.getOrNull() ?: return FilterList.Empty
        cachedFilters = list
        return FilterList(list.mapIndexed { index, filter -> filter.toApiFilter(index) })
    }

    /**
     * Copies the user's choices onto the extension's own filter objects. Falls back to a fresh
     * translation when the cached list is missing (e.g. the host restarted).
     */
    private fun applyFilters(apiFilters: FilterList): AnimeFilterList {
        val target = cachedFilters ?: catalogue?.let { runCatching { it.getFilterList() }.getOrNull() }
            ?.also { cachedFilters = it }
            ?: return AnimeFilterList()

        apiFilters.filters.forEachIndexed { index, apiFilter ->
            val aniyomiFilter = target.getOrNull(index) ?: return@forEachIndexed
            applyState(aniyomiFilter, apiFilter)
        }
        return target
    }

    private fun applyState(aniyomiFilter: AnimeFilter<*>, apiFilter: Filter) {
        when (aniyomiFilter) {
            is AnimeFilter.Text -> if (apiFilter is Filter.Text) aniyomiFilter.state = apiFilter.value
            is AnimeFilter.CheckBox -> if (apiFilter is Filter.CheckBox) aniyomiFilter.state = apiFilter.state
            is AnimeFilter.TriState -> if (apiFilter is Filter.TriState) {
                aniyomiFilter.state = when (apiFilter.state) {
                    TriState.IGNORE -> AnimeFilter.TriState.STATE_IGNORE
                    TriState.INCLUDE -> AnimeFilter.TriState.STATE_INCLUDE
                    TriState.EXCLUDE -> AnimeFilter.TriState.STATE_EXCLUDE
                }
            }

            is AnimeFilter.Select<*> -> if (apiFilter is Filter.Select) {
                aniyomiFilter.state = apiFilter.selectedIndex.coerceIn(0, maxOf(0, aniyomiFilter.values.size - 1))
            }

            is AnimeFilter.Sort -> if (apiFilter is Filter.Sort) {
                aniyomiFilter.state = AnimeFilter.Sort.Selection(
                    index = apiFilter.selectedIndex.coerceIn(0, maxOf(0, aniyomiFilter.values.size - 1)),
                    ascending = apiFilter.ascending,
                )
            }

            is AnimeFilter.Group<*> -> if (apiFilter is Filter.Group) {
                aniyomiFilter.state.zip(apiFilter.filters).forEach { (child, apiChild) ->
                    when (child) {
                        is AnimeFilter.CheckBox -> if (apiChild is Filter.CheckBox) child.state = apiChild.state
                        is AnimeFilter.TriState -> if (apiChild is Filter.TriState) {
                            child.state = when (apiChild.state) {
                                TriState.IGNORE -> AnimeFilter.TriState.STATE_IGNORE
                                TriState.INCLUDE -> AnimeFilter.TriState.STATE_INCLUDE
                                TriState.EXCLUDE -> AnimeFilter.TriState.STATE_EXCLUDE
                            }
                        }

                        else -> Unit
                    }
                }
            }

            else -> Unit
        }
    }

    private fun AnimeFilter<*>.toApiFilter(index: Int): Filter {
        val key = "f$index"
        return when (this) {
            is AnimeFilter.Header -> Filter.Header(key, name)
            is AnimeFilter.Separator -> Filter.Separator(key, name)
            is AnimeFilter.Text -> Filter.Text(key, name, state)
            is AnimeFilter.CheckBox -> Filter.CheckBox(key, name, state)
            is AnimeFilter.TriState -> Filter.TriState(
                key,
                name,
                when (state) {
                    AnimeFilter.TriState.STATE_INCLUDE -> TriState.INCLUDE
                    AnimeFilter.TriState.STATE_EXCLUDE -> TriState.EXCLUDE
                    else -> TriState.IGNORE
                },
            )

            is AnimeFilter.Select<*> -> Filter.Select(
                key,
                name,
                values.map { SelectOption(it.toString()) },
                state.coerceIn(0, maxOf(0, values.size - 1)),
            )

            is AnimeFilter.Sort -> Filter.Sort(
                key,
                name,
                values.map { SelectOption(it) },
                state?.index?.coerceIn(0, maxOf(0, values.size - 1)) ?: 0,
                state?.ascending ?: true,
            )

            is AnimeFilter.Group<*> -> Filter.Group(
                key,
                name,
                state.mapIndexedNotNull { childIndex, child ->
                    val childKey = "$key-g$childIndex"
                    when (child) {
                        is AnimeFilter.CheckBox -> Filter.CheckBox(childKey, child.name, child.state)
                        is AnimeFilter.TriState -> Filter.TriState(
                            childKey,
                            child.name,
                            when (child.state) {
                                AnimeFilter.TriState.STATE_INCLUDE -> TriState.INCLUDE
                                AnimeFilter.TriState.STATE_EXCLUDE -> TriState.EXCLUDE
                                else -> TriState.IGNORE
                            },
                        )

                        else -> null
                    }
                },
            )
        }
    }
}

internal fun AnimesPage.toSeriesPage(): SeriesPage = SeriesPage(
    series = animes.map { it.toRemoteSeries() },
    hasNextPage = hasNextPage,
)

internal fun SAnime.toRemoteSeries(): RemoteSeries = RemoteSeries(
    remoteId = url.ifBlank { title },
    title = title,
    url = url,
    thumbnailUrl = thumbnail_url,
    description = description,
    genres = genre?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
    status = status.toSeriesStatus(),
    author = author,
    artist = artist,
    initialized = initialized,
)

internal fun SEpisode.toRemoteEpisode(): RemoteEpisode = RemoteEpisode(
    remoteId = url.ifBlank { name },
    name = name,
    url = url,
    episodeNumber = episode_number.toDouble(),
    filler = fillermark,
    uploadDate = date_upload,
)

private fun RemoteSeries.toSAnime(initialized: Boolean = this.initialized): SAnime = SAnime.create().apply {
    url = this@toSAnime.url.ifBlank { remoteId }
    title = this@toSAnime.title
    thumbnail_url = thumbnailUrl
    description = this@toSAnime.description
    genre = genres.joinToString(", ").ifBlank { null }
    status = this@toSAnime.status.toAniyomiStatus()
    author = this@toSAnime.author
    artist = this@toSAnime.artist
    this.initialized = initialized
}

private fun RemoteEpisode.toSEpisode(): SEpisode = SEpisode.create().apply {
    url = this@toSEpisode.url.ifBlank { remoteId }
    name = this@toSEpisode.name
    episode_number = this@toSEpisode.episodeNumber.toFloat()
    fillermark = this@toSEpisode.filler
    date_upload = this@toSEpisode.uploadDate
}

private fun AniyomiVideo.toApiVideo(): ApiVideo = ApiVideo(
    url = videoUrl,
    quality = videoTitle.ifBlank { "Video" },
    type = videoTypeOf(videoUrl),
    headers = headers?.toMultimap()
        ?.mapValues { (_, values) -> values.firstOrNull().orEmpty() }
        ?.filterValues { it.isNotBlank() }
        .orEmpty(),
    subtitles = subtitleTracks.map { SubtitleTrack(url = it.url, lang = it.lang) },
)

private fun videoTypeOf(url: String): VideoType {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".m3u8") -> VideoType.HLS
        path.endsWith(".mpd") -> VideoType.DASH
        else -> VideoType.MP4
    }
}

private fun SeriesStatus.toAniyomiStatus(): Int = when (this) {
    SeriesStatus.ONGOING -> SAnime.ONGOING
    SeriesStatus.COMPLETED -> SAnime.COMPLETED
    SeriesStatus.LICENSED -> SAnime.LICENSED
    SeriesStatus.PUBLISHING_FINISHED -> SAnime.PUBLISHING_FINISHED
    SeriesStatus.CANCELLED -> SAnime.CANCELLED
    SeriesStatus.ON_HIATUS -> SAnime.ON_HIATUS
    SeriesStatus.UNKNOWN -> SAnime.UNKNOWN
}

private fun Int.toSeriesStatus(): SeriesStatus = when (this) {
    SAnime.ONGOING -> SeriesStatus.ONGOING
    SAnime.COMPLETED -> SeriesStatus.COMPLETED
    SAnime.LICENSED -> SeriesStatus.LICENSED
    SAnime.PUBLISHING_FINISHED -> SeriesStatus.PUBLISHING_FINISHED
    SAnime.CANCELLED -> SeriesStatus.CANCELLED
    SAnime.ON_HIATUS -> SeriesStatus.ON_HIATUS
    else -> SeriesStatus.UNKNOWN
}
