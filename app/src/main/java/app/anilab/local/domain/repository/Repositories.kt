package app.anilab.local.domain.repository

import android.net.Uri
import app.anilab.local.domain.model.Category
import app.anilab.local.domain.model.ContinueWatchingItem
import app.anilab.local.domain.model.DownloadGroup
import app.anilab.local.domain.model.Episode
import app.anilab.local.domain.model.HistoryDay
import app.anilab.local.domain.model.InstalledExtension
import app.anilab.local.domain.model.PlaybackProgress
import app.anilab.local.domain.model.RepoExtension
import app.anilab.local.domain.model.RepoExtensions
import app.anilab.local.domain.model.RepoSummary
import app.anilab.local.domain.model.Series
import app.anilab.local.domain.model.Settings
import app.anilab.local.domain.model.SourceRef
import app.anilab.local.domain.model.StorageSummary
import app.anilab.local.domain.model.UpdateFailure
import app.anilab.local.domain.model.UpdateGroup
import app.anilab.local.domain.model.UpdateItem
import app.anilab.source.api.FilterList
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------------------------
// Extension repositories (the GitHub-hosted index files)
// ---------------------------------------------------------------------------------------------

interface RepoRepository {
    fun observeRepos(): Flow<List<RepoSummary>>

    suspend fun addRepo(url: String): AddRepoResult

    /** Returns true when the index was refreshed, false when the cached copy is still being shown. */
    suspend fun refresh(repoId: Long): Boolean

    /** Returns how many repositories failed to refresh. */
    suspend fun refreshAll(): Int

    suspend fun setPinned(repoId: Long, pinned: Boolean)

    suspend fun setTrusted(repoId: Long, trusted: Boolean)

    /** Removes the repository and its index. Installed extensions stay installed. */
    suspend fun delete(repoId: Long)

    suspend fun availableExtensions(repoId: Long): List<RepoExtension>
}

sealed interface AddRepoResult {
    data class Added(val repo: RepoSummary) : AddRepoResult
    data class Rejected(val error: RepoInputError) : AddRepoResult
}

enum class RepoInputError {
    EMPTY,
    NOT_HTTPS,
    UNREACHABLE,
    NOT_AN_INDEX,
    ALREADY_ADDED,
}

// ---------------------------------------------------------------------------------------------
// Installed / available extensions
// ---------------------------------------------------------------------------------------------

interface ExtensionRepository {
    fun observeInstalled(): Flow<List<InstalledExtension>>

    fun observeAvailable(): Flow<List<RepoExtensions>>

    fun observeUpdateCount(): Flow<Int>

    suspend fun install(
        pkg: String,
        repoId: Long?,
        confirmed: Boolean,
        onProgress: (Float) -> Unit,
    ): InstallOutcome

    suspend fun update(pkg: String, confirmed: Boolean, onProgress: (Float) -> Unit): InstallOutcome

    suspend fun updateAll(onProgress: (UpdateProgress) -> Unit): UpdateReport

    suspend fun cancelInstall()

    suspend fun uninstall(pkg: String)

    suspend fun setEnabled(pkg: String, enabled: Boolean)

    suspend fun setNsfw(pkg: String, nsfw: Boolean)

    /** Records a failed call and disables the extension after repeated failures. */
    suspend fun reportFailure(pkg: String, message: String)

    suspend fun clearFailures(pkg: String)
}

data class UpdateProgress(
    val pkg: String,
    val name: String,
    val index: Int,
    val total: Int,
    val fraction: Float,
)

data class UpdateFailureItem(val pkg: String, val name: String, val message: String)

data class UpdateReport(
    val updated: List<String> = emptyList(),
    val failed: List<UpdateFailureItem> = emptyList(),
    val skipped: List<UpdateFailureItem> = emptyList(),
) {
    val hasFailures: Boolean get() = failed.isNotEmpty()
}

sealed interface InstallOutcome {
    data class Installed(val pkg: String) : InstallOutcome

    data class NeedsConfirmation(
        val pkg: String,
        val reasons: List<ConfirmationReason>,
        val permissions: List<String>,
    ) : InstallOutcome

    data object Cancelled : InstallOutcome

    data class Failed(val reason: InstallFailure, val message: String) : InstallOutcome
}

enum class ConfirmationReason { UNTRUSTED_REPOSITORY, UNUSUAL_PERMISSIONS }

enum class InstallFailure {
    CANCELLED,
    SIGNATURE_MISMATCH,
    DOWNGRADE,
    CORRUPT_APK,
    PACKAGE_MISMATCH,
    DOWNLOAD_FAILED,
    INCOMPATIBLE_API,
    INSTALLER_BLOCKED,
    NOT_FOUND,
    UNKNOWN,
}

// ---------------------------------------------------------------------------------------------
// Browsing extension sources
// ---------------------------------------------------------------------------------------------

data class SourcePage(val items: List<Series>, val hasNextPage: Boolean)

data class PlayableSubtitle(
    val url: String,
    val lang: String,
    val label: String,
    val embedded: Boolean,
)

data class PlayableVideo(
    val url: String,
    val quality: String,
    val kind: String,
    val headers: Map<String, String>,
    val subtitles: List<PlayableSubtitle>,
)

interface BrowseRepository {
    fun observeSources(): Flow<List<SourceRef>>

    suspend fun popular(sourcePkg: String, sourceId: Long, page: Int): Result<SourcePage>

    suspend fun latest(sourcePkg: String, sourceId: Long, page: Int): Result<SourcePage>

    suspend fun search(
        sourcePkg: String,
        sourceId: Long,
        query: String,
        filters: FilterList,
        page: Int,
    ): Result<SourcePage>

    suspend fun filters(sourcePkg: String, sourceId: Long): Result<FilterList>

    suspend fun details(series: Series): Result<Series>

    suspend fun episodes(series: Series): Result<List<Episode>>

    suspend fun videos(series: Series, episode: Episode): Result<List<PlayableVideo>>

    suspend fun related(series: Series): List<Series>
}

// ---------------------------------------------------------------------------------------------
// Library, history, progress
// ---------------------------------------------------------------------------------------------

data class LibraryRefreshReport(
    val checked: Int,
    val newEpisodes: Int,
    val failures: List<UpdateFailure>,
)

interface LibraryRepository {
    fun observeLibrary(): Flow<List<Series>>

    fun observeRecentlyAdded(): Flow<List<Series>>

    fun observeCategories(): Flow<List<Category>>

    fun observeUpdates(): Flow<List<UpdateGroup>>

    fun observeUpdateCount(): Flow<Int>

    fun observeSeries(seriesId: Long): Flow<Series?>

    fun observeEpisodes(seriesId: Long): Flow<List<Episode>>

    suspend fun isInLibrary(series: Series): Boolean

    /** Returns true when the series is now in the library. */
    suspend fun toggleLibrary(series: Series, categoryIds: List<Long> = emptyList()): Boolean

    suspend fun setCategories(seriesId: Long, categoryIds: List<Long>)

    suspend fun createCategory(name: String): Long

    suspend fun renameCategory(categoryId: Long, name: String)

    suspend fun deleteCategory(categoryId: Long)

    suspend fun reorderCategories(orderedIds: List<Long>)

    suspend fun markWatched(episodeId: Long, watched: Boolean)

    suspend fun bulkMarkWatched(episodeIds: List<Long>, watched: Boolean)

    suspend fun removeFromLibrary(seriesIds: List<Long>)

    suspend fun refreshLibrary(onProgress: (Int, Int) -> Unit): LibraryRefreshReport

    suspend fun lastCheckAt(): Long

    suspend fun clearUpdates()

    /** Stores an episode list fetched from a source, keeping watched state and local ids. */
    suspend fun cacheEpisodes(series: Series): Result<List<Episode>>
}

interface HistoryRepository {
    fun observeHistory(): Flow<List<HistoryDay>>

    fun observeContinueWatching(): Flow<List<ContinueWatchingItem>>

    fun observeNewEpisodes(): Flow<List<UpdateItem>>

    suspend fun record(series: Series, episode: Episode, positionMs: Long, durationMs: Long)

    suspend fun deleteEntry(episodeId: Long)

    suspend fun clearHistory()
}

interface ProgressRepository {
    fun observe(episodeId: Long): Flow<PlaybackProgress?>

    suspend fun save(seriesId: Long, episodeId: Long, positionMs: Long, durationMs: Long)

    suspend fun progressFor(seriesId: Long): Map<Long, PlaybackProgress>

    /** First unwatched or partially watched episode, used by Play/Resume. */
    suspend fun resumeEpisode(seriesId: Long): Episode?
}

// ---------------------------------------------------------------------------------------------
// Downloads
// ---------------------------------------------------------------------------------------------

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadGroup>>

    fun observeStorage(): Flow<StorageSummary>

    suspend fun enqueue(series: Series, episode: Episode, quality: String?): Boolean

    suspend fun pause(episodeId: Long)

    suspend fun resume(episodeId: Long)

    suspend fun cancel(episodeId: Long)

    suspend fun remove(episodeId: Long, deleteFile: Boolean)

    suspend fun reconcile()

    suspend fun localFile(episodeId: Long): String?

    suspend fun clearAll()
}

// ---------------------------------------------------------------------------------------------
// Settings, backup, connectivity
// ---------------------------------------------------------------------------------------------

interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun current(): Settings

    suspend fun edit(transform: (Settings) -> Settings)
}

interface BackupRepository {
    suspend fun export(target: Uri): Result<Int>

    suspend fun preview(source: Uri): Result<BackupPreview>

    suspend fun import(source: Uri, mode: ImportMode): Result<BackupReport>
}

data class BackupPreview(
    val repositories: Int,
    val extensions: Int,
    val series: Int,
    val categories: Int,
    val progress: Int,
    val history: Int,
    val hasSettings: Boolean,
)

data class BackupReport(
    val repositories: Int,
    val extensions: Int,
    val series: Int,
    val categories: Int,
    val progress: Int,
    val history: Int,
    val skipped: Int,
    val errors: List<String>,
)

enum class ImportMode { MERGE, REPLACE }

interface ConnectivityRepository {
    val online: Flow<Boolean>

    suspend fun isOnline(): Boolean
}
