package app.aniverse.local.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aniverse.local.domain.model.PlayerQualityMode
import app.aniverse.local.domain.model.Settings
import app.aniverse.local.domain.model.SubtitleSize
import app.aniverse.local.domain.model.ThemeMode
import app.aniverse.local.domain.repository.BackupPreview
import app.aniverse.local.domain.repository.BackupRepository
import app.aniverse.local.domain.repository.HistoryRepository
import app.aniverse.local.domain.repository.ImportMode
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.domain.repository.SettingsRepository
import app.aniverse.local.ui.components.SectionHeader
import app.aniverse.local.ui.components.SettingSwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val historyRepository: HistoryRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    data class Ui(
        val preview: BackupPreview? = null,
        val previewUri: Uri? = null,
        val report: String? = null,
        val busy: Boolean = false,
        val message: String? = null,
    )

    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _ui = MutableStateFlow(Ui())
    val ui = _ui.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepository.edit(transform) }
    }

    fun export(target: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val result = backupRepository.export(target)
            _ui.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { count -> "Exported $count record(s)" },
                        onFailure = { error -> "Export failed: ${error.message ?: "unknown error"}" },
                    ),
                )
            }
        }
    }

    fun preview(source: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, previewUri = source) }
            val result = backupRepository.preview(source)
            _ui.update {
                it.copy(
                    busy = false,
                    preview = result.getOrNull(),
                    previewUri = if (result.isSuccess) source else null,
                    message = result.exceptionOrNull()?.let { error -> "That file is not an AniVerse backup: ${error.message ?: ""}" },
                )
            }
        }
    }

    fun runImport(mode: ImportMode) {
        val uri = _ui.value.previewUri ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, preview = null) }
            val result = backupRepository.`import`(uri, mode)
            _ui.update {
                it.copy(
                    busy = false,
                    previewUri = null,
                    report = result.fold(
                        onSuccess = { report ->
                            val errors = if (report.errors.isEmpty()) "" else " ${report.errors.size} item(s) were skipped."
                            "Imported ${report.series} series, ${report.categories} categories, ${report.progress} progress entries, " +
                                "${report.history} history entries, ${report.repositories} repositories and ${report.extensions} extensions." + errors
                        },
                        onFailure = { error -> "Import failed: ${error.message ?: "unknown error"}" },
                    ),
                )
            }
        }
    }

    fun dismissPreview() = _ui.update { it.copy(preview = null, previewUri = null) }

    fun dismissReport() = _ui.update { it.copy(report = null) }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
            _ui.update { it.copy(message = "Watch history cleared") }
        }
    }

    fun clearUpdates() {
        viewModelScope.launch {
            libraryRepository.clearUpdates()
            _ui.update { it.copy(message = "Update list cleared") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenRepositories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmClearHistory by remember { mutableStateOf(false) }

    LaunchedEffect(ui.message) {
        ui.message?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.update { it.copy(downloadTreeUri = uri.toString()) }
        }
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.preview(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader("Appearance", Modifier.padding(horizontal = 16.dp)) }
            item {
                ChipRow(
                    label = "Theme",
                    options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                    selected = settings.themeMode,
                    onSelect = { mode -> viewModel.update { it.copy(themeMode = mode) } },
                )
            }
            item {
                SettingSwitchRow(
                    title = "Pure black",
                    subtitle = "Use true black backgrounds in dark mode",
                    checked = settings.pureBlack,
                    onCheckedChange = { value -> viewModel.update { it.copy(pureBlack = value) } },
                )
            }
            item {
                SettingSwitchRow(
                    title = "Dynamic colour",
                    subtitle = "Follow the system wallpaper palette (Android 12+)",
                    checked = settings.dynamicColor,
                    onCheckedChange = { value -> viewModel.update { it.copy(dynamicColor = value) } },
                )
            }
            item {
                SettingSwitchRow(
                    title = "Reduce motion",
                    subtitle = "Disable shimmering placeholders and long animations",
                    checked = settings.reduceMotion,
                    onCheckedChange = { value -> viewModel.update { it.copy(reduceMotion = value) } },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Library", Modifier.padding(horizontal = 16.dp)) }
            item {
                ChipRow(
                    label = "Check for updates every",
                    options = listOf(6 to "6 hours", 12 to "12 hours", 24 to "24 hours", 48 to "48 hours"),
                    selected = settings.updateIntervalHours,
                    onSelect = { hours -> viewModel.update { it.copy(updateIntervalHours = hours) } },
                )
            }
            item {
                SettingSwitchRow(
                    title = "Updates on Wi-Fi only",
                    subtitle = "Library checks wait for an unmetered connection",
                    checked = settings.wifiOnlyUpdates,
                    onCheckedChange = { value -> viewModel.update { it.copy(wifiOnlyUpdates = value) } },
                )
            }
            item {
                SettingSwitchRow(
                    title = "Show NSFW sources",
                    subtitle = "Hidden sources stay installed but are not listed",
                    checked = settings.showNsfw,
                    onCheckedChange = { value -> viewModel.update { it.copy(showNsfw = value) } },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Playback", Modifier.padding(horizontal = 16.dp)) }
            item {
                ChipRow(
                    label = "Quality",
                    options = listOf(
                        PlayerQualityMode.AUTO to "Automatic",
                        PlayerQualityMode.MANUAL to "Preferred",
                    ),
                    selected = settings.playerQualityMode,
                    onSelect = { mode -> viewModel.update { it.copy(playerQualityMode = mode) } },
                )
            }
            if (settings.playerQualityMode == PlayerQualityMode.MANUAL) {
                item {
                    Text(
                        "Preferred quality: ${settings.playerPreferredQuality ?: "not chosen yet - pick one in the player"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                ChipRow(
                    label = "Default speed",
                    options = listOf(0.75f to "0.75x", 1f to "1x", 1.25f to "1.25x", 1.5f to "1.5x"),
                    selected = settings.playerSpeed,
                    onSelect = { speed -> viewModel.update { it.copy(playerSpeed = speed) } },
                )
            }
            item {
                ChipRow(
                    label = "Subtitle size",
                    options = listOf(
                        SubtitleSize.SMALL to "Small",
                        SubtitleSize.MEDIUM to "Medium",
                        SubtitleSize.LARGE to "Large",
                    ),
                    selected = settings.subtitleSize,
                    onSelect = { size -> viewModel.update { it.copy(subtitleSize = size) } },
                )
            }
            item {
                SettingSwitchRow(
                    title = "Subtitle background",
                    subtitle = "Draw a dark box behind subtitles for readability",
                    checked = settings.subtitleBackground,
                    onCheckedChange = { value -> viewModel.update { it.copy(subtitleBackground = value) } },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Downloads", Modifier.padding(horizontal = 16.dp)) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Storage folder", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (settings.downloadTreeUri != null) "Custom folder selected" else "App storage (no folder chosen)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { treePicker.launch(null) }) { Text("Choose folder") }
                        if (settings.downloadTreeUri != null) {
                            TextButton(onClick = { viewModel.update { it.copy(downloadTreeUri = null) } }) { Text("Use app storage") }
                        }
                    }
                }
            }
            item {
                ChipRow(
                    label = "Parallel downloads",
                    options = listOf(1 to "1", 2 to "2", 3 to "3"),
                    selected = settings.downloadParallelLimit,
                    onSelect = { limit -> viewModel.update { it.copy(downloadParallelLimit = limit) } },
                )
            }
            item {
                SettingSwitchRow(
                    title = "Download on Wi-Fi only",
                    subtitle = "Queued episodes wait for an unmetered connection",
                    checked = settings.downloadWifiOnly,
                    onCheckedChange = { value -> viewModel.update { it.copy(downloadWifiOnly = value) } },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Extensions", Modifier.padding(horizontal = 16.dp)) }
            item { ActionRow("Manage extensions", "Install, update or disable sources", onOpenExtensions) }
            item { ActionRow("Repositories", "Add, trust or refresh extension indexes", onOpenRepositories) }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Backup", Modifier.padding(horizontal = 16.dp)) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Backups contain repositories, extensions, your library, categories, progress and history. " +
                            "They never contain APK files or downloaded media.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { createBackup.launch("aniverse-backup.json") },
                            enabled = !ui.busy,
                        ) { Text("Export") }
                        OutlinedButton(
                            onClick = { openBackup.launch(arrayOf("application/json", "text/plain")) },
                            enabled = !ui.busy,
                        ) { Text("Import") }
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Data", Modifier.padding(horizontal = 16.dp)) }
            item {
                ActionRow("Clear watch history", "Keeps your library and progress", { confirmClearHistory = true })
            }
            item {
                ActionRow("Clear update list", "Marks every new episode as seen", viewModel::clearUpdates)
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("About", Modifier.padding(horizontal = 16.dp)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("AniVerse", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "No account, no backend, no analytics and no bundled sources. Every series you see comes from an " +
                            "extension you installed yourself, and all state stays in this app's private storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Extensions are third-party code. Only install repositories you trust.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    ui.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPreview,
            title = { Text("Import this backup?") },
            text = {
                Text(
                    "${preview.series} series, ${preview.categories} categories, ${preview.progress} progress entries, " +
                        "${preview.history} history entries, ${preview.repositories} repositories and " +
                        "${preview.extensions} extensions.\n\nMerge keeps what is already on this device; replace clears it first.",
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.runImport(ImportMode.REPLACE) }) { Text("Replace") }
                    Button(onClick = { viewModel.runImport(ImportMode.MERGE) }) { Text("Merge") }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissPreview) { Text("Cancel") } },
        )
    }

    ui.report?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReport,
            title = { Text("Import finished") },
            text = { Text(report) },
            confirmButton = { Button(onClick = viewModel::dismissReport) { Text("OK") } },
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear watch history?") },
            text = { Text("History and the Continue watching row are removed. Progress and your library stay.") },
            confirmButton = {
                Button(onClick = { viewModel.clearHistory(); confirmClearHistory = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun <T> ChipRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                )
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick, modifier = Modifier.padding(top = 2.dp)) { Text("Open") }
    }
}
