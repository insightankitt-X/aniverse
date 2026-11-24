package app.aniverse.local.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.aniverse.local.domain.model.PlayerQualityMode
import app.aniverse.local.domain.model.Settings
import app.aniverse.local.domain.model.SubtitleSize
import app.aniverse.local.domain.model.ThemeMode
import app.aniverse.local.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    override val settings: Flow<Settings> = context.settingsDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    override suspend fun current(): Settings = settings.first()

    override suspend fun edit(transform: (Settings) -> Settings) {
        context.settingsDataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.THEME] = updated.themeMode.name
            prefs[Keys.PURE_BLACK] = updated.pureBlack
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColor
            prefs[Keys.REDUCE_MOTION] = updated.reduceMotion
            prefs[Keys.LANGUAGE] = updated.language
            prefs[Keys.UPDATE_INTERVAL] = updated.updateIntervalHours
            prefs[Keys.WIFI_ONLY_UPDATES] = updated.wifiOnlyUpdates
            prefs[Keys.QUALITY_MODE] = updated.playerQualityMode.name
            prefs[Keys.PREFERRED_QUALITY] = updated.playerPreferredQuality.orEmpty()
            prefs[Keys.PLAYER_SPEED] = updated.playerSpeed
            prefs[Keys.SUBTITLE_SIZE] = updated.subtitleSize.name
            prefs[Keys.SUBTITLE_BACKGROUND] = updated.subtitleBackground
            prefs[Keys.DOWNLOAD_TREE] = updated.downloadTreeUri.orEmpty()
            prefs[Keys.DOWNLOAD_PARALLEL] = updated.downloadParallelLimit
            prefs[Keys.DOWNLOAD_WIFI_ONLY] = updated.downloadWifiOnly
            prefs[Keys.SHOW_NSFW] = updated.showNsfw
            prefs[Keys.CLEARTEXT_WARNED] = updated.dismissedCleartextWarning
            prefs[Keys.SEARCH_HISTORY] = updated.searchHistory.joinToString(SEPARATOR)
        }
    }

    private fun Preferences.toSettings(): Settings = Settings(
        themeMode = enumValue(this[Keys.THEME], ThemeMode.SYSTEM),
        pureBlack = this[Keys.PURE_BLACK] ?: false,
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: true,
        reduceMotion = this[Keys.REDUCE_MOTION] ?: false,
        language = this[Keys.LANGUAGE] ?: "system",
        updateIntervalHours = this[Keys.UPDATE_INTERVAL] ?: 12,
        wifiOnlyUpdates = this[Keys.WIFI_ONLY_UPDATES] ?: true,
        playerQualityMode = enumValue(this[Keys.QUALITY_MODE], PlayerQualityMode.AUTO),
        playerPreferredQuality = this[Keys.PREFERRED_QUALITY]?.takeIf { it.isNotBlank() },
        playerSpeed = this[Keys.PLAYER_SPEED] ?: 1f,
        subtitleSize = enumValue(this[Keys.SUBTITLE_SIZE], SubtitleSize.MEDIUM),
        subtitleBackground = this[Keys.SUBTITLE_BACKGROUND] ?: true,
        downloadTreeUri = this[Keys.DOWNLOAD_TREE]?.takeIf { it.isNotBlank() },
        downloadParallelLimit = (this[Keys.DOWNLOAD_PARALLEL] ?: 2).coerceIn(1, 4),
        downloadWifiOnly = this[Keys.DOWNLOAD_WIFI_ONLY] ?: true,
        showNsfw = this[Keys.SHOW_NSFW] ?: false,
        dismissedCleartextWarning = this[Keys.CLEARTEXT_WARNED] ?: false,
        searchHistory = this[Keys.SEARCH_HISTORY]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
    )

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        raw?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val LANGUAGE = stringPreferencesKey("language")
        val UPDATE_INTERVAL = intPreferencesKey("update_interval_hours")
        val WIFI_ONLY_UPDATES = booleanPreferencesKey("wifi_only_updates")
        val QUALITY_MODE = stringPreferencesKey("quality_mode")
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        val PLAYER_SPEED = floatPreferencesKey("player_speed")
        val SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
        val SUBTITLE_BACKGROUND = booleanPreferencesKey("subtitle_background")
        val DOWNLOAD_TREE = stringPreferencesKey("download_tree")
        val DOWNLOAD_PARALLEL = intPreferencesKey("download_parallel")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val SHOW_NSFW = booleanPreferencesKey("show_nsfw")
        val CLEARTEXT_WARNED = booleanPreferencesKey("cleartext_warned")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
    }

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
