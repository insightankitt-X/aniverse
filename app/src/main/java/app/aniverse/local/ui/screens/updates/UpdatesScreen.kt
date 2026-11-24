package app.aniverse.local.ui.screens.updates

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
import app.aniverse.local.domain.model.UpdateFailure
import app.aniverse.local.domain.model.UpdateGroup
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.ui.components.EmptyState
import app.aniverse.local.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    data class State(
        val groups: List<UpdateGroup> = emptyList(),
        val checking: Boolean = false,
        val progress: Float = 0f,
        val failures: List<UpdateFailure> = emptyList(),
        val lastReport: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.observeUpdates().collect { groups ->
                _state.update { it.copy(groups = groups) }
            }
        }
    }

    fun check() {
        if (_state.value.checking) return
        viewModelScope.launch {
            _state.update { it.copy(checking = true, progress = 0f, failures = emptyList(), lastReport = null) }
            val report = libraryRepository.refreshLibrary { done, total ->
                _state.update { it.copy(progress = if (total > 0) done.toFloat() / total else 0f) }
            }
            _state.update {
                it.copy(
                    checking = false,
                    failures = report.failures,
                    lastReport = "Checked ${report.checked} series, found ${report.newEpisodes} new episode(s)",
                )
            }
        }
    }

    fun clear() {
        viewModelScope.launch { libraryRepository.clearUpdates() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onOpenSeries: (Long) -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates") },
                actions = {
                    TextButton(onClick = viewModel::check, enabled = !state.checking) {
                        Text(if (state.checking) "Checking" else "Check now")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.checking) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }
            state.lastReport?.let { report ->
                Text(
                    report,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.failures.isNotEmpty()) {
                SectionHeader(title = "Sources that failed", modifier = Modifier.padding(horizontal = 16.dp))
                state.failures.forEach { failure ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(failure.seriesTitle, style = MaterialTheme.typography.bodyMedium)
                            Text(failure.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Text(failure.sourceName, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            when {
                state.groups.isEmpty() -> EmptyState(
                    title = "No new episodes",
                    message = "Check your library for updates. This also runs automatically every 12 hours.",
                    actionLabel = "Check now",
                    onAction = viewModel::check,
                )
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.groups, key = { it.seriesId }) { group ->
                        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(group.seriesTitle, style = MaterialTheme.typography.titleMedium)
                            group.items.forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.episodeName, style = MaterialTheme.typography.bodyMedium)
                                        Text(item.sourceName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { onOpenSeries(item.seriesId) }) { Text("Open") }
                                }
                            }
                        }
                    }
                    item {
                        Button(onClick = viewModel::clear, modifier = Modifier.fillMaxWidth()) { Text("Mark all as seen") }
                    }
                }
            }
        }
    }
}
