package app.aniverse.source.runtime

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.util.zip.ZipFile

/**
 * Which extension contract an APK follows.
 *
 * AniVerse has its own `app.aniverse.source.api` contract, but the ecosystem people actually
 * install from (Keiyoushi, Yuzono, ...) ships Aniyomi extensions. Both are supported, and
 * everything that is neither is rejected with a readable reason instead of being installed and
 * failing later with a class-cast error.
 */
sealed interface ExtensionFormat {
    data object AniVerse : ExtensionFormat

    data class Aniyomi(
        val factoryClass: String?,
        val libVersion: String?,
        val nsfw: Boolean,
    ) : ExtensionFormat

    data class Unsupported(val reason: String) : ExtensionFormat
}

object ExtensionFormats {
    const val ANIYOMI_CLASS_META = "tachiyomi.animeextension.class"
    const val ANIYOMI_LIB_META = "aniyomix.extensionLib"
    const val ANIYOMI_NAME_META = "aniyomix.name"
    const val ANIYOMI_NSFW_META = "tachiyomi.animeextension.nsfw"
    const val MANGA_CLASS_META = "tachiyomi.extension.class"

    /** The extension library generation this host implements. */
    const val SUPPORTED_ANIYOMI_LIB_MAJOR = 16

    private const val FACTORY_SERVICE = "META-INF/services/app.aniverse.source.api.SourceFactory"
    private const val META_JSON = "assets/aniverse-extension.json"
    private const val FACTORY_TEXT = "assets/aniverse-factory.txt"

    /** Inspects an APK on disk. Works before installation and without package visibility. */
    fun inspect(context: Context, apk: File): ExtensionFormat {
        val metaData = runCatching {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_META_DATA)
                ?.applicationInfo
                ?.metaData
        }.getOrNull()

        val native = runCatching {
            ZipFile(apk).use { zip ->
                zip.getEntry(FACTORY_SERVICE) != null ||
                    zip.getEntry(META_JSON) != null ||
                    zip.getEntry(FACTORY_TEXT) != null
            }
        }.getOrDefault(false)
        if (native) return ExtensionFormat.AniVerse

        val factory = metaData?.getString(ANIYOMI_CLASS_META)
        if (!factory.isNullOrBlank()) {
            return ExtensionFormat.Aniyomi(
                factoryClass = factory,
                libVersion = metaData.getString(ANIYOMI_LIB_META),
                nsfw = metaData.getString(ANIYOMI_NSFW_META) == "1",
            )
        }

        if (!metaData?.getString(MANGA_CLASS_META).isNullOrBlank()) {
            return ExtensionFormat.Unsupported(
                "This is a manga extension. AniVerse only runs anime extensions.",
            )
        }

        return ExtensionFormat.Unsupported(
            "This APK is not an extension: it declares neither an AniVerse SourceFactory " +
                "nor an Aniyomi extension entry point.",
        )
    }

    /**
     * True when the extension was built against a newer library generation than this host
     * implements, e.g. `17.0`. Missing metadata is treated as compatible.
     */
    fun isNewerThanSupported(libVersion: String?): Boolean {
        val major = libVersion?.substringBefore('.')?.trim()?.toIntOrNull() ?: return false
        return major > SUPPORTED_ANIYOMI_LIB_MAJOR
    }
}
