package eu.kanade.tachiyomi.torrentutils

import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo

object TorrentUtils {
    fun getTorrentInfo(
        url: String,
        title: String,
    ): TorrentInfo = throw UnsupportedOperationException(
        "Torrent playback is not supported by AniVerse",
    )
}
