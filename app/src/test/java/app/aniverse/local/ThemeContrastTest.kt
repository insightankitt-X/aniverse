package app.aniverse.local

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import app.aniverse.local.domain.model.ThemePalette
import app.aniverse.local.ui.theme.staticColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Every palette must keep readable text. This is a plain JVM test: Compose Color and the Material 3
 * scheme builders are pure Kotlin, so no device is needed.
 */
class ThemeContrastTest {

    private val palettes = ThemePalette.entries.filter { it != ThemePalette.DYNAMIC }

    @Test
    fun darkAndLightPalettesMeetWcagAa() {
        val failures = mutableListOf<String>()
        for (palette in palettes) {
            for (dark in listOf(true, false)) {
                val scheme = staticColorScheme(palette, dark, amoled = false, accentArgb = null)
                val mode = if (dark) "dark" else "light"
                check(failures, palette, mode, "primary", scheme.primary, scheme.onPrimary)
                check(failures, palette, mode, "background", scheme.background, scheme.onBackground)
                check(failures, palette, mode, "surface", scheme.surface, scheme.onSurface)
                check(failures, palette, mode, "surfaceVariant", scheme.surfaceVariant, scheme.onSurfaceVariant)
                check(failures, palette, mode, "primaryContainer", scheme.primaryContainer, scheme.onPrimaryContainer)
                check(failures, palette, mode, "secondaryContainer", scheme.secondaryContainer, scheme.onSecondaryContainer)
                check(failures, palette, mode, "error", scheme.error, scheme.onError)
                check(failures, palette, mode, "errorContainer", scheme.errorContainer, scheme.onErrorContainer)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun customAccentKeepsReadableOnColor() {
        val failures = mutableListOf<String>()
        val accents = listOf(0xFFD9232E, 0xFFFFE066, 0xFF00A37A, 0xFF1E3A8A, 0xFFF3F4F6)
        for (accent in accents) {
            for (dark in listOf(true, false)) {
                val scheme = staticColorScheme(ThemePalette.CRIMSON, dark, amoled = false, accentArgb = accent)
                check(failures, ThemePalette.CRIMSON, if (dark) "dark" else "light", "custom accent", scheme.primary, scheme.onPrimary)
                check(failures, ThemePalette.CRIMSON, if (dark) "dark" else "light", "custom accent container", scheme.primaryContainer, scheme.onPrimaryContainer)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun check(
        failures: MutableList<String>,
        palette: ThemePalette,
        mode: String,
        role: String,
        background: Color,
        foreground: Color,
    ) {
        val ratio = contrastRatio(background, foreground)
        if (ratio < 4.5) {
            failures += "${palette.name}/$mode $role contrast ${"%.2f".format(ratio)}:1"
        }
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val first = luminance(a)
        val second = luminance(b)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    @Suppress("unused")
    private fun ColorScheme.roles(): List<Color> = listOf(primary, background, surface, surfaceVariant)
}
