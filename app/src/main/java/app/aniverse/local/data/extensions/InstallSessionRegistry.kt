package app.aniverse.local.data.extensions

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Bridges the PackageInstaller result broadcast back to the suspending install call. */
data class InstallSignal(val statusCode: Int, val message: String?)

@Singleton
class InstallSessionRegistry @Inject constructor() {
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<InstallSignal>>()

    fun register(sessionId: Int): CompletableDeferred<InstallSignal> {
        val deferred = CompletableDeferred<InstallSignal>()
        pending[sessionId] = deferred
        return deferred
    }

    fun complete(sessionId: Int, signal: InstallSignal) {
        pending.remove(sessionId)?.complete(signal)
    }

    fun forget(sessionId: Int) {
        pending.remove(sessionId)
    }
}
