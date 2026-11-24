package app.aniverse.local.data

import app.aniverse.local.data.db.DownloadEntity
import app.aniverse.local.data.db.EpisodeEntity
import app.aniverse.local.data.db.ExtensionEntity
import app.aniverse.local.data.db.ProgressEntity
import app.aniverse.local.data.db.RepoEntity
import app.aniverse.local.data.db.SeriesEntity
import app.aniverse.local.data.network.ParsedExtension
import app.aniverse.local.domain.VersionComparator
import app.aniverse.local.domain.model.DownloadState
import app.aniverse.local.domain.model.Episode
import app.aniverse.local.domain.model.InstalledExtension
import app.aniverse.local.domain.model.PlaybackProgress
import app.aniverse.local.domain.model.RepoExtension
import app.aniverse.local.domain.model.RepoSummary
import app.aniverse.local.domain.model.Series
import app.aniverse.source.api.RemoteEpisode
import app.aniverse.source.api.RemoteSeries
import app.aniverse.source.api.SeriesStatus as RemoteStatus
import app.aniverse.local.domain.model.SeriesStatus as DomainStatus

fun RemoteStatus.toDomain(): DomainStatus = when (this) {
    RemoteStatus.ONGOING -> DomainStatus.ONGOING
    RemoteStatus.COMPLETED -> DomainStatus.COMPLETED
    RemoteStatus.LICENSED -> DomainStatus.LICENSED
    RemoteStatus.PUBLISHING_FINISHED -> DomainStatus.PUBLISHING_FINISHED
    RemoteStatus.CANCELLED -> DomainStatus.CANCELLED
    RemoteStatus.ON_HIATUS -> DomainStatus.ON_HIATUS
    RemoteStatus.UNKNOWN -> DomainStatus.UNKNOWN
}

fun String.toStatus(): DomainStatus = runCatching { DomainStatus.valueOf(this) }.getOrDefault(DomainStatus.UNKNOWN)

fun RemoteSeries.toDomain(sourcePkg: String, sourceId: Long): Series = Series(
    sourcePkg = sourcePkg,
    sourceId = sourceId,
    remoteId = remoteId,
    title = title,
    url = url,
    thumbnailUrl = thumbnailUrl,
    description = description,
    genres = genres,
    status = status.toDomain(),
    author = author,
    artist = artist,
)

fun RemoteEpisode.toDomain(
    seriesId: Long,
    localId: Long = 0L,
    watched: Boolean = false,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    downloadState: DownloadState? = null,
    downloadedPath: String? = null,
): Episode = Episode(
    id = localId,
    seriesId = seriesId,
    remoteId = remoteId,
    name = name,
    url = url,
    number = episodeNumber,
    filler = filler,
    uploadDate = uploadDate,
    watched = watched,
    positionMs = positionMs,
    durationMs = durationMs,
    downloadState = downloadState,
    downloadedPath = downloadedPath,
)

fun SeriesEntity.toDomain(): Series = Series(
    id = id,
    sourcePkg = sourcePkg,
    sourceId = sourceId,
    remoteId = remoteId,
    title = title,
    url = url,
    thumbnailUrl = thumbnailUrl,
    description = description,
    genres = genres,
    status = status.toStatus(),
    author = author,
    artist = artist,
    inLibrary = inLibrary,
    lastUpdatedAt = lastUpdatedAt,
)

fun Series.toEntity(existing: SeriesEntity? = null): SeriesEntity = SeriesEntity(
    id = existing?.id ?: id,
    sourcePkg = sourcePkg,
    sourceId = sourceId,
    remoteId = remoteId,
    title = title,
    url = url,
    thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
    description = description ?: existing?.description,
    genres = if (genres.isEmpty()) existing?.genres.orEmpty() else genres,
    status = status.name,
    author = author ?: existing?.author,
    artist = artist ?: existing?.artist,
    inLibrary = existing?.inLibrary ?: inLibrary,
    lastUpdatedAt = lastUpdatedAt,
    lastFetchedAt = existing?.lastFetchedAt ?: 0L,
    addedToLibraryAt = existing?.addedToLibraryAt ?: 0L,
)

fun RemoteEpisode.toEntity(seriesId: Long, existing: EpisodeEntity? = null): EpisodeEntity = EpisodeEntity(
    id = existing?.id ?: 0L,
    seriesId = seriesId,
    remoteId = remoteId,
    name = name,
    url = url,
    number = episodeNumber,
    filler = filler,
    uploadDate = uploadDate,
    watched = existing?.watched ?: false,
    watchedAt = existing?.watchedAt ?: 0L,
)

fun EpisodeEntity.toDomain(
    progress: ProgressEntity? = null,
    download: DownloadEntity? = null,
): Episode = Episode(
    id = id,
    seriesId = seriesId,
    remoteId = remoteId,
    name = name,
    url = url,
    number = number,
    filler = filler,
    uploadDate = uploadDate,
    watched = watched,
    positionMs = progress?.positionMs ?: 0L,
    durationMs = progress?.durationMs ?: 0L,
    downloadState = download?.let { DownloadState.valueOf(it.state) },
    downloadedPath = download?.filePath,
)

fun ProgressEntity.toDomain(): PlaybackProgress = PlaybackProgress(
    episodeId = episodeId,
    seriesId = seriesId,
    positionMs = positionMs,
    durationMs = durationMs,
    updatedAt = updatedAt,
)

fun RepoEntity.toDomain(): RepoSummary = RepoSummary(
    id = id,
    name = name,
    url = url,
    indexUrl = indexUrl,
    pinned = pinned,
    trusted = trusted,
    extensionCount = extensionCount,
    lastUpdatedAt = lastUpdatedAt,
    lastError = lastError,
)

fun ParsedExtension.toEntity(repoId: Long?, existing: ExtensionEntity? = null, now: Long = System.currentTimeMillis()): ExtensionEntity =
    ExtensionEntity(
        pkg = pkg,
        name = name,
        versionName = versionName,
        versionCode = versionCode,
        lang = lang,
        nsfw = nsfw,
        iconUrl = iconUrl ?: existing?.iconUrl,
        description = description ?: existing?.description,
        apkUrl = apkUrl,
        repoId = repoId ?: existing?.repoId,
        installed = existing?.installed ?: false,
        enabled = existing?.enabled ?: false,
        installedVersionCode = existing?.installedVersionCode ?: 0,
        installedVersionName = existing?.installedVersionName.orEmpty(),
        installedAt = existing?.installedAt ?: 0L,
        failureCount = existing?.failureCount ?: 0,
        disabledReason = existing?.disabledReason,
        lastSeenAt = now,
    )

fun ExtensionEntity.toDomain(repoName: String?, trusted: Boolean): InstalledExtension {
    val hasUpdate = installed && VersionComparator.isUpdate(
        newVersionCode = versionCode,
        newVersionName = versionName,
        installedVersionCode = installedVersionCode,
        installedVersionName = installedVersionName,
    )
    return InstalledExtension(
        pkg = pkg,
        name = name,
        versionName = if (installed) installedVersionName else versionName,
        versionCode = if (installed) installedVersionCode else versionCode,
        lang = lang,
        nsfw = nsfw,
        iconUrl = iconUrl,
        repoId = repoId,
        repoName = repoName,
        trusted = trusted,
        enabled = enabled,
        failureCount = failureCount,
        disabledReason = disabledReason,
        installedAt = installedAt,
        hasUpdate = hasUpdate,
        latestVersionName = versionName,
        latestVersionCode = versionCode,
    )
}

fun ExtensionEntity.toAvailable(): RepoExtension = RepoExtension(
    pkg = pkg,
    name = name,
    versionName = versionName,
    versionCode = versionCode,
    lang = lang,
    nsfw = nsfw,
    iconUrl = iconUrl,
    description = description,
    apkUrl = apkUrl,
)
