package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Util for evaluating JavaScript in sources.
 *
 * Backed by QuickJS, the engine Aniyomi itself uses, so obfuscated-token sources behave the same.
 */
@Suppress("unused_parameter", "UNCHECKED_CAST")
class JavaScriptEngine(context: Context) {

    /**
     * Evaluate arbitrary JavaScript code and get the result as a primitive type
     * (e.g., String, Int).
     *
     * @since extensions-lib 14
     * @param script JavaScript to execute.
     * @return Result of JavaScript code as a primitive type.
     */
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Default) {
        val engine = try {
            QuickJs.create()
        } catch (e: Throwable) {
            throw IllegalStateException("JavaScript is not available in this build", e)
        }
        engine.use {
            try {
                it.evaluate(script) as T
            } catch (e: Throwable) {
                throw IllegalStateException(e.message ?: "The script could not be evaluated", e)
            }
        }
    }
}
