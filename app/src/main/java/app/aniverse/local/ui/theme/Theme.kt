package app.aniverse.local.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.aniverse.local.domain.model.Settings
import app.aniverse.local.domain.model.ThemeMode

/** True when the user asked for reduced motion; shimmer and long transitions are disabled. */
val LocalReduceMotion = staticCompositionLocalOf { false }

val AniShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun AniVerseTheme(
    settings: Settings = Settings(),
    applySystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = aniverseColorScheme(
        palette = settings.themePalette,
        dark = dark,
        amoled = settings.pureBlack,
        accentArgb = settings.accentArgb,
    )
    val scheme = base.animatedColors(settings.reduceMotion)
    val extended = AniExtendedColors(
        hdFill = scheme.primary,
        hdText = scheme.onPrimary,
    )

    val view = LocalView.current
    if (applySystemBars && !view.isInEditMode) {
        val window = (LocalContext.current as? Activity)?.window
        SideEffect {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalReduceMotion provides settings.reduceMotion,
        LocalAniDark provides dark,
        LocalAniExtendedColors provides extended,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AniTypography,
            shapes = AniShapes,
            content = content,
        )
    }
}

/** Colours cross-fade when the palette changes, so the whole app never hard-flips. */
@Composable
private fun ColorScheme.animatedColors(reduceMotion: Boolean): ColorScheme {
    if (reduceMotion) return this
    val spec = tween<Color>(Motion.EmphasizedMs)
    @Composable
    fun color(target: Color, label: String): Color =
        animateColorAsState(targetValue = target, animationSpec = spec, label = label).value

    return copy(
        primary = color(primary, "primary"),
        onPrimary = color(onPrimary, "onPrimary"),
        primaryContainer = color(primaryContainer, "primaryContainer"),
        onPrimaryContainer = color(onPrimaryContainer, "onPrimaryContainer"),
        background = color(background, "background"),
        onBackground = color(onBackground, "onBackground"),
        surface = color(surface, "surface"),
        onSurface = color(onSurface, "onSurface"),
        surfaceVariant = color(surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = color(onSurfaceVariant, "onSurfaceVariant"),
        surfaceContainer = color(surfaceContainer, "surfaceContainer"),
        outline = color(outline, "outline"),
    )
}

/** Preview-only convenience so component previews always render a complete theme. */
@Composable
fun AniThemePreview(
    dark: Boolean = true,
    palette: app.aniverse.local.domain.model.ThemePalette = app.aniverse.local.domain.model.ThemePalette.CRIMSON,
    content: @Composable () -> Unit,
) {
    AniVerseTheme(
        settings = Settings(
            themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
            themePalette = palette,
        ),
        applySystemBars = false,
        content = content,
    )
}

/** A neutral light fallback kept for code that only needs a scheme, never a full theme. */
val AniLightFallbackScheme = lightColorScheme()
