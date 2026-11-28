package app.aniverse.local.data.prefs

import androidx.compose.runtime.Immutable
import app.aniverse.local.domain.model.ThemeMode
import app.aniverse.local.domain.model.ThemePalette
import app.aniverse.local.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the theme needs, separate from playback/download settings. */
@Immutable
data class ThemeState(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.CRIMSON,
    val amoled: Boolean = false,
    val accentArgb: Long? = null,
    val reduceMotion: Boolean = false,
)

/**
 * Single writer for appearance preferences. DataStore lives behind SettingsRepository so the app
 * still has one persisted settings source; this class is the typed facade.
 */
@Singleton
class ThemeRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    val state: Flow<ThemeState> = settingsRepository.settings
        .map {
            ThemeState(
                mode = it.themeMode,
                palette = it.themePalette,
                amoled = it.pureBlack,
                accentArgb = it.accentArgb,
                reduceMotion = it.reduceMotion,
            )
        }
        .distinctUntilChanged()

    suspend fun setMode(mode: ThemeMode) = settingsRepository.edit { it.copy(themeMode = mode) }

    suspend fun setPalette(palette: ThemePalette) = settingsRepository.edit { it.copy(themePalette = palette) }

    suspend fun setAmoled(enabled: Boolean) = settingsRepository.edit { it.copy(pureBlack = enabled) }

    suspend fun setAccent(argb: Long?) = settingsRepository.edit { it.copy(accentArgb = argb) }

    suspend fun setReduceMotion(enabled: Boolean) = settingsRepository.edit { it.copy(reduceMotion = enabled) }
}
