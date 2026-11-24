package app.anilab.local.domain.model

/** A source an extension exposes, as far as the UI cares. */
data class SourceRef(
    val pkg: String,
    val sourceId: Long,
    val name: String,
    val lang: String,
    val nsfw: Boolean = false,
    val enabled: Boolean = true,
    val trusted: Boolean = false,
)

enum class SeriesStatus { ONGOING, COMPLETED, LICENSED, PUBLISHING_FINISHED, CANCELLED, ON_HIATUS, UNKNOWN }

data class Series(
    val id: Long = 0L,
    val sourcePkg: String,
    val sourceId: Long,
    val remoteId: String,
    val title: String,
    val url: String = "",
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: SeriesStatus = SeriesStatus.UNKNOWN,
    val author: String? = null,
    val artist: String? = null,
    val inLibrary: Boolean = false,
    val lastUpdatedAt: Long = 0L,
) {
    val key: String get() = "$sourcePkg:$sourceId:$remoteId"
}

data class Episode(
    val id: Long = 0L,
    val seriesId: Long,
    val remoteId: String,
    val name: String,
    val url: String = "",
    val number: Double = -1.0,
    val filler: Boolean = false,
    val uploadDate: Long = 0L,
    val watched: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val downloadState: DownloadState? = null,
    val downloadedPath: String? = null,
)

enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, FAILED, COMPLETED, MISSING }

data class DownloadItem(
    val episodeId: Long,
    val seriesId: Long,
    val episodeName: String,
    val seriesTitle: String,
    val thumbnailUrl: String?,
    val state: DownloadState,
    val progress: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val quality: String?,
    val errorMessage: String?,
    val filePath: String?,
)

data class DownloadGroup(
    val seriesId: Long,
    val seriesTitle: String,
    val thumbnailUrl: String?,
    val items: List<DownloadItem>,
)

data class StorageSummary(
    val usedBytes: Long,
    val freeBytes: Long,
    val episodeCount: Int,
)

data class PlaybackProgress(
    val episodeId: Long,
    val seriesId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float get() = PlaybackThresholds.fraction(positionMs, durationMs)
    val watched: Boolean get() = PlaybackThresholds.isWatched(positionMs, durationMs)
}

/** One place for every progress rule so player, home, library and history agree. */
object PlaybackThresholds {
    const val CONTINUE_MIN_FRACTION = 0.05f
    const val CONTINUE_MAX_FRACTION = 0.95f
    const val WATCHED_FRACTION = 0.90f
    const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    const val NEXT_EPISODE_COUNTDOWN_MS = 10_000L
    const val SEEK_STEP_MS = 10_000L

    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    fun isWatched(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0L && fraction(positionMs, durationMs) >= WATCHED_FRACTION

    fun isContinueWatching(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val value = fraction(positionMs, durationMs)
        return value > CONTINUE_MIN_FRACTION && value < CONTINUE_MAX_FRACTION
    }

    fun remainingMs(positionMs: Long, durationMs: Long): Long = (durationMs - positionMs).coerceAtLeast(0L)
}

data class ContinueWatchingItem(
    val series: Series,
    val episode: Episode,
    val progress: PlaybackProgress,
)

data class HistoryEntry(
    val id: Long,
    val seriesId: Long,
    val episodeId: Long,
    val seriesTitle: String,
    val episodeName: String,
    val thumbnailUrl: String?,
    val watchedAt: Long,
    val sourcePkg: String,
    val sourceId: Long,
    val remoteId: String,
)

data class HistoryDay(val label: String, val entries: List<HistoryEntry>)

data class Category(val id: Long, val name: String, val order: Int, val seriesCount: Int = 0)

data class RepoSummary(
    val id: Long,
    val name: String,
    val url: String,
    val indexUrl: String,
    val pinned: Boolean,
    val trusted: Boolean,
    val extensionCount: Int,
    val lastUpdatedAt: Long,
    val lastError: String? = null,
)

data class RepoExtension(
    val pkg: String,
    val name: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val nsfw: Boolean,
    val iconUrl: String?,
    val description: String?,
    val apkUrl: String,
)

data class InstalledExtension(
    val pkg: String,
    val name: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val nsfw: Boolean,
    val iconUrl: String?,
    val repoId: Long?,
    val repoName: String?,
    val trusted: Boolean,
    val enabled: Boolean,
    val failureCount: Int,
    val disabledReason: String?,
    val installedAt: Long,
    val hasUpdate: Boolean = false,
    val latestVersionName: String? = null,
    val latestVersionCode: Int? = null,
)

data class RepoExtensions(
    val repoId: Long,
    val repoName: String,
    val trusted: Boolean,
    val pinned: Boolean,
    val updateCount: Int,
    val extensions: List<RepoExtension>,
)

data class UpdateItem(
    val episodeId: Long,
    val seriesId: Long,
    val seriesTitle: String,
    val thumbnailUrl: String?,
    val episodeName: String,
    val sourceName: String,
    val discoveredAt: Long,
)

data class UpdateGroup(val seriesId: Long, val seriesTitle: String, val items: List<UpdateItem>)

data class UpdateFailure(val seriesId: Long, val seriesTitle: String, val sourceName: String, val message: String)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class PlayerQualityMode { AUTO, MANUAL }

enum class SubtitleSize { SMALL, MEDIUM, LARGE }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val reduceMotion: Boolean = false,
    val language: String = "system",
    val updateIntervalHours: Int = 12,
    val wifiOnlyUpdates: Boolean = true,
    val playerQualityMode: PlayerQualityMode = PlayerQualityMode.AUTO,
    val playerPreferredQuality: String? = null,
    val playerSpeed: Float = 1f,
    val subtitleSize: SubtitleSize = SubtitleSize.MEDIUM,
    val subtitleBackground: Boolean = true,
    val downloadTreeUri: String? = null,
    val downloadParallelLimit: Int = 2,
    val downloadWifiOnly: Boolean = true,
    val showNsfw: Boolean = false,
    val dismissedCleartextWarning: Boolean = false,
    val searchHistory: List<String> = emptyList(),
)
