package app.aniverse.local.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aniverse.local.domain.model.Series
import app.aniverse.local.domain.model.SourceRef
import app.aniverse.local.domain.repository.BrowseRepository
import app.aniverse.local.domain.repository.SettingsRepository
import app.aniverse.local.ui.components.AniBottomSheet
import app.aniverse.local.ui.components.AniTopBar
import app.aniverse.local.ui.components.EmptyState
import app.aniverse.local.ui.components.GenreChip
import app.aniverse.local.ui.components.PillButton
import app.aniverse.local.ui.components.PillStyle
import app.aniverse.local.ui.components.PosterGrid
import app.aniverse.local.ui.components.SectionHeader
import app.aniverse.local.ui.components.SegmentedToggle
import app.aniverse.source.api.FilterList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

enum class SearchSort { RELEVANCE, TITLE, SOURCE }

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
        val genre: String? = null,
        val sort: SearchSort = SearchSort.RELEVANCE,
        val showFilters: Boolean = false,
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
        viewModelScope.launch { settingsRepository.edit { it.copy(searchHistory = emptyList()) } }
    }

    fun setGenre(genre: String?) = _state.update { it.copy(genre = genre) }

    fun setSort(sort: SearchSort) = _state.update { it.copy(sort = sort) }

    fun showFilters() = _state.update { it.copy(showFilters = true) }

    fun dismissFilters() = _state.update { it.copy(showFilters = false) }

    fun resetFilters() = _state.update { it.copy(genre = null, sort = SearchSort.RELEVANCE) }

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
        // Sources are independent network calls. Three at a time keeps search fast without
        // flooding the extension process the way one call per source would.
        val permits = Semaphore(3)
        coroutineScope {
            sources.forEach { source ->
                launch {
                    permits.withPermit { searchSource(source, query) }
                }
            }
        }
        remember(query)
    }

    private suspend fun searchSource(source: SourceRef, query: String) {
        if (_state.value.query.trim() != query) return
        runCatching {
            browseRepository.search(source.pkg, source.sourceId, query, FilterList.Empty, 1).getOrThrow()
        }.fold(
            onSuccess = { page ->
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
            },
            onFailure = { error ->
                _state.update { current ->
                    current.copy(
                        completed = current.completed + 1,
                        results = current.results.map { result ->
                            if (result.ref.sourceId == source.sourceId) {
                                result.copy(loading = false, error = error.message ?: "Search failed", finished = true)
                            } else {
                                result
                            }
                        },
                    )
                }
            },
        )
    }

    private suspend fun remember(query: String) {
        val hasResults = _state.value.results.any { it.items.isNotEmpty() }
        if (!hasResults) return
        settingsRepository.edit { settings ->
            settings.copy(
                searchHistory = (listOf(query) + settings.searchHistory.filterNot { it.equals(query, true) }).take(8),
            )
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}

@Composable
fun SearchScreen(
    onOpenSeries: (Series) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failures = state.results.count { it.error != null }
    val all = remember(state.results) {
        state.results.flatMap { it.items }.distinctBy { it.key }
    }
    val genres = remember(all) {
        all.flatMap { it.genres }.distinct().sorted().take(24)
    }
    val filtered = remember(all, state.genre, state.sort) {
        val filteredByGenre = if (state.genre == null) all else all.filter { state.genre in it.genres }
        when (state.sort) {
            SearchSort.RELEVANCE -> filteredByGenre
            SearchSort.TITLE -> filteredByGenre.sortedBy { it.title.lowercase() }
            SearchSort.SOURCE -> filteredByGenre.sortedWith(compareBy({ it.sourcePkg }, { it.title.lowercase() }))
        }
    }

    Column(Modifier.fillMaxSize()) {
        AniTopBar(title = "Search", onBack = onBack)

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Search every installed source") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                    IconButton(onClick = viewModel::showFilters, enabled = state.query.isNotBlank()) {
                        Icon(
                            Icons.Outlined.FilterList,
                            contentDescription = "Filters",
                            tint = if (state.genre != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        )

        if (state.query.isBlank()) {
            BeforeSearch(
                recent = state.recent,
                onUse = viewModel::useRecent,
                onClear = viewModel::clearRecent,
            )
            return
        }

        if (state.searchStarted) {
            if (state.completed < state.total) {
                LinearProgressIndicator(
                    progress = { if (state.total == 0) 0f else state.completed.toFloat() / state.total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.completed < state.total) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Searching ${state.completed} of ${state.total} sources",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "${filtered.size} result(s) across ${state.results.count { it.items.isNotEmpty() }} source(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (failures > 0) {
                        Text(
                            " · $failures failed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.genre != null) {
                    Text(
                        state.genre.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { viewModel.setGenre(null) },
                    )
                }
            }
        }

        when {
            state.searchStarted && state.completed >= state.total && filtered.isEmpty() -> EmptyState(
                modifier = Modifier.weight(1f),
                title = "No matches",
                message = "No enabled source returned anything for \"${state.query}\". Try a shorter title or another spelling.",
            )

            filtered.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
            }

            else -> PosterGrid(
                items = filtered,
                onItem = onOpenSeries,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (state.showFilters) {
        AniBottomSheet(onDismiss = viewModel::dismissFilters, title = "Filters") {
            Text("Genre", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (genres.isEmpty()) {
                Text(
                    "Genres appear once the sources return results.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Genres(genres = genres, selected = state.genre, onSelect = viewModel::setGenre)
            }
            Spacer(Modifier.height(18.dp))
            Text("Sort", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SegmentedToggle(
                options = listOf("Relevance", "Title", "Source"),
                selectedIndex = state.sort.ordinal,
                onSelect = { viewModel.setSort(SearchSort.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Year, season and status filters are hidden because AniVerse's source API does not expose them.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Reset", viewModel::resetFilters, style = PillStyle.Outlined, modifier = Modifier.weight(1f))
                PillButton("Apply", viewModel::dismissFilters, style = PillStyle.Filled, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Genres(genres: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GenreChip(text = "All", selected = selected == null, onClick = { onSelect(null) })
        genres.forEach { genre ->
            GenreChip(text = genre, selected = selected == genre, onClick = { onSelect(genre) })
        }
    }
}

@Composable
private fun BeforeSearch(
    recent: List<String>,
    onUse: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (recent.isEmpty()) {
        EmptyState(
            title = "Find your next series",
            message = "Search across every installed source at once. Nothing about your searches leaves this device.",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            SectionHeader(
                title = "Top searches",
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        items(recent.take(3).size) { index ->
            val query = recent[index]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onUse(query) }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.width(30.dp),
                )
                Text(query, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        item {
            SectionHeader(
                title = "Recent searches",
                actionLabel = "Clear all",
                onAction = onClear,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recent) { query ->
                    GenreChip(text = query, onClick = { onUse(query) })
                }
            }
        }
        item {
            Text(
                "Suggestions come only from searches you have already made on this device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }
    }
}
