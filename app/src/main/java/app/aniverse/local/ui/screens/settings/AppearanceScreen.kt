package app.aniverse.local.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aniverse.local.data.prefs.ThemeRepository
import app.aniverse.local.data.prefs.ThemeState
import app.aniverse.local.domain.model.Settings
import app.aniverse.local.domain.model.ThemeMode
import app.aniverse.local.domain.model.ThemePalette
import app.aniverse.local.ui.components.AniGroup
import app.aniverse.local.ui.components.AniTopBar
import app.aniverse.local.ui.components.PillStyle
import app.aniverse.local.ui.components.PillTabRow
import app.aniverse.local.ui.components.SegmentedToggle
import app.aniverse.local.ui.components.SettingSwitchRow
import app.aniverse.local.ui.theme.AniVerseTheme
import app.aniverse.local.ui.theme.contrastOn
import app.aniverse.local.ui.theme.displayName
import app.aniverse.local.ui.theme.tagline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
) : ViewModel() {

    val state = themeRepository.state.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeState())

    fun setMode(mode: ThemeMode) = viewModelScope.launch { themeRepository.setMode(mode) }

    fun setPalette(palette: ThemePalette) = viewModelScope.launch { themeRepository.setPalette(palette) }

    fun setAmoled(enabled: Boolean) = viewModelScope.launch { themeRepository.setAmoled(enabled) }

    fun setAccent(argb: Long?) = viewModelScope.launch { themeRepository.setAccent(argb) }

    fun setReduceMotion(enabled: Boolean) = viewModelScope.launch { themeRepository.setReduceMotion(enabled) }
}

private data class AccentChoice(val name: String, val argb: Long)

private val accents = listOf(
    AccentChoice("Crimson", 0xFFD9232E),
    AccentChoice("Rose", 0xFFFF5E7E),
    AccentChoice("Amber", 0xFFFFA33C),
    AccentChoice("Sun", 0xFFFEC92F),
    AccentChoice("Mint", 0xFF3ED598),
    AccentChoice("Ocean", 0xFF2FC7C2),
    AccentChoice("Blue", 0xFF5B8CFF),
    AccentChoice("Violet", 0xFF9B7BFF),
    AccentChoice("Mono", 0xFFE8E8E8),
)

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showHex by remember { mutableStateOf(false) }
    val palettes = remember {
        ThemePalette.entries.filter { it != ThemePalette.DYNAMIC || Build.VERSION.SDK_INT >= 31 }
    }
    val modeIndex = when (state.mode) {
        ThemeMode.SYSTEM -> 0
        ThemeMode.LIGHT -> 1
        ThemeMode.DARK -> 2
    }

    Column(Modifier.fillMaxSize()) {
        AniTopBar(title = "Appearance", onBack = onBack)
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LivePreview(palette = state.palette, amoled = state.amoled)
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Mode", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SegmentedToggle(
                        options = listOf("System", "Light", "Dark"),
                        selectedIndex = modeIndex,
                        onSelect = { index ->
                            viewModel.setMode(
                                when (index) {
                                    0 -> ThemeMode.SYSTEM
                                    1 -> ThemeMode.LIGHT
                                    else -> ThemeMode.DARK
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Colour theme", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Every palette is checked for readable text contrast in both modes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(palettes) { palette ->
                        PaletteCard(
                            palette = palette,
                            selected = state.palette == palette,
                            onClick = { viewModel.setPalette(palette) },
                        )
                    }
                }
            }

            item {
                AniGroup(Modifier.padding(horizontal = 20.dp)) {
                    SettingSwitchRow(
                        title = "AMOLED black",
                        subtitle = "True black surfaces in dark mode",
                        checked = state.amoled,
                        onCheckedChange = viewModel::setAmoled,
                    )
                    SettingSwitchRow(
                        title = "Reduce motion",
                        subtitle = "Snap transitions, no shimmer, no auto-advance",
                        checked = state.reduceMotion,
                        onCheckedChange = viewModel::setReduceMotion,
                    )
                }
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Accent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (state.accentArgb != null) {
                            TextButton(onClick = { viewModel.setAccent(null) }) {
                                Text("Use palette", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Text(
                        "Recolours Play, the active tab, See all, HD chips and progress bars.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(accents) { accent ->
                        AccentSwatch(
                            color = Color(accent.argb.toInt()),
                            selected = state.accentArgb == accent.argb,
                            description = accent.name,
                            onClick = { viewModel.setAccent(accent.argb) },
                        )
                    }
                    item {
                        AccentSwatch(
                            color = MaterialTheme.colorScheme.primary,
                            selected = false,
                            description = "Custom colour",
                            custom = true,
                            onClick = { showHex = true },
                        )
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "Saved automatically and applied everywhere, including the player and system bars.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showHex) {
        HexDialog(
            initial = state.accentArgb?.let { String.format("#%06X", it and 0xFFFFFF) } ?: "#D9232E",
            onDismiss = { showHex = false },
            onApply = { argb ->
                viewModel.setAccent(argb)
                showHex = false
            },
        )
    }
}

@Composable
private fun LivePreview(palette: ThemePalette, amoled: Boolean) {
    AniVerseTheme(
        settings = Settings(themeMode = ThemeMode.DARK, themePalette = palette, pureBlack = amoled),
        applySystemBars = false,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(64.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), MaterialTheme.colorScheme.surfaceVariant),
                        ),
                    ),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(palette.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Live preview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text("Play", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text("My List", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteCard(palette: ThemePalette, selected: Boolean, onClick: () -> Unit) {
    AniVerseTheme(
        settings = Settings(themeMode = ThemeMode.DARK, themePalette = palette),
        applySystemBars = false,
    ) {
        Column(
            Modifier
                .width(126.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick)
                .padding(8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                    ),
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                palette.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                palette.tagline,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    custom: Boolean = false,
) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (custom) MaterialTheme.colorScheme.surfaceVariant else color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (custom) {
            Icon(Icons.Outlined.Colorize, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        } else if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = description,
                tint = contrastOn(color),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HexDialog(initial: String, onDismiss: () -> Unit, onApply: (Long) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom accent") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    singleLine = true,
                    label = { Text("#RRGGBB") },
                    supportingText = { if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = parseHex(value)
                if (parsed == null) error = "Use a 6-digit hex value, for example #D9232E" else onApply(parsed)
            }) { Text("Apply", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(24.dp),
    )
}

private fun parseHex(raw: String): Long? {
    val clean = raw.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) 0xFF000000L or value else value
}
