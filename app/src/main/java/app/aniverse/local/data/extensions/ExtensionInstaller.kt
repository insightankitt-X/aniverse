package app.aniverse.local.data.extensions

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import app.aniverse.local.data.network.RepoApi
import app.aniverse.local.domain.repository.InstallFailure
import app.aniverse.source.api.EXTENSION_API_VERSION
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ResponseBody
import java.io.File
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

        data class Failed(val reason: InstallFailure, val message: String) : DownloadOutcome
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
        val dir = File(context.cacheDir, "extensions").apply { mkdirs() }
        val part = File(dir, "$pkg.part")
        val target = File(dir, "$pkg.apk")
        val resumeFrom = if (part.exists()) part.length() else 0L

        try {
            val response = api.fetchApk(apkUrl, if (resumeFrom > 0L) "bytes=$resumeFrom-" else null)
            if (!response.isSuccessful) {
                return@withContext DownloadOutcome.Failed(
                    InstallFailure.DOWNLOAD_FAILED,
                    "The extension download failed (HTTP ${response.code()})",
                )
            }
            val body: ResponseBody = response.body()
                ?: return@withContext DownloadOutcome.Failed(InstallFailure.DOWNLOAD_FAILED, "The download was empty")

            val appending = response.code() == 206 && resumeFrom > 0L
            val alreadyThere = if (appending) resumeFrom else 0L
            body.use { stream ->
                part.outputStream(append = appending).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = alreadyThere
                    val total = stream.contentLength().let { if (it > 0) it + alreadyThere else -1L }
                    stream.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(if (total > 0) (written.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f)
                        }
                    }
                }
            }
            if (part.length() <= 0L) {
                return@withContext DownloadOutcome.Failed(InstallFailure.CORRUPT_APK, "The downloaded file was empty")
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                return@withContext DownloadOutcome.Failed(InstallFailure.CORRUPT_APK, "The download could not be saved")
            }
            val info = inspector.inspect(target)
                ?: return@withContext DownloadOutcome.Failed(InstallFailure.CORRUPT_APK, "This file is not a valid APK")
            if (info.pkg != pkg) {
                return@withContext DownloadOutcome.Failed(
                    InstallFailure.PACKAGE_MISMATCH,
                    "The index says this file is $pkg but the APK is ${info.pkg}",
                )
            }
            if (info.apiVersion != EXTENSION_API_VERSION) {
                return@withContext DownloadOutcome.Failed(
                    InstallFailure.INCOMPATIBLE_API,
                    "This extension targets API v${info.apiVersion}, but this app supports API v$EXTENSION_API_VERSION",
                )
            }
            onProgress(1f)
            DownloadOutcome.Ready(target, info)
        } catch (e: CancellationException) {
            part.delete()
            DownloadOutcome.Cancelled
        } catch (e: IOException) {
            DownloadOutcome.Failed(InstallFailure.DOWNLOAD_FAILED, "The download was interrupted. Try again.")
        } catch (e: Exception) {
            part.delete()
            DownloadOutcome.Failed(InstallFailure.DOWNLOAD_FAILED, "The download failed. Try again.")
        }
    }

    suspend fun install(apk: File, pkg: String): InstallOutcomeInternal = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg)
            setInstallLocation(PackageInstaller.SessionParams.INSTALL_LOCATION_AUTO)
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
                ?: return@withContext InstallOutcomeInternal.Failed(
                    InstallFailure.UNKNOWN,
                    "The installer did not report back. Check whether the extension was installed.",
                )
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
        return when {
            signal.statusCode == PackageInstaller.STATUS_SUCCESS -> InstallOutcomeInternal.Installed
            signal.statusCode == PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcomeInternal.Cancelled
            message.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> InstallOutcomeInternal.Failed(
                InstallFailure.DOWNGRADE,
                "This would replace a newer installed version. Uninstall the newer copy first.",
            )
            message.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") ||
                signal.statusCode == PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallOutcomeInternal.Failed(
                InstallFailure.SIGNATURE_MISMATCH,
                "An extension with this package name is already installed with a different signature.",
            )
            message.contains("INSTALL_FAILED_INVALID_APK") ||
                signal.statusCode == PackageInstaller.STATUS_FAILURE_INVALID -> InstallOutcomeInternal.Failed(
                InstallFailure.CORRUPT_APK,
                "Android rejected the APK as invalid or corrupt.",
            )
            signal.statusCode == PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallOutcomeInternal.Failed(
                InstallFailure.INSTALLER_BLOCKED,
                "Android blocked the install. Allow AniVerse to install unknown apps.",
            )
            else -> InstallOutcomeInternal.Failed(
                InstallFailure.UNKNOWN,
                "The install failed. Nothing was changed on your device.",
            )
        }
    }

    private companion object {
        const val INSTALL_TIMEOUT_MS = 3 * 60 * 1000L
    }
}
