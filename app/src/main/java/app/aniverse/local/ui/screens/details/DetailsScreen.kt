package app.aniverse.local.ui.screens.details

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aniverse.local.domain.model.Category
import app.aniverse.local.domain.model.DownloadState
import app.aniverse.local.domain.model.Episode
import app.aniverse.local.domain.model.Series
import app.aniverse.local.domain.repository.BrowseRepository
import app.aniverse.local.domain.repository.DownloadRepository
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.ui.components.Badge
import app.aniverse.local.ui.components.EmptyState
import app.aniverse.local.ui.components.LoadingState
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        val categories: List<Category> = emptyList(),
        val selectedCategories: List<Long> = emptyList(),
        val showCategoryPicker: Boolean = false,
        val resumeEpisodeId: Long? = null,
        val error: String? = null,
        val grid: Boolean = false,
        val message: String? = null,
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
        }
    }

    fun openCategoryPicker() {
        val series = _state.value.series ?: return
        viewModelScope.launch {
            if (series.inLibrary) {
                libraryRepository.toggleLibrary(series)
                _state.update { it.copy(series = series.copy(inLibrary = false), message = "Removed from library") }
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
            _state.update { it.copy(series = series.copy(inLibrary = true), showCategoryPicker = false, message = "Added to library") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    onPlay: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.series?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleGrid() }) {
                        Icon(if (state.grid) Icons.Default.ViewList else Icons.Default.GridView, contentDescription = "Toggle layout")
                    }
                    IconButton(
                        onClick = {
                            val share = listOfNotNull(
                                state.series?.title,
                                state.series?.sourcePkg,
                                state.series?.url?.takeIf { it.isNotBlank() },
                            ).joinToString("\n")
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, share)
                            }
                            runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Share")) }
                        },
                    ) { Icon(Icons.Default.Share, contentDescription = "Share") }
                },
            )
        },
    ) { padding ->
        val series = state.series
        when {
            state.loading && series == null -> LoadingState(Modifier.padding(padding))
            series == null -> EmptyState(
                title = "Couldn't open this series",
                message = state.error ?: "Try again in a moment.",
                actionLabel = "Retry",
                onAction = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth().height(220.dp)) {
                        if (series.thumbnailUrl != null) {
                            AsyncImage(
                                model = series.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)),
                            ),
                        )
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(series.title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge(series.status.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
                            series.genres.take(3).forEach { genre -> Badge(genre) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val target = state.resumeEpisodeId ?: state.episodes.firstOrNull()?.id
                                    if (target != null && target > 0L) onPlay(target)
                                },
                                enabled = state.episodes.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (state.resumeEpisodeId != null) "Resume" else "Play")
                            }
                            FilledTonalButton(onClick = viewModel::openCategoryPicker) {
                                Text(if (series.inLibrary) "In library" else "Add to library")
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(16.dp)) {
                        val description = series.description.orEmpty()
                        if (description.isNotBlank()) {
                            Text(
                                description,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (expanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "Show less" else "Show more")
                            }
                        }
                        Text(
                            "${state.episodes.size} episode(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Episodes") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Details") })
                    }
                }
                if (tab == 0) {
                    when {
                        state.episodes.isEmpty() -> item {
                            EmptyState(title = "No episodes", message = "This source returned no episodes.")
                        }
                        state.grid -> items(state.episodes.chunked(4)) { rowEpisodes ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                                rowEpisodes.forEach { episode ->
                                    EpisodeCell(
                                        episode = episode,
                                        onClick = { onPlay(episode.id) },
                                        onLongPress = { viewModel.markWatched(episode, !episode.watched) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(4 - rowEpisodes.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        else -> items(state.episodes, key = { it.id }) { episode ->
                            EpisodeRow(
                                episode = episode,
                                onClick = { onPlay(episode.id) },
                                onMarkWatched = { viewModel.markWatched(episode, !episode.watched) },
                                onDownload = { viewModel.download(episode) },
                                onRemoveDownload = { viewModel.removeDownload(episode) },
                            )
                        }
                    }
                } else {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text("Author: ${series.author ?: "Unknown"}")
                            Text("Artist: ${series.artist ?: "Unknown"}")
                            Text("Status: ${series.status.name.lowercase()}")
                            Text("Source: ${series.sourcePkg}")
                        }
                    }
                }
            }
        }
    }

    if (state.showCategoryPicker) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCategoryPicker,
            title = { Text("Add to library") },
            text = {
                Column {
                    if (state.categories.isEmpty()) {
                        Text("No categories yet. It will be added without a category.")
                    } else {
                        state.categories.forEach { category ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = state.selectedCategories.contains(category.id),
                                    onCheckedChange = { viewModel.toggleCategory(category.id) },
                                )
                                Text(category.name)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = viewModel::confirmAddToLibrary) { Text("Add") } },
            dismissButton = { OutlinedButton(onClick = viewModel::dismissCategoryPicker) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    onMarkWatched: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(episode.name, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (episode.watched) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Watched", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Watched", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (episode.filler) Badge("Filler")
                    if (episode.downloadState == DownloadState.COMPLETED) {
                        Icon(Icons.Default.Download, contentDescription = "Downloaded", modifier = Modifier.size(14.dp))
                    }
                }
            }
            Text(if (episode.number > 0) episode.number.toInt().toString() else "", style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (episode.watched) "Mark unwatched" else "Mark watched") }, onClick = { menu = false; onMarkWatched() })
            if (episode.downloadState == DownloadState.COMPLETED) {
                DropdownMenuItem(text = { Text("Remove download") }, onClick = { menu = false; onRemoveDownload() })
            } else {
                DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; onDownload() })
            }
        }
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
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(8.dp)
            .aspectRatio(1.6f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (episode.number > 0) episode.number.toInt().toString() else episode.name.take(2),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
