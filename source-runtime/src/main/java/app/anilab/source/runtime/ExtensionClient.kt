package app.anilab.source.runtime

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** App-process half of the extension bridge. */
class ExtensionClient(
    private val context: Context,
    private val json: Json,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun apiVersion(pkg: String): Int = bounded {
        val bundle = rpc(Protocol.METHOD_API_VERSION, pkg, sourceId = null, callMethod = null, payload = null)
        throwIfError(bundle)
        bundle.getInt(Protocol.KEY_API_VERSION, -1).takeIf { it > 0 }
            ?: throw SourceCallException(Protocol.ERROR_LOAD, "The extension did not report an API version")
    }

    suspend fun sources(pkg: String): List<SourceDescriptor> = bounded {
        val bundle = rpc(Protocol.METHOD_SOURCES, pkg, sourceId = null, callMethod = null, payload = null)
        throwIfError(bundle)
        val text = bundle.getString(Protocol.KEY_RESULT).orEmpty()
        try {
            json.decodeFromString(ListSerializer(SourceDescriptor.serializer()), text)
        } catch (e: Exception) {
            throw SourceCallException(Protocol.ERROR_RUNTIME, "The extension returned a malformed source list", e)
        }
    }

    suspend fun call(pkg: String, sourceId: Long, method: String, payload: String): String = bounded {
        val bundle = rpc(Protocol.METHOD_CALL, pkg, sourceId, method, payload)
        throwIfError(bundle)
        bundle.getString(Protocol.KEY_RESULT).orEmpty()
    }

    private suspend fun <T> bounded(block: suspend () -> T): T = withContext(io) {
        try {
            withTimeout(Protocol.CALL_TIMEOUT_MS) { block() }
        } catch (e: TimeoutCancellationException) {
            throw SourceCallException(Protocol.ERROR_TIMEOUT, "The extension took too long to respond")
        }
    }

    private fun rpc(method: String, pkg: String, sourceId: Long?, callMethod: String?, payload: String?): Bundle {
        val extras = Bundle().apply {
            putString(Protocol.KEY_PKG, pkg)
            if (sourceId != null) putLong(Protocol.KEY_SOURCE_ID, sourceId)
            if (callMethod != null) putString(Protocol.KEY_CALL_METHOD, callMethod)
            if (payload != null) putString(Protocol.KEY_PAYLOAD, payload)
        }
        return try {
            context.contentResolver.call(Protocol.baseUri(context), method, callMethod, extras)
        } catch (e: Exception) {
            throw SourceCallException(Protocol.ERROR_LOAD, "Could not reach the extension host", e)
        } ?: throw SourceCallException(Protocol.ERROR_LOAD, "The extension host did not respond")
    }

    private fun throwIfError(bundle: Bundle) {
        val kind = bundle.getString(Protocol.KEY_ERROR_KIND) ?: return
        val message = bundle.getString(Protocol.KEY_ERROR_MESSAGE).orEmpty()
            .ifBlank { "The extension call failed" }
        if (kind == Protocol.ERROR_LOAD) throw ExtensionLoadException(message)
        throw SourceCallException(kind, message)
    }
}
