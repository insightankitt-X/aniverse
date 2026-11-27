package app.aniverse.local.data.extensions

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import app.aniverse.local.data.network.RepoApi
import app.aniverse.local.data.network.RepoUrlResolver
import app.aniverse.local.domain.repository.InstallFailure
import app.aniverse.source.api.EXTENSION_API_VERSION
import app.aniverse.source.runtime.ExtensionFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads an extension APK and installs it through a PackageInstaller session. Nothing is ever
 * uninstalled to make room: a conflicting package produces a specific, readable error instead.
 */
@Singleton
class ExtensionInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: RepoApi,
    private val inspector: ApkInspector,
    private val registry: InstallSessionRegistry,
) {
    sealed interface DownloadOutcome {
        data class Ready(val file: File, val info: ApkInspector.ApkInfo) : DownloadOutcome

        data object Cancelled : DownloadOutcome

        data class Failed(
            val reason: InstallFailure,
            val message: String,
            /** True when the bytes were continued from an earlier partial download. */
            val resumed: Boolean = false,
        ) : DownloadOutcome
    }

    sealed interface InstallOutcomeInternal {
        data object Installed : InstallOutcomeInternal

        data object Cancelled : InstallOutcomeInternal

        data class Failed(val reason: InstallFailure, val message: String) : InstallOutcomeInternal
    }

    suspend fun download(
        apkUrl: String,
        pkg: String,
        onProgress: (Float) -> Unit,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val first = downloadOnce(apkUrl, pkg, onProgress, allowResume = true)
        // A file stitched together from an old partial download can fail APK inspection even
        // though the server is fine. Start over once with a clean file before blaming the APK.
        if (first is DownloadOutcome.Failed &&
            first.reason == InstallFailure.CORRUPT_APK &&
            first.resumed
        ) {
            downloadOnce(apkUrl, pkg, onProgress, allowResume = false)
        } else {
            first
        }
    }

    private suspend fun downloadOnce(
        apkUrl: String,
        pkg: String,
        onProgress: (Float) -> Unit,
        allowResume: Boolean,
    ): DownloadOutcome {
        val dir = File(context.cacheDir, "extensions").apply { mkdirs() }
        // Partial files are keyed by URL so an index that moved to a different APK layout can
        // never append new bytes to a file downloaded from another URL.
        val part = File(dir, "$pkg-${Integer.toHexString(apkUrl.hashCode())}.part")
        val target = File(dir, "$pkg.apk")
        val resumeFrom = if (allowResume && part.exists()) part.length() else 0L
        var effectiveResume = resumeFrom
        var resumed = resumeFrom > 0L

        try {
            var response = api.fetchApk(apkUrl, if (effectiveResume > 0L) "bytes=$effectiveResume-" else null)
            // Repositories disagree about where APKs live: the index usually lists a bare file
            // name while the file sits in an `apk/` folder (or the other way round). Retry the
            // other layout once instead of failing the install with a bare 404.
            val alternate = RepoUrlResolver.alternateFileLayout(apkUrl)
            if (!response.isSuccessful && response.code() in RETRYABLE_CODES && alternate != null) {
                runCatching { response.errorBody()?.close() }
                part.delete()
                effectiveResume = 0L
                resumed = false
                response = api.fetchApk(alternate, null)
            }
            if (!response.isSuccessful) {
                return DownloadOutcome.Failed(
                    InstallFailure.DOWNLOAD_FAILED,
                    "The extension download failed (HTTP ${response.code()})",
                    resumed,
                )
            }
            val body: ResponseBody = response.body()
                ?: return DownloadOutcome.Failed(InstallFailure.DOWNLOAD_FAILED, "The download was empty", resumed)

            val appending = response.code() == 206 && effectiveResume > 0L
            resumed = appending
            val alreadyThere = if (appending) effectiveResume else 0L
            body.use { stream ->
                FileOutputStream(part, appending).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = alreadyThere
                    val total = stream.contentLength().let { if (it > 0) it + alreadyThere else -1L }
                    var lastFraction = -1f
                    var lastEmitAt = 0L
                    stream.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            val fraction = if (total > 0) {
                                (written.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            // Updating UI state for every 8 KiB chunk makes the whole screen
                            // recompose hundreds of times a second and the app feel frozen.
                            val now = System.currentTimeMillis()
                            if (now - lastEmitAt >= PROGRESS_INTERVAL_MS && fraction != lastFraction) {
                                lastFraction = fraction
                                lastEmitAt = now
                                onProgress(fraction)
                            }
                        }
                    }
                }
            }
            if (part.length() <= 0L) {
                return DownloadOutcome.Failed(InstallFailure.CORRUPT_APK, "The downloaded file was empty", resumed)
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                return DownloadOutcome.Failed(InstallFailure.CORRUPT_APK, "The download could not be saved", resumed)
            }
            val info = inspector.inspect(target)
                ?: return DownloadOutcome.Failed(InstallFailure.CORRUPT_APK, "This file is not a valid APK", resumed)
            val format = inspector.formatOf(target)
            if (format is ExtensionFormat.Unsupported) {
                return DownloadOutcome.Failed(InstallFailure.INCOMPATIBLE_API, format.reason, resumed)
            }
            if (info.pkg != pkg) {
                return DownloadOutcome.Failed(
                    InstallFailure.PACKAGE_MISMATCH,
                    "The index says this file is $pkg but the APK is ${info.pkg}",
                    resumed,
                )
            }
            if (info.apiVersion != EXTENSION_API_VERSION) {
                return DownloadOutcome.Failed(
                    InstallFailure.INCOMPATIBLE_API,
                    "This extension targets API v${info.apiVersion}, but this app supports API v$EXTENSION_API_VERSION",
                    resumed,
                )
            }
            onProgress(1f)
            return DownloadOutcome.Ready(target, info)
        } catch (e: CancellationException) {
            part.delete()
            return DownloadOutcome.Cancelled
        } catch (e: IOException) {
            return DownloadOutcome.Failed(InstallFailure.DOWNLOAD_FAILED, "The download was interrupted. Try again.", resumed)
        } catch (e: Exception) {
            part.delete()
            return DownloadOutcome.Failed(InstallFailure.DOWNLOAD_FAILED, "The download failed. Try again.", resumed)
        }
    }

    suspend fun install(apk: File, pkg: String): InstallOutcomeInternal = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg)
            setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
        }
        val sessionId = try {
            installer.createSession(params)
        } catch (e: Exception) {
            return@withContext InstallOutcomeInternal.Failed(
                InstallFailure.INSTALLER_BLOCKED,
                "Android blocked the install. Allow AniVerse to install unknown apps in system settings.",
            )
        }

        val session = try {
            installer.openSession(sessionId)
        } catch (e: Exception) {
            return@withContext InstallOutcomeInternal.Failed(InstallFailure.UNKNOWN, "The install session could not be opened")
        }

        try {
            session.openWrite("base.apk", 0L, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }

            val deferred = registry.register(sessionId)
            val callback = Intent(context, InstallResultReceiver::class.java).apply {
                putExtra(InstallResultReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(InstallResultReceiver.EXTRA_PKG, pkg)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pendingIntent.intentSender)

            val signal = withTimeoutOrNull(INSTALL_TIMEOUT_MS) { deferred.await() }
            if (signal == null) {
                registry.forget(sessionId)
                return@withContext InstallOutcomeInternal.Failed(
                    InstallFailure.UNKNOWN,
                    "The installer did not report back. Check whether the extension was installed.",
                )
            }
            registry.forget(sessionId)
            mapSignal(signal)
        } catch (e: CancellationException) {
            runCatching { session.abandon() }
            throw e
        } catch (e: Exception) {
            runCatching { session.abandon() }
            InstallOutcomeInternal.Failed(InstallFailure.UNKNOWN, "The extension could not be installed")
        } finally {
            runCatching { session.close() }
        }
    }

    private fun mapSignal(signal: InstallSignal): InstallOutcomeInternal {
        val message = signal.message.orEmpty()
        val detail = message.substringAfterLast(':').trim().ifBlank { message }
        return when {
            signal.statusCode == PackageInstaller.STATUS_SUCCESS -> InstallOutcomeInternal.Installed
            signal.statusCode == PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcomeInternal.Cancelled
            message.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> InstallOutcomeInternal.Failed(
                InstallFailure.DOWNGRADE,
                "This would replace a newer installed version. Uninstall the newer copy first.",
            )
            message.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") ||
                message.contains("INSTALL_FAILED_DUPLICATE_PERMISSION") ||
                signal.statusCode == PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallOutcomeInternal.Failed(
                InstallFailure.SIGNATURE_MISMATCH,
                "An extension with this package name is already installed with a different signature.",
            )
            message.contains("INSTALL_FAILED_INVALID_APK") ||
                signal.statusCode == PackageInstaller.STATUS_FAILURE_INVALID -> InstallOutcomeInternal.Failed(
                InstallFailure.CORRUPT_APK,
                "Android rejected the APK as invalid or corrupt" + detailSuffix(detail),
            )
            signal.statusCode == PackageInstaller.STATUS_FAILURE_STORAGE ||
                message.contains("INSUFFICIENT_STORAGE") -> InstallOutcomeInternal.Failed(
                InstallFailure.UNKNOWN,
                "There is not enough free storage to install this extension.",
            )
            message.contains("INSTALL_FAILED_USER_RESTRICTED") -> InstallOutcomeInternal.Failed(
                InstallFailure.INSTALLER_BLOCKED,
                "Your device blocked the install. Allow installs from AniVerse in the system settings and try again.",
            )
            message.contains("INSTALL_FAILED_VERIFICATION_FAILURE") -> InstallOutcomeInternal.Failed(
                InstallFailure.INSTALLER_BLOCKED,
                "Your device's app verification blocked this extension. Allow it in the security settings and try again.",
            )
            signal.statusCode == PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallOutcomeInternal.Failed(
                InstallFailure.INSTALLER_BLOCKED,
                "Android blocked the install. Allow AniVerse to install unknown apps.",
            )
            signal.statusCode == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallOutcomeInternal.Failed(
                InstallFailure.INCOMPATIBLE_API,
                "This extension is not compatible with this device.",
            )
            else -> InstallOutcomeInternal.Failed(
                InstallFailure.UNKNOWN,
                "The install failed" + detailSuffix(detail).ifBlank { ". The installer reported no reason." },
            )
        }
    }

    private fun detailSuffix(detail: String): String =
        if (detail.isBlank()) "" else " ($detail)"

    private companion object {
        const val INSTALL_TIMEOUT_MS = 3 * 60 * 1000L
        const val PROGRESS_INTERVAL_MS = 120L
        val RETRYABLE_CODES = setOf(403, 404, 410)
    }
}
