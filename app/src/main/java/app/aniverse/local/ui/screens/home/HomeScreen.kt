package app.aniverse.local.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aniverse.local.domain.model.ContinueWatchingItem
import app.aniverse.local.domain.model.Series
import app.aniverse.local.domain.model.SourceRef
import app.aniverse.local.domain.model.UpdateItem
import app.aniverse.local.domain.repository.BrowseRepository
import app.aniverse.local.domain.repository.ConnectivityRepository
import app.aniverse.local.domain.repository.HistoryRepository
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.ui.components.AniTopBar
import app.aniverse.local.ui.components.ContinueWatchingCard
import app.aniverse.local.ui.components.EmptyState
import app.aniverse.local.ui.components.GenreLine
import app.aniverse.local.ui.components.HeroBanner
import app.aniverse.local.ui.components.HeroItem
import app.aniverse.local.ui.components.HomeSkeleton
import app.aniverse.local.ui.components.OfflineNotice
import app.aniverse.local.ui.components.PosterRow
import app.aniverse.local.ui.components.Reveal
import app.aniverse.local.ui.components.SectionHeader
import app.aniverse.local.ui.components.ShimmerBox
import app.aniverse.local.ui.components.formatRelativeTime
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
        val heroInList: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    // Keyed by package + source id: two repositories can use the same numeric ids.
    private val rowCache = mutableMapOf<String, Pair<Long, List<Series>>>()
    private var membershipJob: Job? = null

    // Each row is an extension call; starting dozens at once saturates the extension process.
    private val loadPermits = Semaphore(MAX_PARALLEL_ROWS)

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
                current.copy(rows = current.rows.map { if (it.ref.key == ref.key) it.copy(state = RowState.Loading) else it })
            }
            loadRow(ref, force = true)
            refreshMembership()
        }
    }

    /** Optimistically toggles the hero's library state and reconciles with the repository. */
    fun toggleHeroList(series: Series) {
        viewModelScope.launch {
            val nowInLibrary = runCatching { libraryRepository.toggleLibrary(series) }
                .getOrElse { series.key !in _state.value.heroInList }
            _state.update { current ->
                current.copy(
                    heroInList = if (nowInLibrary) {
                        current.heroInList + series.key
                    } else {
                        current.heroInList - series.key
                    },
                )
            }
        }
    }

    fun removeContinue(episodeId: Long) {
        viewModelScope.launch { runCatching { historyRepository.deleteEntry(episodeId) } }
    }

    private suspend fun loadRows(sources: List<SourceRef>, force: Boolean) {
        if (sources.isEmpty()) {
            _state.update { it.copy(rows = emptyList()) }
            return
        }
        _state.update { current ->
            current.copy(
                rows = sources.map { ref ->
                    current.rows.firstOrNull { it.ref.key == ref.key } ?: SourceRow(ref, RowState.Loading)
                },
            )
        }
        supervisorScope {
            sources.forEach { ref ->
                launch { loadPermits.withPermit { loadRow(ref, force) } }
            }
        }
        refreshMembership()
    }

    private suspend fun loadRow(ref: SourceRef, force: Boolean) {
        val cached = rowCache[ref.key]
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
        rowCache[ref.key] = System.currentTimeMillis() to page.items
        setRow(ref, RowState.Content(page.items))
    }

    private fun setRow(ref: SourceRef, rowState: RowState) {
        _state.update { current ->
            current.copy(rows = current.rows.map { row -> if (row.ref.key == ref.key) row.copy(state = rowState) else row })
        }
    }

    private fun refreshMembership() {
        val featured = _state.value.rows
            .mapNotNull { (it.state as? RowState.Content)?.items }
            .firstOrNull { it.isNotEmpty() }
            ?.take(5)
            .orEmpty()
        if (featured.isEmpty()) return
        membershipJob?.cancel()
        membershipJob = viewModelScope.launch {
            val inList = featured.mapNotNull { series ->
                runCatching { libraryRepository.isInLibrary(series) }.getOrDefault(false).let { inside ->
                    if (inside) series.key else null
                }
            }.toSet()
            _state.update { it.copy(heroInList = it.heroInList + inList) }
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 15 * 60 * 1000L
        const val MAX_PARALLEL_ROWS = 3
    }
}

private val SourceRef.key: String get() = "$pkg:$sourceId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSeries: (Series) -> Unit,
    onOpenSeriesById: (Long) -> Unit,
    onOpenSource: (SourceRef) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenExtensions: () -> Unit,
    onPlay: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 16
        }
    }

    val rowItems = state.rows.map { row -> row to (row.state as? HomeViewModel.RowState.Content)?.items.orEmpty() }
    val loadedRows = rowItems.filter { it.second.isNotEmpty() }
    val pendingRows = rowItems.filter { it.second.isEmpty() }
    val featured = loadedRows.firstOrNull()?.second.orEmpty()
    val heroItems = featured.take(5).map { series ->
        HeroItem(
            series = series,
            inList = series.key in state.heroInList || series.inLibrary,
            resumeEpisodeId = state.continueWatching
                .firstOrNull { it.series.key == series.key }
                ?.episode?.id,
        )
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> HomeSkeleton(Modifier.fillMaxSize())

            state.sources.isEmpty() -> Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(96.dp))
                EmptyState(
                    title = "Your screen is waiting",
                    message = "Add an extension repository, then install a source to fill Home with anime.",
                    actionLabel = "Open extensions",
                    onAction = onOpenExtensions,
                )
            }

            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "hero") {
                        if (heroItems.isEmpty()) {
                            ShimmerBox(
                                Modifier
                                    .fillMaxWidth()
                                    .height(470.dp),
                            )
                        } else {
                            HeroBanner(
                                items = heroItems,
                                onPlay = { item ->
                                    val episodeId = item.resumeEpisodeId
                                    if (episodeId != null) onPlay(episodeId) else onOpenSeries(item.series)
                                },
                                onToggleList = { item -> viewModel.toggleHeroList(item.series) },
                            )
                        }
                    }

                    if (!state.online) {
                        item(key = "offline") {
                            OfflineNotice(
                                onRetry = viewModel::refresh,
                                detail = "Showing what is stored on this device",
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    if (state.continueWatching.isNotEmpty()) {
                        item(key = "continueHeader") {
                            SectionHeader(
                                title = "Continue watching",
                                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                            )
                        }
                        item(key = "continueRow") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(
                                    items = state.continueWatching,
                                    key = { _, item -> item.episode.id },
                                    contentType = { _, _ -> "continue" },
                                ) { index, item ->
                                    Reveal(index) {
                                        ContinueWatchingCard(
                                            title = item.series.title,
                                            label = item.episode.name,
                                            thumbnailUrl = item.series.thumbnailUrl,
                                            progress = item.progress.fraction,
                                            onClick = { onPlay(item.episode.id) },
                                            onInfo = { onOpenSeries(item.series) },
                                            onRemove = { viewModel.removeContinue(item.episode.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val topRow = loadedRows.getOrNull(0)
                    if (topRow != null && topRow.second.isNotEmpty()) {
                        item(key = "topAiring") {
                            PosterRow(
                                title = "Top Airing",
                                subtitle = "Popular on ${topRow.first.ref.name}",
                                items = topRow.second.take(12),
                                onItem = onOpenSeries,
                                actionLabel = "See all",
                                onAction = { onOpenSource(topRow.first.ref) },
                                modifier = Modifier.padding(top = 18.dp),
                            )
                        }
                    }

                    if (state.newEpisodes.isNotEmpty()) {
                        item(key = "latestHeader") {
                            SectionHeader(
                                title = "Latest Episodes",
                                subtitle = "From your library",
                                actionLabel = "See all",
                                onAction = onOpenUpdates,
                                modifier = Modifier.padding(top = 22.dp),
                            )
                        }
                        items(
                            items = state.newEpisodes.take(5),
                            key = { "update-${it.episodeId}" },
                            contentType = { "update" },
                        ) { update ->
                            UpdateRow(update = update, onClick = { onOpenSeriesById(update.seriesId) })
                        }
                    }

                    val trendingRow = loadedRows.getOrNull(1)
                    if (trendingRow != null && trendingRow.second.isNotEmpty()) {
                        item(key = "trending") {
                            PosterRow(
                                title = "Trending",
                                subtitle = "What's hot on ${trendingRow.first.ref.name}",
                                items = trendingRow.second.take(12),
                                onItem = onOpenSeries,
                                actionLabel = "See all",
                                onAction = { onOpenSource(trendingRow.first.ref) },
                                modifier = Modifier.padding(top = 22.dp),
                            )
                        }
                    }

                    items(
                        items = loadedRows.drop(2),
                        key = { "row-${it.first.ref.key}" },
                        contentType = { "sourceRow" },
                    ) { (row, items) ->
                        SourceRowSection(
                            row = row,
                            items = items,
                            onOpenSource = { onOpenSource(row.ref) },
                            onOpenSeries = onOpenSeries,
                            onRetry = { viewModel.retryRow(row.ref) },
                            modifier = Modifier.padding(top = 22.dp),
                        )
                    }

                    items(
                        items = pendingRows,
                        key = { "pending-${it.first.ref.key}" },
                        contentType = { "pendingRow" },
                    ) { (row, _) ->
                        SourceRowSection(
                            row = row,
                            items = emptyList(),
                            onOpenSource = { onOpenSource(row.ref) },
                            onOpenSeries = onOpenSeries,
                            onRetry = { viewModel.retryRow(row.ref) },
                            modifier = Modifier.padding(top = 22.dp),
                        )
                    }

                    if (state.recentlyAdded.isNotEmpty()) {
                        item(key = "recentlyAdded") {
                            PosterRow(
                                title = "Recently added to My List",
                                items = state.recentlyAdded.take(12),
                                onItem = onOpenSeries,
                                actionLabel = "See all",
                                onAction = onOpenLibrary,
                                modifier = Modifier.padding(top = 22.dp),
                            )
                        }
                    }

                    if (heroItems.isEmpty() && state.rows.all { it.state is HomeViewModel.RowState.Failed }) {
                        item(key = "allFailed") {
                            Text(
                                text = "Sources could not be reached. Pull down to retry.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }
            }
        }

        HomeTopBar(
            scrolled = scrolled && !state.loading,
            hasUpdates = state.newEpisodes.isNotEmpty(),
            onSearch = onOpenSearch,
            onUpdates = onOpenUpdates,
        )
    }
}

@Composable
private fun HomeTopBar(
    scrolled: Boolean,
    hasUpdates: Boolean,
    onSearch: () -> Unit,
    onUpdates: () -> Unit,
) {
    val foreground = if (scrolled) MaterialTheme.colorScheme.onSurface else Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (scrolled) MaterialTheme.colorScheme.background else Color.Transparent,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = "AniVerse",
                style = MaterialTheme.typography.titleLarge,
                color = foreground,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "Search", tint = foreground, modifier = Modifier.size(23.dp))
            }
            Box {
                IconButton(onClick = onUpdates) {
                    Icon(
                        Icons.Outlined.NotificationsNone,
                        contentDescription = "Notifications",
                        tint = foreground,
                        modifier = Modifier.size(23.dp),
                    )
                }
                if (hasUpdates) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 9.dp)
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        if (scrolled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun SourceRowSection(
    row: HomeViewModel.SourceRow,
    items: List<Series>,
    onOpenSource: () -> Unit,
    onOpenSeries: (Series) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        when (val rowState = row.state) {
            HomeViewModel.RowState.Loading -> {
                SectionHeader(title = "Popular on ${row.ref.name}")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) {
                        ShimmerBox(Modifier.width(132.dp).height(198.dp))
                    }
                }
            }

            is HomeViewModel.RowState.Failed -> {
                SectionHeader(title = "Popular on ${row.ref.name}")
                Text(
                    rowState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                androidx.compose.material3.TextButton(onClick = onRetry) {
                    Text("Retry", color = MaterialTheme.colorScheme.primary)
                }
            }

            is HomeViewModel.RowState.Content -> {
                if (items.isNotEmpty()) {
                    PosterRow(
                        title = "Popular on ${row.ref.name}",
                        subtitle = rowState.staleWarning?.let { "Showing cached results" },
                        items = items.take(12),
                        onItem = onOpenSeries,
                        actionLabel = "See all",
                        onAction = onOpenSource,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(update: UpdateItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 76.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (update.thumbnailUrl != null) {
                AsyncImage(
                    model = update.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                update.seriesTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                update.episodeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            GenreLine("${update.sourceName} • ${formatRelativeTime(update.discoveredAt)}")
        }
        androidx.compose.material3.TextButton(onClick = onClick) {
            Text("Open", color = MaterialTheme.colorScheme.primary)
        }
    }
}
