package app.anilab.local.ui.screens.repositories

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.anilab.local.domain.model.RepoSummary
import app.anilab.local.domain.repository.AddRepoResult
import app.anilab.local.domain.repository.RepoRepository
import app.anilab.local.ui.components.AddRepositoryDialog
import app.anilab.local.ui.components.Badge
import app.anilab.local.ui.components.EmptyState
import app.anilab.local.ui.components.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RepositoriesViewModel @Inject constructor(
    private val repoRepository: RepoRepository,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val repos: List<RepoSummary> = emptyList(),
        val refreshing: Set<Long> = emptySet(),
        val refreshingAll: Boolean = false,
        val message: String? = null,
        val pendingTrust: RepoSummary? = null,
        val pendingDelete: RepoSummary? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repoRepository.observeRepos().collect { repos ->
                _state.update { it.copy(repos = repos, loading = false) }
            }
        }
    }

    suspend fun add(url: String): AddRepoResult = repoRepository.addRepo(url)

    fun afterAdd(repo: RepoSummary) = _state.update { it.copy(pendingTrust = repo) }

    fun setTrust(repo: RepoSummary, trusted: Boolean) {
        viewModelScope.launch {
            repoRepository.setTrusted(repo.id, trusted)
            _state.update { it.copy(pendingTrust = null) }
        }
    }

    fun dismissTrust() = _state.update { it.copy(pendingTrust = null) }

    fun refresh(repo: RepoSummary) {
        if (_state.value.refreshing.contains(repo.id)) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = it.refreshing + repo.id) }
            val fresh = runCatching { repoRepository.refresh(repo.id) }.getOrDefault(false)
            _state.update {
                it.copy(
                    refreshing = it.refreshing - repo.id,
                    message = if (fresh) "Index updated" else "Showing the cached index. Check your connection.",
                )
            }
        }
    }

    fun refreshAll() {
        if (_state.value.refreshingAll) return
        viewModelScope.launch {
            _state.update { it.copy(refreshingAll = true) }
            val failures = runCatching { repoRepository.refreshAll() }.getOrDefault(0)
            _state.update {
                it.copy(
                    refreshingAll = false,
                    message = if (failures == 0) "All repositories updated" else "$failures repository(ies) could not be updated",
                )
            }
        }
    }

    fun setPinned(repo: RepoSummary, pinned: Boolean) = viewModelScope.launch { repoRepository.setPinned(repo.id, pinned) }

    fun askDelete(repo: RepoSummary) = _state.update { it.copy(pendingDelete = repo) }

    fun dismissDelete() = _state.update { it.copy(pendingDelete = null) }

    fun delete(repo: RepoSummary) {
        viewModelScope.launch {
            repoRepository.delete(repo.id)
            _state.update { it.copy(pendingDelete = null, message = "Repository removed. Installed extensions stay installed.") }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoriesScreen(
    onBack: () -> Unit,
    viewModel: RepositoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repositories") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state.refreshingAll) {
                        CircularProgressIndicator(Modifier.padding(12.dp).height(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = viewModel::refreshAll, enabled = state.repos.isNotEmpty()) { Text("Refresh all") }
                    }
                    IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, contentDescription = "Add repository") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.repos.isEmpty() -> EmptyState(
                title = "No repositories",
                message = "Add a repository by pasting its GitHub-hosted index URL. Nothing is bundled with the app.",
                actionLabel = "Add repository",
                onAction = { adding = true },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.repos, key = { it.id }) { repo ->
                    RepoRow(
                        repo = repo,
                        refreshing = state.refreshing.contains(repo.id),
                        onRefresh = { viewModel.refresh(repo) },
                        onPinned = { pinned -> viewModel.setPinned(repo, pinned) },
                        onTrust = { trusted -> viewModel.setTrust(repo, trusted) },
                        onDelete = { viewModel.askDelete(repo) },
                    )
                }
            }
        }
    }

    if (adding) {
        AddRepositoryDialog(
            onDismiss = { adding = false },
            onSubmit = viewModel::add,
            onAdded = { repo ->
                adding = false
                viewModel.afterAdd(repo)
            },
        )
    }

    state.pendingTrust?.let { repo ->
        AlertDialog(
            onDismissRequest = viewModel::dismissTrust,
            title = { Text("Trust ${repo.name}?") },
            text = {
                Text(
                    "Extensions from this repository are third-party code that runs inside the app. " +
                        "Trusted repositories install with one tap and can be auto-updated. " +
                        "Untrusted ones always ask before installing.",
                )
            },
            confirmButton = { Button(onClick = { viewModel.setTrust(repo, true) }) { Text("Trust") } },
            dismissButton = { TextButton(onClick = { viewModel.setTrust(repo, false) }) { Text("Keep untrusted") } },
        )
    }

    state.pendingDelete?.let { repo ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Remove ${repo.name}?") },
            text = { Text("Its index is deleted from this device. Extensions you already installed stay installed until you uninstall them.") },
            confirmButton = { Button(onClick = { viewModel.delete(repo) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RepoRow(
    repo: RepoSummary,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPinned: (Boolean) -> Unit,
    onTrust: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(repo.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    repo.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (refreshing) {
                CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Refresh index") }, onClick = { menu = false; onRefresh() })
                DropdownMenuItem(
                    text = { Text(if (repo.pinned) "Unpin" else "Pin to top") },
                    onClick = { menu = false; onPinned(!repo.pinned) },
                )
                DropdownMenuItem(text = { Text("Remove repository") }, onClick = { menu = false; onDelete() })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge(if (repo.trusted) "Trusted" else "Untrusted")
            if (repo.pinned) Badge("Pinned")
            Text("${repo.extensionCount} extension(s)", style = MaterialTheme.typography.labelSmall)
            if (repo.lastUpdatedAt > 0L) {
                Text(
                    "updated ${formatAgo(repo.lastUpdatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Text("Trust", style = MaterialTheme.typography.labelSmall)
            Switch(checked = repo.trusted, onCheckedChange = onTrust)
        }
        repo.lastError?.let { error ->
            Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatAgo(timestamp: Long): String {
    val delta = System.currentTimeMillis() - timestamp
    return when {
        delta < 60_000L -> "just now"
        delta < 3_600_000L -> "${delta / 60_000L} min ago"
        delta < 86_400_000L -> "${delta / 3_600_000L} h ago"
        else -> "${delta / 86_400_000L} d ago"
    }
}
