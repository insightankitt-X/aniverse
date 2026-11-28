package app.aniverse.local.ui.screens.details

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import app.aniverse.local.domain.model.Category
import app.aniverse.local.domain.model.DownloadState
import app.aniverse.local.domain.model.Episode
import app.aniverse.local.domain.model.Series
import app.aniverse.local.domain.repository.BrowseRepository
import app.aniverse.local.domain.repository.DownloadRepository
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.ui.components.AniBottomSheet
import app.aniverse.local.ui.components.AniSheetAction
import app.aniverse.local.ui.components.AniTopBar
import app.aniverse.local.ui.components.EmptyState
import app.aniverse.local.ui.components.EpisodeRow
import app.aniverse.local.ui.components.GenreChip
import app.aniverse.local.ui.components.LoadingState
import app.aniverse.local.ui.components.PillButton
import app.aniverse.local.ui.components.PillStyle
import app.aniverse.local.ui.components.PosterRow
import app.aniverse.local.ui.components.SegmentedToggle
import app.aniverse.local.ui.components.StatusChip
import app.aniverse.local.ui.theme.contrastOn
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val browseRepository: BrowseRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val series: Series? = null,
        val episodes: List<Episode> = emptyList(),
        val related: List<Series> = emptyList(),
        val categories: List<Category> = emptyList(),
        val selectedCategories: List<Long> = emptyList(),
        val showCategoryPicker: Boolean = false,
        val resumeEpisodeId: Long? = null,
        val error: String? = null,
        val grid: Boolean = false,
        val message: String? = null,
        val tab: Int = 0,
    )

    private val seriesIdArg: Long = savedStateHandle.get<String>("seriesId")?.toLongOrNull() ?: 0L
    private val pkgArg: String = savedStateHandle.get<String>("pkg").orEmpty()
    private val sourceIdArg: Long = savedStateHandle.get<String>("sourceId")?.toLongOrNull() ?: 0L
    private val remoteIdArg: String = savedStateHandle.get<String>("remoteId").orEmpty()
    private val titleArg: String = savedStateHandle.get<String>("title").orEmpty()

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.observeCategories().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
        if (seriesIdArg > 0L) {
            viewModelScope.launch {
                libraryRepository.observeSeries(seriesIdArg).collect { stored ->
                    if (stored != null) _state.update { it.copy(series = stored) }
                }
            }
            viewModelScope.launch {
                libraryRepository.observeEpisodes(seriesIdArg).collect { episodes ->
                    _state.update { it.copy(episodes = episodes) }
                }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val stub = _state.value.series ?: Series(
                id = seriesIdArg,
                sourcePkg = pkgArg,
                sourceId = sourceIdArg,
                remoteId = remoteIdArg,
                title = titleArg.ifBlank { "Series" },
            )
            if (stub.sourcePkg.isBlank() || stub.remoteId.isBlank()) {
                _state.update { it.copy(loading = false, error = "This series is not available") }
                return@launch
            }
            val detailed = browseRepository.details(stub).getOrElse { stub }
            val episodes = browseRepository.episodes(detailed)
                .getOrElse { libraryRepository.cacheEpisodes(detailed).getOrElse { emptyList() } }
            val resume = episodes.firstOrNull { it.positionMs > 0L && it.durationMs > 0L && !it.watched }?.id
                ?: episodes.firstOrNull { !it.watched }?.id
            val inLibrary = runCatching { libraryRepository.isInLibrary(detailed) }.getOrDefault(false)
            _state.update {
                it.copy(
                    loading = false,
                    series = detailed.copy(inLibrary = inLibrary),
                    episodes = episodes,
                    resumeEpisodeId = resume,
                    error = null,
                )
            }
            viewModelScope.launch {
                val related = runCatching { browseRepository.related(detailed) }.getOrDefault(emptyList())
                _state.update { it.copy(related = related) }
            }
        }
    }

    fun setTab(index: Int) = _state.update { it.copy(tab = index) }

    fun openCategoryPicker() {
        val series = _state.value.series ?: return
        viewModelScope.launch {
            if (series.inLibrary) {
                libraryRepository.toggleLibrary(series)
                _state.update { it.copy(series = series.copy(inLibrary = false), message = "Removed from My List") }
            } else {
                _state.update { it.copy(showCategoryPicker = true, selectedCategories = emptyList()) }
            }
        }
    }

    fun dismissCategoryPicker() = _state.update { it.copy(showCategoryPicker = false) }

    fun toggleCategory(id: Long) {
        _state.update {
            val selected = it.selectedCategories.toMutableList()
            if (selected.contains(id)) selected.remove(id) else selected.add(id)
            it.copy(selectedCategories = selected)
        }
    }

    fun confirmAddToLibrary() {
        val series = _state.value.series ?: return
        val categories = _state.value.selectedCategories
        viewModelScope.launch {
            libraryRepository.toggleLibrary(series, categories)
            _state.update { it.copy(series = series.copy(inLibrary = true), showCategoryPicker = false, message = "Added to My List") }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun toggleGrid() = _state.update { it.copy(grid = !it.grid) }

    fun markWatched(episode: Episode, watched: Boolean) {
        viewModelScope.launch { libraryRepository.markWatched(episode.id, watched) }
    }

    fun download(episode: Episode) {
        val series = _state.value.series ?: return
        viewModelScope.launch {
            val queued = downloadRepository.enqueue(series, episode, quality = null)
            _state.update { it.copy(message = if (queued) "Download queued" else "Already downloaded") }
        }
    }

    fun removeDownload(episode: Episode) {
        viewModelScope.launch {
            downloadRepository.remove(episode.id, deleteFile = true)
            _state.update { it.copy(message = "Download removed") }
        }
    }
}

@Composable
fun DetailsScreen(
    onPlay: (Long) -> Unit,
    onOpenRelated: (Series) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val series = state.series
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var expanded by remember { mutableStateOf(false) }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Only recompose the top bar when it crosses the solid threshold; the parallax itself is read
    // inside graphicsLayer, never during composition.
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 180
        }
    }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(180),
        label = "detailsTopBar",
    )
    val artworkTint = rememberArtworkColor(series?.thumbnailUrl)

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading && series == null -> LoadingState()
            series == null -> EmptyState(
                title = "Couldn't open this series",
                message = state.error ?: "Try again in a moment.",
                actionLabel = "Retry",
                onAction = viewModel::load,
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 30.dp),
            ) {
                item(key = "header") {
                    DetailHeader(
                        series = series,
                        listState = listState,
                        tint = artworkTint,
                    )
                }

                item(key = "summary") {
                    DetailSummary(
                        series = series,
                        episodes = state.episodes,
                        resumeEpisodeId = state.resumeEpisodeId,
                        tint = artworkTint,
                        expanded = expanded,
                        onToggleExpanded = { expanded = !expanded },
                        onPlay = {
                            val target = state.resumeEpisodeId ?: state.episodes.firstOrNull()?.id
                            if (target != null && target > 0L) onPlay(target)
                        },
                        onToggleList = viewModel::openCategoryPicker,
                    )
                }

                item(key = "tabs") {
                    SegmentedToggle(
                        options = listOf("Episodes", "More"),
                        selectedIndex = state.tab,
                        onSelect = viewModel::setTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }

                if (state.tab == 0) {
                    item(key = "episodeHeader") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${state.episodes.size} episode(s)",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = viewModel::toggleGrid) {
                                Icon(
                                    if (state.grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                                    contentDescription = "Toggle episode layout",
                                )
                            }
                        }
                    }

                    if (state.episodes.isEmpty()) {
                        item(key = "noEpisodes") {
                            EmptyState(title = "No episodes", message = "This source returned no episodes for this series.")
                        }
                    } else if (state.grid) {
                        items(state.episodes.chunked(4), key = { row -> "row-${row.first().id}" }) { rowEpisodes ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                                rowEpisodes.forEach { episode ->
                                    EpisodeCell(
                                        episode = episode,
                                        onClick = { onPlay(episode.id) },
                                        onLongPress = { selectedEpisode = episode },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(4 - rowEpisodes.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        items(state.episodes, key = { it.id }, contentType = { "episode" }) { episode ->
                            EpisodeRow(
                                episode = episode,
                                onClick = { onPlay(episode.id) },
                                onLongClick = { selectedEpisode = episode },
                                trailing = {
                                    IconButton(
                                        onClick = {
                                            if (episode.downloadState == DownloadState.COMPLETED) {
                                                viewModel.removeDownload(episode)
                                            } else {
                                                viewModel.download(episode)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            if (episode.downloadState == DownloadState.COMPLETED) {
                                                Icons.Outlined.CheckCircle
                                            } else {
                                                Icons.Outlined.Download
                                            },
                                            contentDescription = if (episode.downloadState == DownloadState.COMPLETED) {
                                                "Remove download"
                                            } else {
                                                "Download episode"
                                            },
                                            tint = if (episode.downloadState == DownloadState.COMPLETED) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                } else {
                    item(key = "moreDetails") {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            MetaLine("Status", series.status.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
                            MetaLine("Episodes", "${state.episodes.size}")
                            series.author?.let { MetaLine("Author", it) }
                            series.artist?.let { MetaLine("Artist", it) }
                            MetaLine("Source", series.sourcePkg.substringAfterLast('.').ifBlank { series.sourcePkg })
                        }
                    }
                }

                if (state.related.isNotEmpty()) {
                    item(key = "related") {
                        PosterRow(
                            title = "More like this",
                            items = state.related,
                            onItem = onOpenRelated,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
            }
        }

        AniTopBar(
            title = if (scrolled) series?.title else null,
            onBack = onBack,
            backgroundAlpha = overlayAlpha,
            actions = {
                IconButton(
                    onClick = {
                        val share = listOfNotNull(
                            series?.title,
                            series?.sourcePkg,
                            series?.url?.takeIf { it.isNotBlank() },
                        ).joinToString("\n")
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, share)
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
                    },
                ) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = if (scrolled) MaterialTheme.colorScheme.onSurface else Color.White,
                    )
                }
            },
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }

    if (state.showCategoryPicker) {
        AniBottomSheet(onDismiss = viewModel::dismissCategoryPicker, title = "Add to My List") {
            if (state.categories.isEmpty()) {
                Text("No categories yet. It will be added without a category.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.categories.forEach { category ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleCategory(category.id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.selectedCategories.contains(category.id),
                            onCheckedChange = { viewModel.toggleCategory(category.id) },
                        )
                        Text(category.name)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            PillButton("Add to My List", viewModel::confirmAddToLibrary, modifier = Modifier.fillMaxWidth())
        }
    }

    selectedEpisode?.let { episode ->
        AniBottomSheet(onDismiss = { selectedEpisode = null }, title = episode.name) {
            AniSheetAction("Play episode", { selectedEpisode = null; onPlay(episode.id) })
            AniSheetAction(
                if (episode.watched) "Mark as unwatched" else "Mark as watched",
                { viewModel.markWatched(episode, !episode.watched); selectedEpisode = null },
            )
            AniSheetAction(
                if (episode.downloadState == DownloadState.COMPLETED) "Remove download" else "Download episode",
                {
                    if (episode.downloadState == DownloadState.COMPLETED) viewModel.removeDownload(episode) else viewModel.download(episode)
                    selectedEpisode = null
                },
            )
        }
    }
}

@Composable
private fun DetailHeader(series: Series, listState: LazyListState, tint: Color?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(430.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (series.thumbnailUrl != null) {
            coil.compose.AsyncImage(
                model = series.thumbnailUrl,
                contentDescription = series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scroll = if (listState.firstVisibleItemIndex == 0) {
                            listState.firstVisibleItemScrollOffset
                        } else {
                            460
                        }
                        translationY = scroll * 0.32f
                        scaleX = 1.08f
                        scaleY = 1.08f
                    },
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), MaterialTheme.colorScheme.surfaceVariant),
                        ),
                    ),
            )
        }
        if (tint != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to tint.copy(alpha = 0.32f),
                            0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.15f),
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
            )
        }
        // Keeps the floating top-bar controls legible on bright artwork in every palette.
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

@Composable
private fun DetailSummary(
    series: Series,
    episodes: List<Episode>,
    resumeEpisodeId: Long?,
    tint: Color?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
) {
    val resumeNumber = episodes.firstOrNull { it.id == resumeEpisodeId }?.number ?: -1.0
    Column(
        Modifier
            .fillMaxWidth()
            .offset(y = (-56).dp)
            .padding(horizontal = 20.dp),
    ) {
        Row {
            Box(
                Modifier
                    .size(width = 112.dp, height = 168.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (series.thumbnailUrl != null) {
                    coil.compose.AsyncImage(
                        model = series.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    series.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                StatusChip(series.status)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${episodes.size} episodes" + series.author?.let { " • $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }

        if (series.genres.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                series.genres.take(4).forEach { genre ->
                    GenreChip(text = genre)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = when {
                    resumeEpisodeId == null -> "Play"
                    resumeNumber > 0 -> "Continue EP ${resumeNumber.toInt()}"
                    else -> "Continue"
                },
                onClick = onPlay,
                icon = Icons.Outlined.PlayArrow,
                modifier = Modifier.weight(1f),
                containerColor = tint,
                contentColorOverride = tint?.let { contrastOn(it) },
            )
            PillButton(
                text = if (series.inLibrary) "In My List" else "My List",
                onClick = onToggleList,
                icon = if (series.inLibrary) Icons.Outlined.Check else Icons.Outlined.Add,
                style = PillStyle.Outlined,
            )
        }

        val description = series.description.orEmpty()
        if (description.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize(tween(240)),
            )
            TextButton(onClick = onToggleExpanded) {
                Text(if (expanded) "Show less" else "Show more", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCell(
    episode: Episode,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (episode.watched) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(10.dp)
            .aspectRatio(1.55f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (episode.number > 0) episode.number.toInt().toString() else episode.name.take(2),
            style = MaterialTheme.typography.titleMedium,
            color = if (episode.watched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Reads the dominant artwork colour once, off the main thread via Coil. */
@Composable
private fun rememberArtworkColor(url: String?): Color? {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val drawable = runCatching { context.imageLoader.execute(request).drawable }.getOrNull()
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val swatch = withContext(Dispatchers.Default) {
            runCatching { Palette.from(bitmap).generate().dominantSwatch }.getOrNull()
        }
        color = swatch?.rgb?.let { Color(it) }
    }
    return color
}
