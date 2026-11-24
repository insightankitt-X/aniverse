package app.aniverse.local.data.source

import app.aniverse.source.runtime.UrlGuard

/** Raised when an extension that used to provide a source is gone, disabled or broken. */
class SourceUnavailableException(message: String) : Exception(message)

/** A message that is safe and useful to show in the UI. */
fun Throwable.userMessage(): String {
    val raw = message?.takeIf { it.isNotBlank() } ?: return "Something went wrong. Try again."
    return UrlGuard.redactMessage(raw)
}

fun offlineMessage(): String = "You're offline"
