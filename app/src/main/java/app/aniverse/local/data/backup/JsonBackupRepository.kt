package app.aniverse.local.data.backup

import android.content.Context
import android.net.Uri
import app.aniverse.local.data.db.AniVerseDatabase
import app.aniverse.local.data.db.CategoryDao
import app.aniverse.local.data.db.CategoryEntity
import app.aniverse.local.data.db.EpisodeDao
import app.aniverse.local.data.db.EpisodeEntity
import app.aniverse.local.data.db.ExtensionDao
import app.aniverse.local.data.db.ExtensionEntity
import app.aniverse.local.data.db.HistoryDao
import app.aniverse.local.data.db.HistoryEntity
import app.aniverse.local.data.db.ProgressDao
import app.aniverse.local.data.db.ProgressEntity
import app.aniverse.local.data.db.RepoDao
import app.aniverse.local.data.db.RepoEntity
import app.aniverse.local.data.db.SeriesCategoryCrossRef
import app.aniverse.local.data.db.SeriesDao
import app.aniverse.local.data.db.SeriesEntity
import app.aniverse.local.domain.model.PlayerQualityMode
import app.aniverse.local.domain.model.Settings
import app.aniverse.local.domain.model.SubtitleSize
import app.aniverse.local.domain.model.ThemeMode
import app.aniverse.local.domain.repository.BackupPreview
import app.aniverse.local.domain.repository.BackupReport
import app.aniverse.local.domain.repository.BackupRepository
import app.aniverse.local.domain.repository.ImportMode
import app.aniverse.local.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup and restore through the Storage Access Framework. Import validates the file first, shows a
 * dry-run count and can merge into or replace the local database. A single bad row is reported as a
 * partial failure instead of aborting the whole import.
 */
@Singleton
class JsonBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AniVerseDatabase,
    private val repoDao: RepoDao,
    private val extensionDao: ExtensionDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val progressDao: ProgressDao,
    private val historyDao: HistoryDao,
    private val categoryDao: CategoryDao,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : BackupRepository {

    override suspend fun export(target: Uri): Result<Int> = runCatching {
        val text = json.encodeToString(BackupFile.serializer(), buildBackup())
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("The selected file could not be written")
        }
        text.length
    }

    override suspend fun preview(source: Uri): Result<BackupPreview> = runCatching {
        val backup = read(source)
        BackupPreview(
            repositories = backup.repositories.size,
            extensions = backup.extensions.size,
            series = backup.series.size,
            categories = backup.categories.size,
            progress = backup.progress.size,
            history = backup.history.size,
            hasSettings = backup.settings != null,
        )
    }

    override suspend fun import(source: Uri, mode: ImportMode): Result<BackupReport> = runCatching {
        val backup = read(source)
        val errors = mutableListOf<String>()
        var skipped = 0

        if (mode == ImportMode.REPLACE) {
            withContext(Dispatchers.IO) { database.clearAllTables() }
        }

        val repoIds = mutableMapOf<String, Long>()
        backup.repositories.forEach { repo ->
            val existing = repoDao.byIndexUrl(repo.indexUrl)
            val id = existing?.id ?: repoDao.insert(
                RepoEntity(
                    name = repo.name,
                    url = repo.url,
                    indexUrl = repo.indexUrl,
                    pinned = repo.pinned,
                    trusted = repo.trusted,
                    extensionCount = 0,
                    lastUpdatedAt = 0L,
                ),
            )
            repoDao.setPinned(id, repo.pinned)
            repoDao.setTrusted(id, repo.trusted)
            repoIds[repo.indexUrl] = id
        }

        var extensionCount = 0
        backup.extensions.forEach { extension ->
            runCatching {
                val existing = extensionDao.byPkg(extension.pkg)
                extensionDao.upsert(
                    ExtensionEntity(
                        pkg = extension.pkg,
                        name = extension.name,
                        versionName = extension.versionName,
                        versionCode = extension.versionCode,
                        lang = extension.lang,
                        nsfw = extension.nsfw,
                        iconUrl = extension.iconUrl,
                        description = extension.description,
                        apkUrl = extension.apkUrl,
                        repoId = extension.repoIndexUrl?.let { repoIds[it] } ?: existing?.repoId,
                        installed = extension.installed,
                        enabled = extension.enabled,
                        installedVersionCode = extension.installedVersionCode,
                        installedVersionName = extension.installedVersionName,
                        installedAt = extension.installedAt,
                        failureCount = 0,
                        disabledReason = null,
                        lastSeenAt = System.currentTimeMillis(),
                    ),
                )
                extensionCount++
            }.onFailure { errors += "Extension ${extension.pkg}: ${it.message ?: "skipped"}" }
        }

        val categoryIds = mutableMapOf<String, Long>()
        backup.categories.forEach { category ->
            val existing = categoryDao.all().firstOrNull { it.name.equals(category.name, ignoreCase = true) }
            val id = existing?.id ?: categoryDao.insert(CategoryEntity(name = category.name, sortOrder = category.sortOrder))
            if (id > 0L) categoryIds[category.name] = id else skipped++
        }

        val localSeries = mutableMapOf<SeriesRef, Long>()
        backup.series.forEach { entry ->
            val existing = seriesDao.find(entry.ref.sourcePkg, entry.ref.sourceId, entry.ref.remoteId)
            val rowId = seriesDao.insert(
                SeriesEntity(
                    id = existing?.id ?: 0L,
                    sourcePkg = entry.ref.sourcePkg,
                    sourceId = entry.ref.sourceId,
                    remoteId = entry.ref.remoteId,
                    title = entry.title,
                    url = entry.url,
                    thumbnailUrl = entry.thumbnailUrl,
                    description = entry.description,
                    genres = entry.genres,
                    status = entry.status,
                    author = entry.author,
                    artist = entry.artist,
                    inLibrary = entry.inLibrary,
                    lastUpdatedAt = entry.lastUpdatedAt,
                    addedToLibraryAt = entry.addedToLibraryAt,
                ),
            )
            val resolved = if (rowId > 0L) rowId else existing?.id
            if (resolved == null) skipped++ else localSeries[entry.ref] = resolved
        }

        backup.categories.forEach { category ->
            val categoryId = categoryIds[category.name] ?: return@forEach
            val links = category.series.mapNotNull { ref -> localSeries[ref] }
                .map { seriesId -> SeriesCategoryCrossRef(seriesId, categoryId) }
            if (links.isNotEmpty()) categoryDao.link(links)
        }

        val localEpisodes = mutableMapOf<Pair<SeriesRef, String>, Long>()
        backup.episodes.forEach { entry ->
            val seriesId = localSeries[entry.series]
            if (seriesId == null) {
                skipped++
                return@forEach
            }
            val existing = episodeDao.find(seriesId, entry.remoteId)
            val rowId = episodeDao.insert(
                EpisodeEntity(
                    id = existing?.id ?: 0L,
                    seriesId = seriesId,
                    remoteId = entry.remoteId,
                    name = entry.name,
                    url = entry.url,
                    number = entry.number,
                    filler = entry.filler,
                    uploadDate = entry.uploadDate,
                    watched = entry.watched,
                    watchedAt = entry.watchedAt,
                ),
            )
            val resolved = if (rowId > 0L) rowId else existing?.id
            if (resolved == null) skipped++ else localEpisodes[entry.series to entry.remoteId] = resolved
        }

        var progressCount = 0
        backup.progress.forEach { entry ->
            val seriesId = localSeries[entry.series]
            val episodeId = localEpisodes[entry.series to entry.episodeRemoteId]
            if (seriesId == null || episodeId == null) {
                skipped++
                return@forEach
            }
            progressDao.upsert(
                ProgressEntity(
                    episodeId = episodeId,
                    seriesId = seriesId,
                    positionMs = entry.positionMs,
                    durationMs = entry.durationMs,
                    updatedAt = entry.updatedAt,
                ),
            )
            progressCount++
        }

        var historyCount = 0
        backup.history.forEach { entry ->
            val seriesId = localSeries[entry.series]
            val episodeId = localEpisodes[entry.series to entry.episodeRemoteId]
            if (seriesId == null || episodeId == null) {
                skipped++
                return@forEach
            }
            historyDao.upsert(HistoryEntity(episodeId = episodeId, seriesId = seriesId, watchedAt = entry.watchedAt))
            historyCount++
        }

        backup.settings?.let { settings ->
            settingsRepository.edit { current -> settings.applyTo(current) }
        }

        BackupReport(
            repositories = repoIds.size,
            extensions = extensionCount,
            series = localSeries.size,
            categories = categoryIds.size,
            progress = progressCount,
            history = historyCount,
            skipped = skipped,
            errors = errors,
        )
    }

    private suspend fun read(source: Uri): BackupFile {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
        } ?: error("The selected file could not be read")
        val backup = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("This file is not an AniVerse backup") }
        require(backup.version <= BackupFile.CURRENT_VERSION) {
            "This backup was created by a newer version of AniVerse"
        }
        return backup
    }

    private suspend fun buildBackup(): BackupFile {
        val repos = repoDao.all()
        val extensions = extensionDao.installed()
        val series = seriesDao.librarySeries()
        val episodes = series.flatMap { row -> episodeDao.forSeries(row.id) }
        val categories = categoryDao.all()
        val links = categoryDao.allLinks()
        val settings = settingsRepository.current()

        val seriesRefs = series.associate { it.id to SeriesRef(it.sourcePkg, it.sourceId, it.remoteId) }
        val episodeRefs = episodes.mapNotNull { episode ->
            val ref = seriesRefs[episode.seriesId] ?: return@mapNotNull null
            episode.id to (ref to episode.remoteId)
        }.toMap()
        val linksByCategory = links.groupBy({ it.categoryId }, { it.seriesId })
        val reposById = repos.associateBy { it.id }

        return BackupFile(
            version = BackupFile.CURRENT_VERSION,
            createdAt = System.currentTimeMillis(),
            repositories = repos.map { repo ->
                RepoBackup(repo.name, repo.url, repo.indexUrl, repo.pinned, repo.trusted)
            },
            extensions = extensions.map { extension ->
                ExtensionBackup(
                    pkg = extension.pkg,
                    name = extension.name,
                    versionName = extension.versionName,
                    versionCode = extension.versionCode,
                    lang = extension.lang,
                    nsfw = extension.nsfw,
                    iconUrl = extension.iconUrl,
                    description = extension.description,
                    apkUrl = extension.apkUrl,
                    repoIndexUrl = extension.repoId?.let { reposById[it]?.indexUrl },
                    installed = extension.installed,
                    enabled = extension.enabled,
                    installedVersionCode = extension.installedVersionCode,
                    installedVersionName = extension.installedVersionName,
                    installedAt = extension.installedAt,
                )
            },
            series = series.map { row ->
                SeriesBackup(
                    ref = seriesRefs.getValue(row.id),
                    title = row.title,
                    url = row.url,
                    thumbnailUrl = row.thumbnailUrl,
                    description = row.description,
                    genres = row.genres,
                    status = row.status,
                    author = row.author,
                    artist = row.artist,
                    inLibrary = row.inLibrary,
                    addedToLibraryAt = row.addedToLibraryAt,
                    lastUpdatedAt = row.lastUpdatedAt,
                )
            },
            episodes = episodes.mapNotNull { episode ->
                val ref = seriesRefs[episode.seriesId] ?: return@mapNotNull null
                EpisodeBackup(
                    series = ref,
                    remoteId = episode.remoteId,
                    name = episode.name,
                    url = episode.url,
                    number = episode.number,
                    filler = episode.filler,
                    uploadDate = episode.uploadDate,
                    watched = episode.watched,
                    watchedAt = episode.watchedAt,
                )
            },
            categories = categories.map { category ->
                CategoryBackup(
                    name = category.name,
                    sortOrder = category.sortOrder,
                    series = linksByCategory[category.id].orEmpty().mapNotNull { seriesRefs[it] },
                )
            },
            progress = progressDao.all().mapNotNull { progress ->
                val ref = episodeRefs[progress.episodeId] ?: return@mapNotNull null
                ProgressBackup(ref.first, ref.second, progress.positionMs, progress.durationMs, progress.updatedAt)
            },
            history = historyDao.all().mapNotNull { entry ->
                val ref = episodeRefs[entry.episodeId] ?: return@mapNotNull null
                HistoryBackup(ref.first, ref.second, entry.watchedAt)
            },
            settings = SettingsBackup(
                themeMode = settings.themeMode.name,
                pureBlack = settings.pureBlack,
                dynamicColor = settings.dynamicColor,
                reduceMotion = settings.reduceMotion,
                language = settings.language,
                updateIntervalHours = settings.updateIntervalHours,
                wifiOnlyUpdates = settings.wifiOnlyUpdates,
                playerQualityMode = settings.playerQualityMode.name,
                playerPreferredQuality = settings.playerPreferredQuality,
                playerSpeed = settings.playerSpeed,
                subtitleSize = settings.subtitleSize.name,
                subtitleBackground = settings.subtitleBackground,
                downloadTreeUri = settings.downloadTreeUri,
                downloadParallelLimit = settings.downloadParallelLimit,
                downloadWifiOnly = settings.downloadWifiOnly,
                showNsfw = settings.showNsfw,
            ),
        )
    }

    private fun SettingsBackup.applyTo(current: Settings): Settings = Settings(
        themeMode = enumOr(themeMode, current.themeMode),
        pureBlack = pureBlack,
        dynamicColor = dynamicColor,
        reduceMotion = reduceMotion,
        language = language,
        updateIntervalHours = updateIntervalHours.coerceIn(1, 24),
        wifiOnlyUpdates = wifiOnlyUpdates,
        playerQualityMode = enumOr(playerQualityMode, current.playerQualityMode),
        playerPreferredQuality = playerPreferredQuality,
        playerSpeed = playerSpeed.coerceIn(0.25f, 2f),
        subtitleSize = enumOr(subtitleSize, current.subtitleSize),
        subtitleBackground = subtitleBackground,
        downloadTreeUri = downloadTreeUri,
        downloadParallelLimit = downloadParallelLimit.coerceIn(1, 4),
        downloadWifiOnly = downloadWifiOnly,
        showNsfw = showNsfw,
        dismissedCleartextWarning = current.dismissedCleartextWarning,
        searchHistory = current.searchHistory,
    )

    private inline fun <reified T : Enum<T>> enumOr(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
