package app.anilab.local.data.extensions

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.anilab.source.api.EXTENSION_API_VERSION
import app.anilab.source.api.EXTENSION_META_PATH
import app.anilab.source.runtime.ExtensionMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/** Reads metadata out of an APK file without installing it, so nothing is offered blindly. */
@Singleton
class ApkInspector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    data class ApkInfo(
        val pkg: String,
        val versionName: String,
        val versionCode: Long,
        val signature: String?,
        val permissions: List<String>,
        val apiVersion: Int,
    )

    fun inspect(file: File): ApkInfo? {
        val flags = PackageManager.GET_PERMISSIONS or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val info = runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        }.getOrNull() ?: return null

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return ApkInfo(
            pkg = info.packageName ?: return null,
            versionName = info.versionName ?: "0.0.0",
            versionCode = versionCode,
            signature = signatureOf(info),
            permissions = info.requestedPermissions?.toList().orEmpty(),
            apiVersion = apiVersionIn(file),
        )
    }

    fun extensionApiVersion(file: File): Int? = runCatching { apiVersionIn(file) }.getOrNull()

    fun installedVersionCode(pkg: String): Long = runCatching {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
    }.getOrDefault(0L)

    fun installedSignature(pkg: String): String? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val info = context.packageManager.getPackageInfo(pkg, flags)
        signatureOf(info)
    }.getOrNull()

    fun isInstalled(pkg: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    }.getOrDefault(false)

    /** Permissions an extension should never need for fetching and playing media. */
    fun unusualPermissions(permissions: List<String>): List<String> = permissions.filter { it !in ALLOWED_PERMISSIONS }

    private fun apiVersionIn(file: File): Int = ZipFile(file).use { zip ->
        val entry = zip.getEntry(EXTENSION_META_PATH) ?: return@use EXTENSION_API_VERSION
        val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        runCatching { json.decodeFromString(ExtensionMeta.serializer(), text).apiVersion }
            .getOrDefault(EXTENSION_API_VERSION)
    }

    private fun signatureOf(info: PackageInfo): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return null
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: return null
        }
        val certificate = signatures.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        val ALLOWED_PERMISSIONS = setOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
        )
    }
}
