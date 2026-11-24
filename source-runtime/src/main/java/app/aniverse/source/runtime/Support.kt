package app.aniverse.source.runtime

import java.net.URI

/** The extension APK could not be found, opened, or did not expose a usable factory. */
class ExtensionLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** An extension call failed. [kind] is one of the `Protocol.ERROR_*` values. */
class SourceCallException(
    val kind: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The extension is paused after repeated failures; the UI shows a disabled notice. */
class CircuitOpenException(message: String) : Exception(message)

/**
 * Single gate for every URL that comes out of an extension. Only http and https survive; file,
 * intent, javascript, data and content URIs are dropped so an extension cannot ask the app to
 * open local files, fire intents or run script.
 */
object UrlGuard {
    private val ALLOWED_SCHEMES = setOf("http", "https")
    private val BLOCKED_SCHEMES = setOf("file", "intent", "javascript", "data", "content", "jar", "ftp", "android.resource")

    fun schemeOf(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return try {
            URI(trimmed).scheme?.lowercase()
        } catch (e: Exception) {
            null
        }
    }

    fun isAllowed(url: String?): Boolean {
        val scheme = schemeOf(url) ?: return false
        if (scheme in BLOCKED_SCHEMES) return false
        return scheme in ALLOWED_SCHEMES
    }

    fun isHttps(url: String?): Boolean = schemeOf(url) == "https"

    /** Scheme and host only, for error messages that reach the UI and for logs. */
    fun redact(url: String?): String {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return "<none>"
        return try {
            val uri = URI(trimmed)
            val host = uri.host ?: "<host>"
            "${uri.scheme ?: "?"}://$host"
        } catch (e: Exception) {
            "<redacted>"
        }
    }

    fun redactMessage(message: String): String = Regex("https?://\\S+").replace(message, "<url>")
}

/**
 * Stops hammering an extension that keeps failing. In-session only; the durable "disabled" flag
 * lives in the database so it survives restarts.
 */
class CircuitBreaker(
    private val threshold: Int = Protocol.FAILURE_THRESHOLD,
    private val cooldownMs: Long = Protocol.COOLDOWN_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var failures = 0
    private var openedAt = 0L

    @Synchronized
    fun ensureClosed() {
        if (isOpen()) {
            throw CircuitOpenException(
                "This extension was paused after repeated failures. It will be tried again later.",
            )
        }
    }

    @Synchronized
    fun recordSuccess() {
        failures = 0
        openedAt = 0L
    }

    @Synchronized
    fun recordFailure() {
        failures += 1
        if (failures >= threshold) openedAt = clock()
    }

    @Synchronized
    fun isOpen(): Boolean {
        if (openedAt == 0L) return false
        if (clock() - openedAt >= cooldownMs) {
            openedAt = 0L
            failures = 0
            return false
        }
        return true
    }

    @Synchronized
    fun reset() {
        failures = 0
        openedAt = 0L
    }
}
