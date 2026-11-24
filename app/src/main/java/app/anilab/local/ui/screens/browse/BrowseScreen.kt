package app.anilab.local.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.anilab.local.domain.model.SourceRef
import app.anilab.local.domain.repository.BrowseRepository
import app.anilab.local.domain.repository.SettingsRepository
import app.anilab.local.ui.components.Badge
import app.anilab.local.ui.components.EmptyState
import app.anilab.local.ui.components.LoadingState
import app.anilab.local.ui.components.ShimmerBox
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    browseRepository: BrowseRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val sources: List<SourceRef> = emptyList(),
        val hiddenNsfwCount: Int = 0,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { browseRepository.observeSources() }.onSuccess { flow ->
                flow.collect { sources ->
                    val showNsfw = runCatching { settingsRepository.current().showNsfw }.getOrDefault(false)
                    _state.update {
                        it.copy(
                            loading = false,
                            sources = if (showNsfw) sources else sources.filter { source -> !source.nsfw },
                            hiddenNsfwCount = if (showNsfw) 0 else sources.count { source -> source.nsfw },
                            error = null,
                        )
                    }
                }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "Couldn't load sources") }
            }
        }
    }

    fun retry() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // The source flow re-emits on its own; this only clears a stale error state.
            _state.update { it.copy(loading = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onOpenSource: (SourceRef) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenExtensions: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse") },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding), label = "Loading sources")
            state.sources.isEmpty() && state.hiddenNsfwCount > 0 -> EmptyState(
                title = "All sources are hidden",
                message = "Every installed source is marked NSFW. Enable NSFW visibility in Settings to see them.",
                modifier = Modifier.padding(padding),
            )
            state.sources.isEmpty() -> EmptyState(
                title = "No sources installed",
                message = "Install an extension to browse. Repositories are added by pasting their index URL.",
                actionLabel = "Open extensions",
                onAction = onOpenExtensions,
                modifier = Modifier.padding(padding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.sources, key = { it.sourceId }) { source ->
                    SourceCard(source = source, onClick = { onOpenSource(source) })
                }
            }
        }
    }
}

@Composable
private fun SourceCard(source: SourceRef, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = source.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    source.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    source.lang.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (source.nsfw) Badge("NSFW", container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
            if (!source.trusted) Badge("Untrusted")
        }
    }
}
