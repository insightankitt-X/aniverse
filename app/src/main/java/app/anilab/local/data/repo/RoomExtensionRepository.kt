package app.anilab.local.data.repo

import app.anilab.local.data.db.ExtensionDao
import app.anilab.local.data.db.RepoDao
import app.anilab.local.data.extensions.ApkInspector
import app.anilab.local.data.extensions.ExtensionInstaller
import app.anilab.local.data.source.SourceRegistry
import app.anilab.local.data.toAvailable
import app.anilab.local.data.toDomain
import app.anilab.local.domain.VersionComparator
import app.anilab.local.domain.model.InstalledExtension
import app.anilab.local.domain.model.RepoExtensions
import app.anilab.local.domain.repository.ConfirmationReason
import app.anilab.local.domain.repository.ExtensionRepository
import app.anilab.local.domain.repository.InstallFailure
import app.anilab.local.domain.repository.InstallOutcome
import app.anilab.local.domain.repository.UpdateFailureItem
import app.anilab.local.domain.repository.UpdateProgress
import app.anilab.local.domain.repository.UpdateReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomExtensionRepository @Inject constructor(
    private val extensionDao: ExtensionDao,
    private val repoDao: RepoDao,
    private val installer: ExtensionInstaller,
    private val inspector: ApkInspector,
    private val registry: SourceRegistry,
) : ExtensionRepository {

    @Volatile
    private var activeInstall: Job? = null

    override fun observeInstalled(): Flow<List<InstalledExtension>> = combine(
        extensionDao.observeInstalled(),
        repoDao.observeAll(),
    ) { installed, repos ->
        val reposById = repos.associateBy { it.id }
        installed.map { extension ->
            val repo = extension.repoId?.let { reposById[it] }
            extension.toDomain(repoName = repo?.name, trusted = repo?.trusted == true)
        }
    }

    override fun observeAvailable(): Flow<List<RepoExtensions>> = combine(
        extensionDao.observeAvailable(),
        repoDao.observeAll(),
    ) { available, repos ->
        val byRepo = available.groupBy { it.repoId }
        repos.mapNotNull { repo ->
            val entries = byRepo[repo.id]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            RepoExtensions(
                repoId = repo.id,
                repoName = repo.name,
                trusted = repo.trusted,
                pinned = repo.pinned,
                updateCount = entries.count { entry ->
                    entry.installed && VersionComparator.isUpdate(
                        entry.versionCode,
                        entry.versionName,
                        entry.installedVersionCode,
                        entry.installedVersionName,
                    )
                },
                extensions = entries.map { it.toAvailable() },
            )
        }
    }

    override fun observeUpdateCount(): Flow<Int> = extensionDao.observeUpdateCount()

    override suspend fun install(
        pkg: String,
        repoId: Long?,
        confirmed: Boolean,
        onProgress: (Float) -> Unit,
    ): InstallOutcome = coroutineScope {
        val tracked = coroutineContext[Job]
        activeInstall = tracked
        try {
            installInternal(pkg, repoId, confirmed, onProgress)
        } catch (e: CancellationException) {
            InstallOutcome.Cancelled
        } finally {
            if (activeInstall === tracked) activeInstall = null
        }
    }

    private suspend fun installInternal(
        pkg: String,
        repoId: Long?,
        confirmed: Boolean,
        onProgress: (Float) -> Unit,
    ): InstallOutcome {
        val extension = extensionDao.byPkg(pkg)
            ?: return InstallOutcome.Failed(InstallFailure.NOT_FOUND, "This extension is no longer listed in any repository")
        val owningRepoId = repoId ?: extension.repoId
        val repo = owningRepoId?.let { repoDao.byId(it) }
        val trusted = repo?.trusted == true

        val download = installer.download(extension.apkUrl, pkg, onProgress)
        val ready = when (download) {
            is ExtensionInstaller.DownloadOutcome.Cancelled -> return InstallOutcome.Cancelled
            is ExtensionInstaller.DownloadOutcome.Failed -> return InstallOutcome.Failed(download.reason, download.message)
            is ExtensionInstaller.DownloadOutcome.Ready -> download
        }

        val installedSignature = if (inspector.isInstalled(pkg)) inspector.installedSignature(pkg) else null
        if (installedSignature != null && ready.info.signature != null && installedSignature != ready.info.signature) {
            cleanUp(ready.file)
            return InstallOutcome.Failed(
                InstallFailure.SIGNATURE_MISMATCH,
                "A different copy of $pkg is already installed. Uninstall it first, then install this one.",
            )
        }
        val installedCode = inspector.installedVersionCode(pkg)
        if (installedCode > 0L && ready.info.versionCode < installedCode) {
            cleanUp(ready.file)
            return InstallOutcome.Failed(
                InstallFailure.DOWNGRADE,
                "The installed version is newer than this one. Uninstall it first if you really want to downgrade.",
            )
        }

        val unusual = inspector.unusualPermissions(ready.info.permissions)
        val reasons = buildList {
            if (!trusted) add(ConfirmationReason.UNTRUSTED_REPOSITORY)
            if (unusual.isNotEmpty()) add(ConfirmationReason.UNUSUAL_PERMISSIONS)
        }
        if (reasons.isNotEmpty() && !confirmed) {
            cleanUp(ready.file)
            return InstallOutcome.NeedsConfirmation(pkg, reasons, unusual)
        }

        return when (val result = installer.install(ready.file, pkg)) {
            is ExtensionInstaller.InstallOutcomeInternal.Installed -> {
                extensionDao.markInstalled(
                    pkg = pkg,
                    code = ready.info.versionCode.toInt(),
                    name = ready.info.versionName,
                    at = System.currentTimeMillis(),
                    enabled = true,
                )
                registry.invalidate(pkg)
                cleanUp(ready.file)
                InstallOutcome.Installed(pkg)
            }

            is ExtensionInstaller.InstallOutcomeInternal.Cancelled -> {
                cleanUp(ready.file)
                InstallOutcome.Cancelled
            }

            is ExtensionInstaller.InstallOutcomeInternal.Failed -> {
                cleanUp(ready.file)
                InstallOutcome.Failed(result.reason, result.message)
            }
        }
    }

    override suspend fun update(pkg: String, confirmed: Boolean, onProgress: (Float) -> Unit): InstallOutcome {
        val extension = extensionDao.byPkg(pkg)
            ?: return InstallOutcome.Failed(InstallFailure.NOT_FOUND, "This extension is no longer listed in any repository")
        if (!VersionComparator.isUpdate(extension.versionCode, extension.versionName, extension.installedVersionCode, extension.installedVersionName)) {
            return InstallOutcome.Failed(InstallFailure.NOT_FOUND, "This extension is already up to date")
        }
        return install(pkg, extension.repoId, confirmed, onProgress)
    }

    override suspend fun updateAll(onProgress: (UpdateProgress) -> Unit): UpdateReport {
        val trustedUpdates = extensionDao.updatableFromTrustedRepos()
        val untrustedUpdates = extensionDao.updatable().filter { extension ->
            trustedUpdates.none { it.pkg == extension.pkg }
        }
        val skipped = untrustedUpdates.map { extension ->
            UpdateFailureItem(extension.pkg, extension.name, "Repository is not trusted, so it is updated manually")
        }
        val updated = mutableListOf<String>()
        val failed = mutableListOf<UpdateFailureItem>()
        trustedUpdates.forEachIndexed { index, extension ->
            onProgress(UpdateProgress(extension.pkg, extension.name, index, trustedUpdates.size, 0f))
            when (val outcome = install(extension.pkg, extension.repoId, confirmed = false) { fraction ->
                onProgress(UpdateProgress(extension.pkg, extension.name, index, trustedUpdates.size, fraction))
            }) {
                is InstallOutcome.Installed -> updated += extension.name
                is InstallOutcome.Failed -> failed += UpdateFailureItem(extension.pkg, extension.name, outcome.message)
                is InstallOutcome.NeedsConfirmation ->
                    skipped += UpdateFailureItem(extension.pkg, extension.name, "Needs your confirmation before updating")
                is InstallOutcome.Cancelled -> failed += UpdateFailureItem(extension.pkg, extension.name, "Cancelled")
            }
        }
        onProgress(UpdateProgress("", "", trustedUpdates.size, trustedUpdates.size, 1f))
        return UpdateReport(updated = updated, failed = failed, skipped = skipped)
    }

    override suspend fun cancelInstall() {
        activeInstall?.cancel()
    }

    /**
     * The app cannot silently remove another package. The UI opens the system uninstall dialog; this
     * only forgets the extension locally so its sources stop being offered.
     */
    override suspend fun uninstall(pkg: String) {
        extensionDao.markUninstalled(pkg)
        registry.invalidate(pkg)
    }

    override suspend fun setEnabled(pkg: String, enabled: Boolean) {
        extensionDao.setEnabled(pkg, enabled)
        registry.invalidate(pkg)
    }

    override suspend fun setNsfw(pkg: String, nsfw: Boolean) = extensionDao.setNsfw(pkg, nsfw)

    override suspend fun reportFailure(pkg: String, message: String) {
        val extension = extensionDao.byPkg(pkg) ?: return
        if (extension.failureCount + 1 >= MAX_FAILURES) {
            extensionDao.disable(pkg, "Disabled after repeated failures: $message")
        } else {
            extensionDao.recordFailure(pkg, message)
        }
    }

    override suspend fun clearFailures(pkg: String) = extensionDao.clearFailures(pkg)

    private fun cleanUp(file: File) {
        runCatching { file.delete() }
    }

    private companion object {
        const val MAX_FAILURES = 5
    }
}
