package app.aniverse.source.runtime

import app.aniverse.source.api.Source
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Facade used by the data layer. Owns one circuit breaker per installed extension package and
 * forwards failures so the app can persist an extension's health.
 */
class SourceRuntime(
    private val client: ExtensionClient,
    private val json: Json,
) {
    private val breakers = ConcurrentHashMap<String, CircuitBreaker>()
    private val failureListeners = ConcurrentHashMap<String, (Throwable) -> Unit>()

    fun observeFailures(pkg: String, listener: (Throwable) -> Unit) {
        failureListeners[pkg] = listener
    }

    suspend fun apiVersion(pkg: String): Int = client.apiVersion(pkg)

    suspend fun sources(pkg: String): List<Source> {
        val breaker = breakers.getOrPut(pkg) { CircuitBreaker() }
        return client.sources(pkg).map { descriptor ->
            RemoteSource(
                sourceId = descriptor.sourceId,
                name = descriptor.name,
                lang = descriptor.lang,
                versionId = descriptor.versionId,
                pkg = pkg,
                client = client,
                json = json,
                breaker = breaker,
                onCallFailed = { error -> failureListeners[pkg]?.invoke(error) },
            )
        }
    }

    suspend fun source(pkg: String, sourceId: Long): Source? =
        sources(pkg).firstOrNull { it.sourceId == sourceId }

    fun isPaused(pkg: String): Boolean = breakers[pkg]?.isOpen() == true

    fun reset(pkg: String) {
        breakers[pkg]?.reset()
    }
}
