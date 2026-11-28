package app.aniverse.local.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import app.aniverse.local.domain.model.ThemePalette
import kotlin.math.pow

/** Colours Material 3 has no role for. Everything else comes from MaterialTheme.colorScheme. */
@Immutable
data class AniExtendedColors(
    val rating: Color = Color(0xFFFEC92F),
    val chipFill: Color = Color.White,
    val chipText: Color = Color(0xFF101114),
    val hdFill: Color = Color(0xFFD9232E),
    val hdText: Color = Color.White,
    val success: Color = Color(0xFF56D68B),
    val scrim: Color = Color(0xCC0D0E11),
)

val LocalAniExtendedColors = staticCompositionLocalOf { AniExtendedColors() }

/** True when the active theme resolves to dark, used by system-bar icon colours and previews. */
val LocalAniDark = staticCompositionLocalOf { true }

val AniRatingYellow = Color(0xFFFEC92F)

private val InkDeep = Color(0xFF0D0E11)
private val InkSurface = Color(0xFF181A20)
private val InkCard = Color(0xFF1F222A)
private val InkElevated = Color(0xFF272A33)
private val InkOutline = Color(0xFF35383F)

private data class PaletteSpec(
    val darkPrimary: Color,
    val lightPrimary: Color,
    val darkSecondary: Color,
    val lightSecondary: Color,
    val darkTertiary: Color,
    val lightTertiary: Color,
    val tint: Color,
)

private fun ThemePalette.spec(): PaletteSpec = when (this) {
    ThemePalette.CRIMSON -> PaletteSpec(
        darkPrimary = Color(0xFFD9232E),
        lightPrimary = Color(0xFFC21722),
        darkSecondary = Color(0xFFFFB3B7),
        lightSecondary = Color(0xFF8C4A4E),
        darkTertiary = Color(0xFFFEC92F),
        lightTertiary = Color(0xFF8A6A00),
        tint = Color(0xFFD9232E),
    )

    ThemePalette.MIDNIGHT -> PaletteSpec(
        darkPrimary = Color(0xFF8B9DFF),
        lightPrimary = Color(0xFF4257D6),
        darkSecondary = Color(0xFFB9C4FF),
        lightSecondary = Color(0xFF5A668F),
        darkTertiary = Color(0xFF7DE2FF),
        lightTertiary = Color(0xFF00677A),
        tint = Color(0xFF5B6CFF),
    )

    ThemePalette.SAKURA -> PaletteSpec(
        darkPrimary = Color(0xFFFF8FB0),
        lightPrimary = Color(0xFFC2185B),
        darkSecondary = Color(0xFFFFC2D4),
        lightSecondary = Color(0xFF8E4A5F),
        darkTertiary = Color(0xFFFFD166),
        lightTertiary = Color(0xFF7A5A00),
        tint = Color(0xFFFF6E9C),
    )

    ThemePalette.OCEAN -> PaletteSpec(
        darkPrimary = Color(0xFF4ADED8),
        lightPrimary = Color(0xFF00706C),
        darkSecondary = Color(0xFF8CE9E5),
        lightSecondary = Color(0xFF3F6664),
        darkTertiary = Color(0xFF8FD3FF),
        lightTertiary = Color(0xFF0B5D7A),
        tint = Color(0xFF00B3AE),
    )

    ThemePalette.FOREST -> PaletteSpec(
        darkPrimary = Color(0xFF6FDD9A),
        lightPrimary = Color(0xFF1B6B3A),
        darkSecondary = Color(0xFFB7F0C9),
        lightSecondary = Color(0xFF4B6353),
        darkTertiary = Color(0xFFD6E86B),
        lightTertiary = Color(0xFF596400),
        tint = Color(0xFF35B96A),
    )

    ThemePalette.SUNSET -> PaletteSpec(
        darkPrimary = Color(0xFFFFA45C),
        lightPrimary = Color(0xFFA94B0B),
        darkSecondary = Color(0xFFFFD1AE),
        lightSecondary = Color(0xFF7A5742),
        darkTertiary = Color(0xFFFF6B6B),
        lightTertiary = Color(0xFF9B3F3F),
        tint = Color(0xFFFF7A2F),
    )

    ThemePalette.MONOCHROME -> PaletteSpec(
        darkPrimary = Color(0xFFF2F2F2),
        lightPrimary = Color(0xFF1F1F1F),
        darkSecondary = Color(0xFFC9C9C9),
        lightSecondary = Color(0xFF5C5C5C),
        darkTertiary = Color(0xFFE0E0E0),
        lightTertiary = Color(0xFF4A4A4A),
        tint = Color(0xFF9AA0A6),
    )

    ThemePalette.DYNAMIC -> ThemePalette.CRIMSON.spec()
}

/** Chooses the source palette and folds in the accent/AMOLED overrides. */
@Composable
fun aniverseColorScheme(
    palette: ThemePalette,
    dark: Boolean,
    amoled: Boolean,
    accentArgb: Long?,
): ColorScheme {
    val context = LocalContext.current
    if (palette == ThemePalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        val accented = dynamic.withAccentOverride(accentArgb, dark)
        return if (dark && amoled) accented.withPureBlack() else accented
    }
    return staticColorScheme(palette, dark, amoled, accentArgb)
}

/** Non-composable variant used by tests and by the composable path for static palettes. */
internal fun staticColorScheme(
    palette: ThemePalette,
    dark: Boolean,
    amoled: Boolean,
    accentArgb: Long?,
): ColorScheme {
    val base = if (dark) palette.spec().darkScheme(amoled) else palette.spec().lightScheme()
    val accented = base.withAccentOverride(accentArgb, dark)
    return if (dark && amoled) accented.withPureBlack() else accented
}

private fun PaletteSpec.darkScheme(amoled: Boolean): ColorScheme {
    val background = if (amoled) Color.Black else lerp(InkDeep, tint, 0.035f)
    val surface = if (amoled) Color.Black else lerp(InkSurface, tint, 0.045f)
    val card = if (amoled) Color(0xFF08080A) else lerp(InkCard, tint, 0.055f)
    val elevated = if (amoled) Color(0xFF111114) else lerp(InkElevated, tint, 0.06f)
    val outline = lerp(InkOutline, tint, 0.10f)
    val onSurface = Color(0xFFF2F3F7)
    return darkColorScheme(
        primary = darkPrimary,
        onPrimary = contrastOn(darkPrimary),
        primaryContainer = lerp(Color.Black, darkPrimary, 0.36f),
        onPrimaryContainer = lerp(Color.White, darkPrimary, 0.22f),
        inversePrimary = lightPrimary,
        secondary = darkSecondary,
        onSecondary = contrastOn(darkSecondary),
        secondaryContainer = lerp(Color.Black, darkSecondary, 0.28f),
        onSecondaryContainer = lerp(Color.White, darkSecondary, 0.22f),
        tertiary = darkTertiary,
        onTertiary = contrastOn(darkTertiary),
        tertiaryContainer = lerp(Color.Black, darkTertiary, 0.26f),
        onTertiaryContainer = lerp(Color.White, darkTertiary, 0.2f),
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = card,
        onSurfaceVariant = Color(0xFFA2A7B3),
        surfaceTint = darkPrimary,
        inverseSurface = Color(0xFFE9E9EE),
        inverseOnSurface = Color(0xFF17181D),
        error = Color(0xFFFF8A8A),
        onError = Color(0xFF3B0000),
        errorContainer = Color(0xFF5C0F14),
        onErrorContainer = Color(0xFFFFDAD8),
        outline = outline,
        outlineVariant = lerp(outline, Color.Black, 0.35f),
        scrim = Color.Black,
        surfaceBright = elevated,
        surfaceDim = background,
        surfaceContainerLowest = if (amoled) Color.Black else lerp(background, Color.Black, 0.25f),
        surfaceContainerLow = surface,
        surfaceContainer = card,
        surfaceContainerHigh = elevated,
        surfaceContainerHighest = lerp(elevated, Color.White, 0.05f),
    )
}

private fun PaletteSpec.lightScheme(): ColorScheme {
    val background = lerp(Color(0xFFFFFBFA), tint, 0.035f)
    val card = lerp(Color.White, tint, 0.075f)
    val outline = lerp(Color(0xFFD7D9DF), tint, 0.12f)
    val primaryContainer = lerp(Color.White, lightPrimary, 0.17f)
    return lightColorScheme(
        primary = lightPrimary,
        onPrimary = contrastOn(lightPrimary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = lerp(Color.Black, lightPrimary, 0.55f),
        inversePrimary = darkPrimary,
        secondary = lightSecondary,
        onSecondary = contrastOn(lightSecondary),
        secondaryContainer = lerp(Color.White, lightSecondary, 0.18f),
        onSecondaryContainer = lerp(Color.Black, lightSecondary, 0.55f),
        tertiary = lightTertiary,
        onTertiary = contrastOn(lightTertiary),
        tertiaryContainer = lerp(Color.White, lightTertiary, 0.18f),
        onTertiaryContainer = lerp(Color.Black, lightTertiary, 0.55f),
        background = background,
        onBackground = Color(0xFF15161A),
        surface = Color.White,
        onSurface = Color(0xFF15161A),
        surfaceVariant = card,
        onSurfaceVariant = Color(0xFF4C505A),
        surfaceTint = lightPrimary,
        inverseSurface = Color(0xFF2B2D33),
        inverseOnSurface = Color(0xFFF2F3F7),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = outline,
        outlineVariant = lerp(outline, Color.White, 0.4f),
        scrim = Color.Black,
        surfaceBright = Color.White,
        surfaceDim = lerp(background, Color.Black, 0.06f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = lerp(Color.White, tint, 0.02f),
        surfaceContainer = lerp(Color.White, tint, 0.045f),
        surfaceContainerHigh = lerp(Color.White, tint, 0.075f),
        surfaceContainerHighest = lerp(Color.White, tint, 0.11f),
    )
}

private fun ColorScheme.withAccentOverride(argb: Long?, dark: Boolean): ColorScheme {
    if (argb == null) return this
    val accent = Color(argb.toInt())
    val container = if (dark) lerp(Color.Black, accent, 0.34f) else lerp(Color.White, accent, 0.18f)
    val onContainer = if (dark) lerp(Color.White, accent, 0.25f) else lerp(Color.Black, accent, 0.55f)
    return copy(
        primary = accent,
        onPrimary = contrastOn(accent),
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        surfaceTint = accent,
        inversePrimary = accent,
        secondary = lerp(secondary, accent, 0.35f),
        onSecondary = contrastOn(lerp(secondary, accent, 0.35f)),
    )
}

private fun ColorScheme.withPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050506),
    surfaceContainer = Color(0xFF0A0A0C),
    surfaceContainerHigh = Color(0xFF111114),
    surfaceContainerHighest = Color(0xFF17171B),
    surfaceBright = Color(0xFF17171B),
    surfaceVariant = Color(0xFF141418),
    outline = Color(0xFF2C2C33),
    outlineVariant = Color(0xFF1E1E23),
)

/** WCAG relative luminance, used to choose black or white text on an accent. */
fun contrastOn(color: Color): Color {
    fun linear(channel: Float): Float =
        if (channel <= 0.03928f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

    fun luminance(c: Color): Double = (
        0.2126 * linear(c.red) +
            0.7152 * linear(c.green) +
            0.0722 * linear(c.blue)
        ).toDouble()

    val background = luminance(color)
    val ink = Color(0xFF0E0F12)
    val inkLuminance = luminance(ink)
    val inkRatio = (maxOf(background, inkLuminance) + 0.05) / (minOf(background, inkLuminance) + 0.05)
    val whiteRatio = (1.05) / (background + 0.05)
    return if (inkRatio >= whiteRatio) ink else Color.White
}

val ThemePalette.displayName: String
    get() = when (this) {
        ThemePalette.CRIMSON -> "AniVerse Crimson"
        ThemePalette.MIDNIGHT -> "Midnight"
        ThemePalette.SAKURA -> "Sakura"
        ThemePalette.OCEAN -> "Ocean"
        ThemePalette.FOREST -> "Forest"
        ThemePalette.SUNSET -> "Sunset"
        ThemePalette.MONOCHROME -> "Monochrome"
        ThemePalette.DYNAMIC -> "Dynamic"
    }

val ThemePalette.tagline: String
    get() = when (this) {
        ThemePalette.CRIMSON -> "The signature red on charcoal"
        ThemePalette.MIDNIGHT -> "Blue-violet after dark"
        ThemePalette.SAKURA -> "Soft pink blossom"
        ThemePalette.OCEAN -> "Teal and deep water"
        ThemePalette.FOREST -> "Fresh green canopy"
        ThemePalette.SUNSET -> "Warm orange dusk"
        ThemePalette.MONOCHROME -> "Ink, paper and nothing else"
        ThemePalette.DYNAMIC -> "Follows your wallpaper"
    }
