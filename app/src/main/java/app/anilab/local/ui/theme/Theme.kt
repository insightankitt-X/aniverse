package app.anilab.local.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.anilab.local.domain.model.ThemeMode

/** True when the system asked for reduced motion; shimmer and long transitions are disabled. */
val LocalReduceMotion = staticCompositionLocalOf { false }

// Hand-picked indigo/violet fallback used when dynamic colour is off or unavailable.
private val Indigo80 = Color(0xFFC7C4FF)
private val Indigo40 = Color(0xFF5B54D6)
private val Violet80 = Color(0xFFE7D6FF)
private val Violet40 = Color(0xFF7C4DBC)
private val Ink = Color(0xFF12101A)
private val InkElevated = Color(0xFF1B1826)
private val Paper = Color(0xFFFCFAFF)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Color(0xFF221C6B),
    primaryContainer = Color(0xFF3A3494),
    onPrimaryContainer = Color(0xFFE4E0FF),
    secondary = Violet80,
    onSecondary = Color(0xFF351B58),
    secondaryContainer = Color(0xFF4C2B77),
    onSecondaryContainer = Color(0xFFF1E4FF),
    background = Ink,
    onBackground = Color(0xFFE8E4F2),
    surface = Ink,
    onSurface = Color(0xFFE8E4F2),
    surfaceVariant = InkElevated,
    onSurfaceVariant = Color(0xFFC9C4D8),
    outline = Color(0xFF8D879E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val PureBlackColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121212),
)

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF1A1466),
    secondary = Violet40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E4FF),
    onSecondaryContainer = Color(0xFF2C0F4A),
    background = Paper,
    onBackground = Color(0xFF1B1826),
    surface = Paper,
    onSurface = Color(0xFF1B1826),
    surfaceVariant = Color(0xFFECE8F5),
    onSurfaceVariant = Color(0xFF48445A),
    error = Color(0xFFBA1A1A),
)

private val AniLabTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AniLabTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    pureBlack: Boolean = false,
    dynamicColor: Boolean = true,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> if (pureBlack) PureBlackColors else DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = colors, typography = AniLabTypography, content = content)
    }
}
