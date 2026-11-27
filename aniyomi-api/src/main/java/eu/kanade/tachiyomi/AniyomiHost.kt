package eu.kanade.tachiyomi

import android.app.Application
import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.TypeReference
import java.lang.reflect.Type

/**
 * The seam between AniVerse and the Aniyomi extension API.
 *
 * Aniyomi extensions are compiled against `extensions-lib` with the library on the *compile* class
 * path only: at runtime the host application has to supply the real implementations of the model
 * factories, the network client and the dependency-injection registry. This object owns those
 * singletons for the `:extension` process and must be initialised before any extension class is
 * instantiated.
 */
object AniyomiHost {
    /** Shared JSON parser extensions resolve through `Injekt.get<Json>()`. */
    val defaultJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Volatile
    private var helper: NetworkHelper? = null

    @Volatile
    private var versionCode: Int = 0

    @Volatile
    private var versionName: String = "0.0.0"

    /** Extensions reach this through `AnimeHttpSource.network`; init() has always run by then. */
    val network: NetworkHelper
        get() = helper ?: throw IllegalStateException("AniyomiHost was not initialised")

    fun appVersionCode(): Int = versionCode

    fun appVersionName(): String = versionName

    /**
     * Idempotent. Called by the extension host in the `:extension` process before loading any
     * extension, so third-party code never sees an uninitialised registry.
     */
    @Synchronized
    fun init(context: Context) {
        val app = context.applicationContext as? Application ?: return
        if (helper == null) {
            helper = NetworkHelper(app)
        }
        readAppVersion(app)
        registerInjectables(app)
    }

    private fun readAppVersion(app: Application) {
        runCatching {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
            versionName = info.versionName ?: "0.0.0"
        }
    }

    private fun registerInjectables(app: Application) {
        // Extensions resolve Application/Context for preferences, toasts and cache directories.
        // Re-registering is harmless: Injekt keeps the last singleton for a type.
        runCatching {
            Injekt.addSingleton(typeRef<Application>(), app)
            Injekt.addSingleton(typeRef<Context>(), app)
            Injekt.addSingleton(typeRef<Json>(), defaultJson)
        }
    }

    private inline fun <reified T : Any> typeRef(): TypeReference<T> = object : TypeReference<T> {
        override val type: Type = T::class.java
    }
}
