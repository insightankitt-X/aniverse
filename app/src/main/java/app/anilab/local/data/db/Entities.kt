package app.anilab.local.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** An extension repository index, cached so every screen works offline. */
@Entity(
    tableName = "repos",
    indices = [Index(value = ["indexUrl"], unique = true)],
)
data class RepoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val url: String,
    val indexUrl: String,
    val pinned: Boolean = false,
    val trusted: Boolean = false,
    val extensionCount: Int = 0,
    val lastUpdatedAt: Long = 0L,
    val lastError: String? = null,
)

/**
 * One row per extension package. It always carries the newest metadata this app has seen for the
 * package and, for installed extensions, its installed version and health.
 */
@Entity(
    tableName = "extensions",
    indices = [Index("repoId"), Index("installed"), Index("lang")],
    foreignKeys = [
        ForeignKey(
            entity = RepoEntity::class,
            parentColumns = ["id"],
            childColumns = ["repoId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class ExtensionEntity(
    @PrimaryKey val pkg: String,
    val name: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val nsfw: Boolean = false,
    val iconUrl: String? = null,
    val description: String? = null,
    val apkUrl: String,
    val repoId: Long? = null,
    val installed: Boolean = false,
    val enabled: Boolean = false,
    val installedVersionCode: Int = 0,
    val installedVersionName: String = "",
    val installedAt: Long = 0L,
    val failureCount: Int = 0,
    val disabledReason: String? = null,
    val lastSeenAt: Long = 0L,
)

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["sourcePkg", "sourceId", "remoteId"], unique = true),
        Index("sourcePkg"),
        Index("inLibrary"),
        Index("lastUpdatedAt"),
    ],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourcePkg: String,
    val sourceId: Long,
    val remoteId: String,
    val title: String,
    val url: String = "",
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String = "UNKNOWN",
    val author: String? = null,
    val artist: String? = null,
    val inLibrary: Boolean = false,
    val lastUpdatedAt: Long = 0L,
    val lastFetchedAt: Long = 0L,
    val addedToLibraryAt: Long = 0L,
)

@Entity(
    tableName = "episodes",
    indices = [Index("seriesId"), Index(value = ["seriesId", "remoteId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val seriesId: Long,
    val remoteId: String,
    val name: String,
    val url: String = "",
    val number: Double = -1.0,
    val filler: Boolean = false,
    val uploadDate: Long = 0L,
    val watched: Boolean = false,
    val watchedAt: Long = 0L,
)

@Entity(
    tableName = "progress",
    indices = [Index("seriesId"), Index("updatedAt")],
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProgressEntity(
    @PrimaryKey val episodeId: Long,
    val seriesId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/** One row per episode: re-watching replaces the timestamp instead of piling up rows. */
@Entity(
    tableName = "history",
    indices = [Index("watchedAt"), Index("seriesId")],
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HistoryEntity(
    @PrimaryKey val episodeId: Long,
    val seriesId: Long,
    val watchedAt: Long,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true), Index("sortOrder")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "series_categories",
    primaryKeys = ["seriesId", "categoryId"],
    indices = [Index("categoryId")],
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SeriesCategoryCrossRef(
    val seriesId: Long,
    val categoryId: Long,
)

@Entity(
    tableName = "downloads",
    indices = [Index(value = ["episodeId"], unique = true), Index("seriesId"), Index("state")],
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val episodeId: Long,
    val seriesId: Long,
    val state: String,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val quality: String? = null,
    val filePath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Cached extension responses, keyed by source, kind, page and query. */
@Entity(tableName = "source_cache", indices = [Index("sourcePkg"), Index("updatedAt")])
data class SourceCacheEntity(
    @PrimaryKey val cacheKey: String,
    val sourcePkg: String,
    val sourceId: Long,
    val kind: String,
    val payload: String,
    val updatedAt: Long,
)

/** Episodes discovered by a library refresh, drives the Updates screen and its badge. */
@Entity(
    tableName = "update_entries",
    indices = [Index("seriesId"), Index(value = ["episodeId"], unique = true), Index("seen")],
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class UpdateEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val seriesId: Long,
    val episodeId: Long,
    val discoveredAt: Long,
    val seen: Boolean = false,
)
