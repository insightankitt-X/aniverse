package app.anilab.source.runtime

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import app.anilab.source.api.ExtensionApiMismatchException
import app.anilab.source.api.FilterList
import app.anilab.source.api.RemoteEpisode
import app.anilab.source.api.RemoteSeries
import app.anilab.source.api.SeriesPage
import app.anilab.source.api.Source
import app.anilab.source.api.Video
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Content provider hosted in the `:extension` process. The app process never loads extension code
 * itself, so a crash or ANR in third-party code cannot take the UI down with it.
 */
class ExtensionProvider : ContentProvider() {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (Binder.getCallingUid() != Process.myUid()) {
            return failure(Protocol.ERROR_DENIED, "Extensions can only be used by AniLab Local")
        }
        val host = context ?: return failure(Protocol.ERROR_RUNTIME, "The extension host is not available")
        val args = extras ?: return failure(Protocol.ERROR_RUNTIME, "Missing extension package")
        val pkg = args.getString(Protocol.KEY_PKG)
            ?: return failure(Protocol.ERROR_RUNTIME, "Missing extension package")

        return try {
            when (method) {
                Protocol.METHOD_API_VERSION -> {
                    successInt(ExtensionHost.apiVersion(host, pkg))
                }

                Protocol.METHOD_SOURCES -> {
                    val descriptors = ExtensionHost.descriptors(host, pkg)
                    success(json.encodeToString(ListSerializer(SourceDescriptor.serializer()), descriptors))
                }

                Protocol.METHOD_CALL -> {
                    val sourceId = args.getLong(Protocol.KEY_SOURCE_ID)
                    val callMethod = args.getString(Protocol.KEY_CALL_METHOD) ?: arg
                    val payload = args.getString(Protocol.KEY_PAYLOAD).orEmpty()
                    if (callMethod.isNullOrBlank()) {
                        failure(Protocol.ERROR_RUNTIME, "Missing extension method")
                    } else {
                        val source = ExtensionHost.source(host, pkg, sourceId)
                        success(runBlocking { dispatch(source, callMethod, payload) })
                    }
                }

                else -> failure(Protocol.ERROR_RUNTIME, "Unknown extension request")
            }
        } catch (e: ExtensionApiMismatchException) {
            failure(Protocol.ERROR_API, e.message ?: "Incompatible extension")
        } catch (e: ExtensionLoadException) {
            failure(Protocol.ERROR_LOAD, e.message ?: "The extension could not be loaded")
        } catch (e: Throwable) {
            failure(Protocol.ERROR_RUNTIME, e.message ?: "The extension call failed")
        }
    }

    private suspend fun dispatch(source: Source, method: String, payload: String): String = when (method) {
        Protocol.METHOD_POPULAR -> json.encodeToString(
            SeriesPage.serializer(),
            source.getPopular(json.decodeFromString(PageRequest.serializer(), payload).page),
        )

        Protocol.METHOD_LATEST -> json.encodeToString(
            SeriesPage.serializer(),
            source.getLatest(json.decodeFromString(PageRequest.serializer(), payload).page),
        )

        Protocol.METHOD_SEARCH -> {
            val request = json.decodeFromString(SearchRequest.serializer(), payload)
            json.encodeToString(SeriesPage.serializer(), source.search(request.query, request.filters, request.page))
        }

        Protocol.METHOD_DETAILS -> json.encodeToString(
            RemoteSeries.serializer(),
            source.getDetails(json.decodeFromString(DetailsRequest.serializer(), payload).series),
        )

        Protocol.METHOD_EPISODES -> json.encodeToString(
            ListSerializer(RemoteEpisode.serializer()),
            source.getEpisodes(json.decodeFromString(EpisodesRequest.serializer(), payload).series),
        )

        Protocol.METHOD_VIDEOS -> json.encodeToString(
            ListSerializer(Video.serializer()),
            source.getVideoList(json.decodeFromString(VideosRequest.serializer(), payload).episode),
        )

        Protocol.METHOD_FILTERS -> json.encodeToString(FilterList.serializer(), source.getFilterList())

        else -> throw ExtensionLoadException("Unknown extension method: $method")
    }

    private fun success(payload: String): Bundle = Bundle().apply {
        putString(Protocol.KEY_RESULT, payload)
    }

    private fun successInt(value: Int): Bundle = Bundle().apply {
        putInt(Protocol.KEY_API_VERSION, value)
    }

    private fun failure(kind: String, message: String): Bundle = Bundle().apply {
        putString(Protocol.KEY_ERROR_KIND, kind)
        putString(Protocol.KEY_ERROR_MESSAGE, UrlGuard.redactMessage(message))
    }

    // This provider exposes no tabular data.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
