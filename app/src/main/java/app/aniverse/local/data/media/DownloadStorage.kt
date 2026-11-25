package app.aniverse.local.data.media

import java.io.FileOutputStream
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import app.aniverse.local.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Episode files live either in a folder the user picked through the Storage Access Framework or,
 * when none is configured, in the app's own external files directory. Writes always go to a
 * `.part` file first and are renamed when complete, so a half-downloaded file is never served.
 */
@Singleton
class DownloadStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    inner class Sink internal constructor(
        val displayName: String,
        private val document: DocumentFile?,
        private val file: File?,
    ) {
        fun length(): Long = document?.length() ?: file?.length() ?: 0L

        fun openAppend(append: Boolean): OutputStream? = try {
            if (document != null) {
                context.contentResolver.openOutputStream(document.uri, if (append) "wa" else "wt")
            } else {
                file?.let { FileOutputStream(it, append) }
            }
        } catch (e: Exception) {
            null
        }

        fun renameTo(newName: String): Boolean {
            if (document != null) return document.renameTo(newName)
            val source = file ?: return false
            val target = File(source.parentFile, newName)
            return source.renameTo(target)
        }

        fun delete(): Boolean = when {
            document != null -> document.delete()
            file != null -> file.delete()
            else -> false
        }

        fun exists(): Boolean = document?.exists() ?: (file?.isFile == true)
    }

    suspend fun createPart(seriesTitle: String, episodeName: String, extension: String): Sink? {
        val partName = "${safeName("$seriesTitle - $episodeName")}.$extension.part"
        val tree = treeRoot()
        if (tree != null) {
            val existing = tree.findFile(partName) ?: tree.createFile(MIME_BINARY, partName)
            return existing?.let { Sink(partName, it, null) }
        }
        val directory = fallbackDirectory()
        if (!directory.isDirectory && !directory.mkdirs()) return null
        return Sink(partName, null, File(directory, partName))
    }

    /** Renames a finished `.part` file and returns its final name, or null when that fails. */
    suspend fun commit(part: Sink, seriesTitle: String, episodeName: String, extension: String): String? {
        val finalName = "${safeName("$seriesTitle - $episodeName")}.$extension"
        if (!part.renameTo(finalName)) return null
        return if (exists(finalName)) finalName else null
    }

    suspend fun exists(name: String): Boolean {
        if (name.startsWith("content://")) {
            return DocumentFile.fromSingleUri(context, Uri.parse(name))?.exists() == true
        }
        return File(name).isFile
    }

    suspend fun delete(reference: String): Boolean = try {
        if (reference.startsWith("content://")) {
            DocumentFile.fromSingleUri(context, Uri.parse(reference))?.delete() ?: false
        } else {
            File(reference).delete()
        }
    } catch (e: Exception) {
        false
    }

    suspend fun reference(name: String): String {
        val tree = treeRoot() ?: return File(fallbackDirectory(), name).absolutePath
        return tree.findFile(name)?.uri?.toString() ?: File(fallbackDirectory(), name).absolutePath
    }

    fun freeBytes(): Long = try {
        val target = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        StatFs(target.absolutePath).availableBytes
    } catch (e: Exception) {
        0L
    }

    fun locationLabel(): String {
        val uri = runCatching { settings.current().downloadTreeUri }.getOrNull()
        return uri ?: (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir).absolutePath
    }

    private suspend fun treeRoot(): DocumentFile? {
        val uri = runCatching { settings.current().downloadTreeUri }.getOrNull() ?: return null
        val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uri)) }.getOrNull() ?: return null
        if (!tree.exists() || !tree.isDirectory) return null
        return tree.findFile(ROOT_FOLDER) ?: tree.createDirectory(ROOT_FOLDER)
    }

    private fun fallbackDirectory(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        return File(base, ROOT_FOLDER)
    }

    private fun safeName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim()
        val shortened = cleaned.take(90).ifBlank { "episode" }
        return shortened
    }

    private companion object {
        const val ROOT_FOLDER = "AniVerse"
        const val MIME_BINARY = "application/octet-stream"
    }
}
