package app.anilab.local.data.backup

import kotlinx.serialization.Serializable

/** Versioned, plain-JSON backup. No APKs, no media, no secrets. */
@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long = 0L,
    val appVersion: String = "1.0.0",
    val repositories: List<RepoBackup> = emptyList(),
    val extensions: List<ExtensionBackup> = emptyList(),
    val series: List<SeriesBackup> = emptyList(),
    val episodes: List<EpisodeBackup> = emptyList(),
    val categories: List<CategoryBackup> = emptyList(),
    val progress: List<ProgressBackup> = emptyList(),
    val history: List<HistoryBackup> = emptyList(),
    val settings: SettingsBackup? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class RepoBackup(
    val name: String,
    val url: String,
    val indexUrl: String,
    val pinned: Boolean = false,
    val trusted: Boolean = false,
)

@Serializable
data class ExtensionBackup(
    val pkg: String,
    val name: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val nsfw: Boolean = false,
    val iconUrl: String? = null,
    val description: String? = null,
    val apkUrl: String,
    val repoIndexUrl: String? = null,
    val installed: Boolean = false,
    val enabled: Boolean = false,
    val installedVersionCode: Int = 0,
    val installedVersionName: String = "",
    val installedAt: Long = 0L,
)

@Serializable
data class SeriesRef(
    val sourcePkg: String,
    val sourceId: Long,
    val remoteId: String,
)

@Serializable
data class SeriesBackup(
    val ref: SeriesRef,
    val title: String,
    val url: String = "",
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String = "UNKNOWN",
    val author: String? = null,
    val artist: String? = null,
    val inLibrary: Boolean = true,
    val addedToLibraryAt: Long = 0L,
    val lastUpdatedAt: Long = 0L,
)

@Serializable
data class EpisodeBackup(
    val series: SeriesRef,
    val remoteId: String,
    val name: String,
    val url: String = "",
    val number: Double = -1.0,
    val filler: Boolean = false,
    val uploadDate: Long = 0L,
    val watched: Boolean = false,
    val watchedAt: Long = 0L,
)

@Serializable
data class CategoryBackup(
    val name: String,
    val sortOrder: Int = 0,
    val series: List<SeriesRef> = emptyList(),
)

@Serializable
data class ProgressBackup(
    val series: SeriesRef,
    val episodeRemoteId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Serializable
data class HistoryBackup(
    val series: SeriesRef,
    val episodeRemoteId: String,
    val watchedAt: Long,
)

@Serializable
data class SettingsBackup(
    val themeMode: String = "SYSTEM",
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val reduceMotion: Boolean = false,
    val language: String = "system",
    val updateIntervalHours: Int = 12,
    val wifiOnlyUpdates: Boolean = true,
    val playerQualityMode: String = "AUTO",
    val playerPreferredQuality: String? = null,
    val playerSpeed: Float = 1f,
    val subtitleSize: String = "MEDIUM",
    val subtitleBackground: Boolean = true,
    val downloadTreeUri: String? = null,
    val downloadParallelLimit: Int = 2,
    val downloadWifiOnly: Boolean = true,
    val showNsfw: Boolean = false,
)
