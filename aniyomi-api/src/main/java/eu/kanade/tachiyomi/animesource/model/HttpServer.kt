package eu.kanade.tachiyomi.animesource.model

import fi.iki.elonen.NanoHTTPD
import keiyoushi.annotation.AnimiruOnly

@AnimiruOnly
open class HttpServer : NanoHTTPD(0) {
    /** Loopback address of this server once the host has started it. */
    val url: String
        get() = "http://127.0.0.1:$listeningPort"

    companion object {
        const val PLACEHOLDER_URL = "http://localhost:1"
    }
}
