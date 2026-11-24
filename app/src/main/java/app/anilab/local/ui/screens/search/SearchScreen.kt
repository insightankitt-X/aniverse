package app.anilab.local.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import app.anilab.local.domain.model.Series
import app.anilab.local.domain.model.SourceRef
import app.anilab.local.domain.repository.BrowseRepository
import app.anilab.local.domain.repository.SettingsRepository
import app.anilab.local.ui.components.EmptyState
import app.anilab.local.ui.components.PosterCard
import app.anilab.local.ui.components.SectionHeader
import app.anilab.source.api.FilterList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val browseRepository: BrowseRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    data class SourceResult(
        val ref: SourceRef,
        val loading: Boolean = false,
        val items: List<Series> = emptyList(),
        val error: String? = null,
        val finished: Boolean = false,
    )

    data class State(
        val query: String = "",
        val recent: List<String> = emptyList(),
        val results: List<SourceResult> = emptyList(),
        val completed: Int = 0,
        val total: Int = 0,
        val searchStarted: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(recent = settings.searchHistory) }
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.update { it.copy(results = emptyList(), completed = 0, total = 0, searchStarted = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            search(value.trim())
        }
    }

    fun useRecent(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { search(query) }
    }

    fun clearRecent() {
        viewModelScope.launch {
            settingsRepository.edit { it.copy(searchHistory = emptyList()) }
        }
    }

    private suspend fun search(query: String) {
        val sources = runCatching { browseRepository.observeSources().first() }.getOrDefault(emptyList())
        _state.update {
            it.copy(
                searchStarted = true,
                total = sources.size,
                completed = 0,
                results = sources.map { source -> SourceResult(source, loading = true) },
            )
        }
        for (source in sources) {
            if (_state.value.query.trim() != query) return
            try {
                val page = browseRepository.search(source.pkg, source.sourceId, query, FilterList.Empty, 1).getOrThrow()
                _state.update { current ->
                    current.copy(
                        completed = current.completed + 1,
                        results = current.results.map { result ->
                            if (result.ref.sourceId == source.sourceId) {
                                result.copy(loading = false, items = page.items, finished = true)
                            } else {
                                result
                            }
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update { current ->
                    current.copy(
                        completed = current.completed + 1,
                        results = current.results.map { result ->
                            if (result.ref.sourceId == source.sourceId) {
                                result.copy(loading = false, error = e.message ?: "Search failed", finished = true)
                            } else {
                                result
                            }
                        },
                    )
                }
            }
        }
        remember(query)
    }

    private suspend fun remember(query: String) {
        val hasResults = _state.value.results.any { it.items.isNotEmpty() }
        if (!hasResults) return
        settingsRepository.edit { settings ->
            settings.copy(searchHistory = (listOf(query) + settings.searchHistory.filterNot { it.equals(query, true) }).take(8))
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenSeries: (Series) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failures = state.results.count { it.error != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search all enabled sources") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.recent.isNotEmpty() && state.query.isBlank()) {
                SectionHeader(
                    title = "Recent searches",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    actionLabel = "Clear",
                    onAction = viewModel::clearRecent,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.recent) { query ->
                        AssistChip(onClick = { viewModel.useRecent(query) }, label = { Text(query) })
                    }
                }
            }

            if (state.searchStarted) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.completed < state.total) {
                        CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Searching ${state.completed} of ${state.total} sources",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    } else {
                        Text(
                            if (failures > 0) "Finished with $failures source(s) failing" else "Finished",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (failures > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val withResults = state.results.filter { it.items.isNotEmpty() || it.loading || it.error != null }
            when {
                state.query.isBlank() -> EmptyState(
                    title = "Search your sources",
                    message = "Results are grouped by source. Nothing about your searches leaves the device.",
                )
                state.searchStarted && withResults.isEmpty() -> EmptyState(
                    title = "No matches",
                    message = "No enabled source returned anything for \"${state.query}\".",
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(withResults, key = { it.ref.sourceId }) { result ->
                        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(result.ref.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (result.loading) CircularProgressIndicator(Modifier.height(14.dp), strokeWidth = 2.dp)
                            }
                            when {
                                result.error != null -> Text(
                                    result.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                result.items.isEmpty() && result.finished -> Text(
                                    "No results",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                else -> LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(result.items, key = { it.key }) { series ->
                                        PosterCard(
                                            title = series.title,
                                            thumbnailUrl = series.thumbnailUrl,
                                            onClick = { onOpenSeries(series) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
