package app.anilab.source.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One page of results. `hasNextPage == false` is what draws the end-of-list marker. */
@Serializable
data class SeriesPage(
    val series: List<RemoteSeries> = emptyList(),
    val hasNextPage: Boolean = false,
)

@Serializable
enum class SeriesStatus {
    @SerialName("ongoing") ONGOING,
    @SerialName("completed") COMPLETED,
    @SerialName("licensed") LICENSED,
    @SerialName("publishing_finished") PUBLISHING_FINISHED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("on_hiatus") ON_HIATUS,
    @SerialName("unknown") UNKNOWN,
}

/**
 * A series as described by an extension. [remoteId] is opaque to the app and only handed back to
 * the extension that produced it.
 */
@Serializable
data class RemoteSeries(
    val remoteId: String,
    val title: String,
    val url: String = "",
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: SeriesStatus = SeriesStatus.UNKNOWN,
    val author: String? = null,
    val artist: String? = null,
    val initialized: Boolean = false,
)

@Serializable
data class RemoteEpisode(
    val remoteId: String,
    val name: String,
    val url: String = "",
    val episodeNumber: Double = -1.0,
    val filler: Boolean = false,
    val uploadDate: Long = 0L,
)

@Serializable
enum class VideoType {
    @SerialName("hls") HLS,
    @SerialName("dash") DASH,
    @SerialName("mp4") MP4,
}

@Serializable
data class SubtitleTrack(
    val url: String,
    /** BCP-47-ish language tag, e.g. "en" or "pt-BR". */
    val lang: String,
    val label: String? = null,
    /** True for tracks muxed into the container; those need no sidecar request. */
    val embedded: Boolean = false,
)

/**
 * A playable link. [headers] are required request headers (Referer, Origin, cookies used for
 * playback only) and are never logged or persisted.
 */
@Serializable
data class Video(
    val url: String,
    val quality: String,
    val type: VideoType,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
)
