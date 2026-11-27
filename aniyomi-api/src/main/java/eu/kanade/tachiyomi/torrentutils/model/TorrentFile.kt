package eu.kanade.tachiyomi.torrentutils.model

import java.net.URLEncoder

data class TorrentFile(
    val path: String,
    val indexFile: Int,
    val size: Long,
    private val torrentHash: String,
    private val trackers: List<String> = emptyList(),
) {
    fun toMagnetURI(): String {
        val name = path.substringAfterLast('/').ifEmpty { path }
        val encodedName = runCatching { URLEncoder.encode(name, "UTF-8") }.getOrDefault(name)
        val trackerPart = trackers
            .filter { it.isNotBlank() }
            .joinToString(separator = "") { tracker ->
                val encoded = runCatching { URLEncoder.encode(tracker, "UTF-8") }.getOrDefault(tracker)
                "&tr=$encoded"
            }
        return "magnet:?xt=urn:btih:$torrentHash&dn=$encodedName$trackerPart"
    }
}
