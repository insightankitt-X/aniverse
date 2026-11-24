package app.anilab.local.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.anilab.local.domain.model.DownloadGroup
import app.anilab.local.domain.model.DownloadItem
import app.anilab.local.domain.model.DownloadState
import app.anilab.local.domain.model.StorageSummary
import app.anilab.local.domain.repository.DownloadRepository
import app.anilab.local.ui.components.Badge
import app.anilab.local.ui.components.EmptyState
import app.anilab.local.ui.components.formatBytes
import app.anilab.local.ui.components.formatProgressPercent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val groups = downloadRepository.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<DownloadGroup>())

    val storage = downloadRepository.observeStorage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageSummary(0L, 0L, 0))

    init {
        viewModelScope.launch { runCatching { downloadRepository.reconcile() } }
    }

    fun pause(item: DownloadItem) = viewModelScope.launch { downloadRepository.pause(item.episodeId) }

    fun resume(item: DownloadItem) = viewModelScope.launch { downloadRepository.resume(item.episodeId) }

    fun remove(item: DownloadItem, deleteFile: Boolean) =
        viewModelScope.launch { downloadRepository.remove(item.episodeId, deleteFile) }

    fun clearAll() = viewModelScope.launch { downloadRepository.clearAll() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    TextButton(onClick = viewModel::clearAll, enabled = groups.isNotEmpty()) { Text("Clear all") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${storage.episodeCount} downloaded episode(s)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatBytes(storage.usedBytes)} used · ${formatBytes(storage.freeBytes)} free",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (groups.isEmpty()) {
                EmptyState(
                    title = "No downloads",
                    message = "Download an episode from a series to watch it offline.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(groups, key = { it.seriesId }) { group ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(group.seriesTitle, style = MaterialTheme.typography.titleMedium)
                            group.items.forEach { item ->
                                DownloadRow(
                                    item = item,
                                    onPause = { viewModel.pause(item) },
                                    onResume = { viewModel.resume(item) },
                                    onRemove = { deleteFile -> viewModel.remove(item, deleteFile) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.episodeName, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Badge(item.state.name.lowercase().replaceFirstChar { it.uppercase() })
                    when (item.state) {
                        DownloadState.DOWNLOADING -> Text(formatProgressPercent(item.progress), style = MaterialTheme.typography.labelSmall)
                        DownloadState.COMPLETED -> Text(formatBytes(item.totalBytes), style = MaterialTheme.typography.labelSmall)
                        else -> item.errorMessage?.let { message ->
                            Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            when (item.state) {
                DownloadState.QUEUED, DownloadState.DOWNLOADING -> TextButton(onClick = onPause) { Text("Pause") }
                DownloadState.PAUSED, DownloadState.FAILED, DownloadState.MISSING -> TextButton(onClick = onResume) { Text("Resume") }
                DownloadState.COMPLETED -> Button(onClick = { onRemove(true) }) { Text("Delete") }
            }
        }
        if (item.state == DownloadState.DOWNLOADING) {
            LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}
