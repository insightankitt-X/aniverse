package app.anilab.local.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.anilab.local.domain.model.ContinueWatchingItem
import app.anilab.local.domain.model.Series
import app.anilab.local.domain.model.SourceRef
import app.anilab.local.domain.model.UpdateItem
import app.anilab.local.domain.repository.BrowseRepository
import app.anilab.local.domain.repository.ConnectivityRepository
import app.anilab.local.domain.repository.HistoryRepository
import app.anilab.local.domain.repository.LibraryRepository
import app.anilab.local.ui.components.EmptyState
import app.anilab.local.ui.components.ErrorState
import app.anilab.local.ui.components.OfflineNotice
import app.anilab.local.ui.components.PosterCard
import app.anilab.local.ui.components.SectionHeader
import app.anilab.local.ui.components.ShimmerBox
import app.anilab.local.ui.components.formatRemaining
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val historyRepository: HistoryRepository,
    private val browseRepository: BrowseRepository,
    connectivity: ConnectivityRepository,
) : ViewModel() {

    sealed interface RowState {
        data object Loading : RowState
        data class Content(val items: List<Series>, val staleWarning: String? = null) : RowState
        data class Failed(val message: String) : RowState
    }

    data class SourceRow(val ref: SourceRef, val state: RowState)

    data class State(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val online: Boolean = true,
        val sources: List<SourceRef> = emptyList(),
        val continueWatching: List<ContinueWatchingItem> = emptyList(),
        val recentlyAdded: List<Series> = emptyList(),
        val newEpisodes: List<UpdateItem> = emptyList(),
        val rows: List<SourceRow> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val rowCache = mutableMapOf<Long, Pair<Long, List<Series>>>()

    init {
        viewModelScope.launch {
            historyRepository.observeContinueWatching().collect { items ->
                _state.update { it.copy(continueWatching = items) }
            }
        }
        viewModelScope.launch {
            libraryRepository.observeRecentlyAdded().collect { items ->
                _state.update { it.copy(recentlyAdded = items) }
            }
        }
        viewModelScope.launch {
            historyRepository.observeNewEpisodes().collect { items ->
                _state.update { it.copy(newEpisodes = items) }
            }
        }
        viewModelScope.launch {
            connectivity.online.collect { online -> _state.update { it.copy(online = online) } }
        }
        viewModelScope.launch {
            browseRepository.observeSources().collect { sources ->
                _state.update { it.copy(sources = sources, loading = false) }
                loadRows(sources, force = false)
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            loadRows(_state.value.sources, force = true)
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun retryRow(ref: SourceRef) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(rows = current.rows.map { if (it.ref.sourceId == ref.sourceId) it.copy(state = RowState.Loading) else it })
            }
            loadRow(ref, force = true)
        }
    }

    private suspend fun loadRows(sources: List<SourceRef>, force: Boolean) {
        if (sources.isEmpty()) {
            _state.update { it.copy(rows = emptyList()) }
            return
        }
        _state.update { current ->
            current.copy(rows = sources.map { ref ->
                current.rows.firstOrNull { it.ref.sourceId == ref.sourceId } ?: SourceRow(ref, RowState.Loading)
            })
        }
        supervisorScope {
            sources.forEach { ref -> launch { loadRow(ref, force) } }
        }
    }

    private suspend fun loadRow(ref: SourceRef, force: Boolean) {
        val cached = rowCache[ref.sourceId]
        if (!force && cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
            setRow(ref, RowState.Content(cached.second))
            return
        }
        val result = browseRepository.popular(ref.pkg, ref.sourceId, page = 1)
        val page = result.getOrNull()
        if (page == null) {
            val message = result.exceptionOrNull()?.message ?: "Couldn't load this source"
            if (cached != null) {
                setRow(ref, RowState.Content(cached.second, staleWarning = message))
            } else {
                setRow(ref, RowState.Failed(message))
            }
            return
        }
        rowCache[ref.sourceId] = System.currentTimeMillis() to page.items
        setRow(ref, RowState.Content(page.items))
    }

    private fun setRow(ref: SourceRef, rowState: RowState) {
        _state.update { current ->
            current.copy(rows = current.rows.map { row -> if (row.ref.sourceId == ref.sourceId) row.copy(state = rowState) else row })
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 15 * 60 * 1000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSeries: (Series) -> Unit,
    onOpenSource: (SourceRef) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenExtensions: () -> Unit,
    onPlay: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading -> HomeSkeleton()
            state.sources.isEmpty() -> EmptyState(
                title = "No sources yet",
                message = "Add an extension repository, then install an extension to start browsing.",
                actionLabel = "Open extensions",
                onAction = onOpenExtensions,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "AniLab Local",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }

                if (!state.online) {
                    item { OfflineNotice(onRetry = viewModel::refresh, detail = "Showing what is stored on this device") }
                }

                if (state.continueWatching.isNotEmpty()) {
                    item { SectionHeader("Continue watching", modifier = Modifier.padding(horizontal = 16.dp)) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.continueWatching, key = { it.episode.id }) { item ->
                                Column(Modifier.width(132.dp)) {
                                    PosterCard(
                                        title = item.series.title,
                                        thumbnailUrl = item.series.thumbnailUrl,
                                        progress = item.progress.fraction,
                                        onClick = { onPlay(item.episode.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = item.episode.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = formatRemaining(item.progress.positionMs, item.progress.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.recentlyAdded.isNotEmpty()) {
                    item { SectionHeader("Recently added", modifier = Modifier.padding(horizontal = 16.dp)) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.recentlyAdded, key = { it.id }) { series ->
                                PosterCard(
                                    title = series.title,
                                    thumbnailUrl = series.thumbnailUrl,
                                    onClick = { onOpenSeries(series) },
                                )
                            }
                        }
                    }
                }

                if (state.newEpisodes.isNotEmpty()) {
                    item { SectionHeader("New episodes", modifier = Modifier.padding(horizontal = 16.dp)) }
                    items(state.newEpisodes.take(8), key = { it.episodeId }) { episode ->
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(episode.seriesTitle, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${episode.episodeName} · ${episode.sourceName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(state.rows, key = { it.ref.sourceId }) { row ->
                    SourceRowSection(
                        row = row,
                        onOpenSource = { onOpenSource(row.ref) },
                        onOpenSeries = onOpenSeries,
                        onRetry = { viewModel.retryRow(row.ref) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRowSection(
    row: HomeViewModel.SourceRow,
    onOpenSource: () -> Unit,
    onOpenSeries: (Series) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Popular on ${row.ref.name}",
            modifier = Modifier.padding(horizontal = 16.dp),
            actionLabel = "See all",
            onAction = onOpenSource,
        )
        when (val rowState = row.state) {
            HomeViewModel.RowState.Loading -> Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    ShimmerBox(Modifier.width(132.dp).height(198.dp))
                }
            }

            is HomeViewModel.RowState.Failed -> Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    rowState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry, modifier = Modifier.height(48.dp)) { Text("Retry") }
            }

            is HomeViewModel.RowState.Content -> Column {
                if (rowState.staleWarning != null) {
                    Text(
                        "Couldn't refresh - showing cached",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (rowState.items.isEmpty()) {
                    Text(
                        "This source returned nothing right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(rowState.items, key = { it.key }) { series ->
                            PosterCard(
                                title = series.title,
                                thumbnailUrl = series.thumbnailUrl,
                                badge = series.sourcePkg.substringAfterLast('.').take(1).uppercase().ifBlank { null },
                                onClick = { onOpenSeries(series) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ShimmerBox(Modifier.width(160.dp).height(28.dp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { ShimmerBox(Modifier.width(132.dp).height(198.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { ShimmerBox(Modifier.width(132.dp).height(198.dp)) }
        }
    }
}
