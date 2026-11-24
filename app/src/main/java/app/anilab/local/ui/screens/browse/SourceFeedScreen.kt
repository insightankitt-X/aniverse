package app.anilab.local.ui.screens.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.anilab.local.domain.model.Series
import app.anilab.local.domain.repository.BrowseRepository
import app.anilab.local.ui.components.EmptyState
import app.anilab.local.ui.components.ErrorState
import app.anilab.local.ui.components.PosterCard
import app.anilab.local.ui.components.ShimmerBox
import app.anilab.source.api.Filter
import app.anilab.source.api.FilterList
import app.anilab.source.api.TriState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourceFeedViewModel @Inject constructor(
    private val browseRepository: BrowseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    enum class Tab { POPULAR, LATEST }

    data class State(
        val tab: Tab = Tab.POPULAR,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val items: List<Series> = emptyList(),
        val page: Int = 0,
        val endReached: Boolean = false,
        val error: String? = null,
        val filters: FilterList = FilterList.Empty,
        val appliedFilters: FilterList = FilterList.Empty,
        val showFilters: Boolean = false,
        val filtersLoading: Boolean = false,
        val query: String = "",
    )

    private val pkg: String = savedStateHandle.get<String>("pkg").orEmpty()
    private val sourceId: Long = savedStateHandle.get<String>("sourceId")?.toLongOrNull() ?: 0L
    val sourceName: String = savedStateHandle.get<String>("name").orEmpty().ifBlank { "Source" }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        reload()
    }

    fun selectTab(tab: Tab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab) }
        reload()
    }

    fun retry() = reload()

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun submitQuery() {
        reload()
    }

    fun showFilters() {
        _state.update { it.copy(showFilters = true) }
        if (_state.value.filters.filters.isEmpty() && !_state.value.filtersLoading) {
            viewModelScope.launch {
                _state.update { it.copy(filtersLoading = true) }
                val filters = browseRepository.filters(pkg, sourceId).getOrNull() ?: FilterList.Empty
                _state.update { it.copy(filters = filters, filtersLoading = false) }
            }
        }
    }

    fun dismissFilters() {
        _state.update { it.copy(showFilters = false) }
    }

    fun updateFilter(updated: Filter) {
        _state.update { it.copy(filters = it.filters.replace(updated)) }
    }

    fun clearFilters() {
        _state.update { it.copy(filters = it.filters.cleared()) }
    }

    fun applyFilters() {
        _state.update { it.copy(appliedFilters = it.filters, showFilters = false) }
        reload()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached) return
        loadPage(current.page + 1, reset = false)
    }

    private fun reload() {
        _state.update { it.copy(loading = true, error = null, items = emptyList(), page = 0, endReached = false) }
        loadPage(1, reset = true)
    }

    private fun loadPage(page: Int, reset: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = !reset, loading = reset || it.items.isEmpty()) }
            val current = _state.value
            val filtering = current.appliedFilters.activeSummary().isNotEmpty() || current.query.isNotBlank()
            val result = if (filtering) {
                browseRepository.search(pkg, sourceId, current.query, current.appliedFilters, page)
            } else {
                when (current.tab) {
                    Tab.POPULAR -> browseRepository.popular(pkg, sourceId, page)
                    Tab.LATEST -> browseRepository.latest(pkg, sourceId, page)
                }
            }
            result.fold(
                onSuccess = { sourcePage ->
                    _state.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            items = if (reset) sourcePage.items else it.items + sourcePage.items,
                            page = page,
                            endReached = !sourcePage.hasNextPage,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            error = if (it.items.isEmpty()) error.message ?: "Couldn't load this source" else null,
                            endReached = true,
                        )
                    }
                },
            )
        }
    }

    private fun FilterList.replace(updated: Filter): FilterList = copy(
        filters = filters.map { filter ->
            when {
                filter.key == updated.key -> updated
                filter is Filter.Group -> filter.copy(filters = FilterList(filter.filters).replace(updated).filters)
                else -> filter
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceFeedScreen(
    onOpenSeries: (Series) -> Unit,
    onBack: () -> Unit,
    viewModel: SourceFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, state.endReached, state.loading) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= state.items.size - 4 && state.items.isNotEmpty()) viewModel.loadMore()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.sourceName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::showFilters) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
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
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(selected = state.tab == SourceFeedViewModel.Tab.POPULAR, onClick = { viewModel.selectTab(SourceFeedViewModel.Tab.POPULAR) }, text = { Text("Popular") })
                Tab(selected = state.tab == SourceFeedViewModel.Tab.LATEST, onClick = { viewModel.selectTab(SourceFeedViewModel.Tab.LATEST) }, text = { Text("Latest") })
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                singleLine = true,
                label = { Text("Search in ${viewModel.sourceName}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val chips = state.appliedFilters.activeSummary()
            if (chips.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.take(3).forEach { chip -> Text(chip, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = { viewModel.clearFilters(); viewModel.applyFilters() }) { Text("Clear") }
                }
            }

            when {
                state.loading && state.items.isEmpty() -> FeedSkeleton()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error ?: "", onRetry = viewModel::retry)
                state.items.isEmpty() -> EmptyState(
                    title = "Nothing here",
                    message = "This source returned no results for the current tab or filters.",
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.items, key = { it.key }) { series ->
                        PosterCard(
                            title = series.title,
                            thumbnailUrl = series.thumbnailUrl,
                            onClick = { onOpenSeries(series) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(Modifier.height(24.dp))
                            }
                        }
                    }
                    if (state.endReached && state.items.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "You've reached the end",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showFilters) {
        ModalBottomSheet(onDismissRequest = viewModel::dismissFilters) {
            FilterSheet(
                filterList = state.filters,
                loading = state.filtersLoading,
                onFilterChange = viewModel::updateFilter,
                onClear = viewModel::clearFilters,
                onApply = viewModel::applyFilters,
            )
        }
    }
}

@Composable
private fun FeedSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(9) { ShimmerBox(Modifier.fillMaxWidth().height(176.dp)) }
    }
}

@Composable
fun FilterSheet(
    filterList: FilterList,
    loading: Boolean,
    onFilterChange: (Filter) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Filters", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (loading) {
            CircularProgressIndicator(Modifier.padding(16.dp))
        } else if (filterList.filters.isEmpty()) {
            Text(
                "This source offers no filters.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            filterList.filters.forEach { filter ->
                FilterControl(filter = filter, onFilterChange = onFilterChange)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClear) { Text("Reset") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onApply) { Text("Apply") }
        }
    }
}

@Composable
private fun FilterControl(filter: Filter, onFilterChange: (Filter) -> Unit) {
    when (filter) {
        is Filter.Header -> Text(
            filter.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )

        is Filter.Separator -> HorizontalDivider(Modifier.padding(vertical = 12.dp))

        is Filter.Text -> OutlinedTextField(
            value = filter.value,
            onValueChange = { onFilterChange(filter.copy(value = it)) },
            label = { Text(filter.title) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )

        is Filter.CheckBox -> Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = filter.state, onCheckedChange = { onFilterChange(filter.copy(state = it)) })
            Text(filter.title, style = MaterialTheme.typography.bodyMedium)
        }

        is Filter.TriState -> Column(Modifier.padding(vertical = 4.dp)) {
            Text(filter.title, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TriState.entries.forEach { state ->
                    FilterChip(
                        selected = filter.state == state,
                        onClick = { onFilterChange(filter.copy(state = state)) },
                        label = { Text(state.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }

        is Filter.Select -> Column(Modifier.padding(vertical = 4.dp)) {
            Text(filter.title, style = MaterialTheme.typography.bodyMedium)
            filter.options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = filter.selectedIndex == index,
                        onClick = { onFilterChange(filter.copy(selectedIndex = index)) },
                    )
                    Text(option.name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        is Filter.Sort -> Column(Modifier.padding(vertical = 4.dp)) {
            Text(filter.title, style = MaterialTheme.typography.bodyMedium)
            filter.options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = filter.selectedIndex == index,
                        onClick = { onFilterChange(filter.copy(selectedIndex = index)) },
                    )
                    Text(option.name, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = filter.ascending, onCheckedChange = { onFilterChange(filter.copy(ascending = it)) })
                Text("Ascending", style = MaterialTheme.typography.bodySmall)
            }
        }

        is Filter.Group -> Column(Modifier.padding(vertical = 4.dp)) {
            Text(filter.title, style = MaterialTheme.typography.titleSmall)
            filter.filters.forEach { nested -> FilterControl(nested, onFilterChange) }
        }
    }
}
