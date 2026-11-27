package app.aniverse.local.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses both shapes of extension index: a bare JSON array or `{"extensions": [...]}`. Unknown keys
 * are ignored, relative apk/icon paths are resolved against the index URL, missing `nsfw` means 0 and
 * a missing icon leaves the UI to render a letter tile. Malformed entries are skipped instead of
 * failing the whole repository.
 */
class RepoIndexParser(private val json: Json = DEFAULT_JSON) {

    /** Returns null when the payload is not an extension index at all. */
    fun parse(body: String, indexUrl: String): ParsedIndex? {
        val cleaned = body.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return null

        val root = runCatching { json.parseToJsonElement(cleaned) }.getOrNull() ?: return null
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["extensions"] as? JsonArray ?: return null
            else -> return null
        }
        // A bare array of scalars (`[1,2,3]`) is somebody else's payload, not an extension index.
        if (array.isNotEmpty() && array.none { it is JsonObject }) return null

        val entries = array.mapNotNull { element ->
            val dto = runCatching {
                json.decodeFromJsonElement(RepoExtensionDto.serializer(), element)
            }.getOrNull() ?: return@mapNotNull null
            toEntry(dto, indexUrl)
        }
            .distinctBy { it.pkg }
            .sortedBy { it.name.lowercase() }

        return ParsedIndex(name = RepoUrlResolver.displayName(indexUrl), extensions = entries)
    }

    private fun toEntry(dto: RepoExtensionDto, indexUrl: String): ParsedExtension? {
        val pkg = dto.pkg?.trim().orEmpty()
        val apk = dto.apk?.trim().orEmpty()
        if (pkg.isEmpty() || apk.isEmpty()) return null

        val apkUrl = RepoUrlResolver.resolveFile(indexUrl, apk, bareFolder = APK_FOLDER) ?: return null
        // Repository indexes are https-only, so their relative APK links must stay https as well.
        // An http index is only accepted by the local integration test harness.
        val requireHttps = indexUrl.startsWith("https://", ignoreCase = true)
        if (requireHttps && !apkUrl.startsWith("https://", ignoreCase = true)) return null

        val icon = dto.icon?.trim().orEmpty()
        // Aniyomi repositories do not put icon paths in the index; they publish
        // `icon/<package>.png` next to it, so build that URL instead of showing a letter tile.
        val iconUrl = if (icon.isEmpty()) {
            RepoUrlResolver.resolveFile(indexUrl, "$pkg.png", bareFolder = ICON_FOLDER)
        } else {
            RepoUrlResolver.resolveFile(indexUrl, icon, bareFolder = ICON_FOLDER)
        }
        return ParsedExtension(
            pkg = pkg,
            name = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: pkg,
            versionName = dto.version?.trim()?.takeIf { it.isNotEmpty() } ?: "0.0.0",
            versionCode = dto.code ?: 0,
            lang = dto.lang?.trim()?.takeIf { it.isNotEmpty() } ?: "en",
            nsfw = (dto.nsfw ?: 0) != 0,
            iconUrl = iconUrl,
            description = dto.description?.trim()?.takeIf { it.isNotEmpty() },
            apkUrl = apkUrl,
        )
    }

    companion object {
        private const val APK_FOLDER = "apk"
        private const val ICON_FOLDER = "icon"

        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
