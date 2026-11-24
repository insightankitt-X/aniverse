package app.aniverse.local.ui.screens.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aniverse.local.domain.model.Category
import app.aniverse.local.domain.model.Series
import app.aniverse.local.domain.repository.CategoryMembership
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.ui.components.EmptyState
import app.aniverse.local.ui.components.PosterCard
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibrarySort { TITLE, ADDED, UPDATED }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    membership: CategoryMembership,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val all: List<Series> = emptyList(),
        val categories: List<Category> = emptyList(),
        val members: Map<Long, Set<Long>> = emptyMap(),
        val selectedCategory: Long? = null,
        val query: String = "",
        val sort: LibrarySort = LibrarySort.TITLE,
        val grid: Boolean = true,
        val selectionMode: Boolean = false,
        val selected: Set<Long> = emptySet(),
        val editingCategory: Category? = null,
        val creating: Boolean = false,
    ) {
        val visible: List<Series>
            get() {
                val categoryMembers = selectedCategory?.let { members[it] }
                val base = if (categoryMembers == null) all else all.filter { it.id in categoryMembers }
                val searched = if (query.isBlank()) base else base.filter { it.title.contains(query, ignoreCase = true) }
                return when (sort) {
                    LibrarySort.TITLE -> searched.sortedBy { it.title.lowercase() }
                    LibrarySort.ADDED -> searched.sortedByDescending { it.id }
                    LibrarySort.UPDATED -> searched.sortedByDescending { it.lastUpdatedAt }
                }
            }
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.observeLibrary().collect { series ->
                _state.update { it.copy(all = series, loading = false) }
            }
        }
        viewModelScope.launch {
            libraryRepository.observeCategories().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            membership.observeMembers().collect { members ->
                _state.update { it.copy(members = members) }
            }
        }
    }

    fun selectCategory(id: Long?) = _state.update { it.copy(selectedCategory = id) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setSort(sort: LibrarySort) = _state.update { it.copy(sort = sort) }

    fun toggleLayout() = _state.update { it.copy(grid = !it.grid) }

    fun startSelection(seriesId: Long? = null) = _state.update {
        it.copy(selectionMode = true, selected = seriesId?.let { id -> setOf(id) } ?: emptySet())
    }

    fun toggleSelection(seriesId: Long) = _state.update {
        val selected = it.selected.toMutableSet()
        if (!selected.add(seriesId)) selected.remove(seriesId)
        it.copy(selected = selected, selectionMode = selected.isNotEmpty())
    }

    fun cancelSelection() = _state.update { it.copy(selectionMode = false, selected = emptySet()) }

    fun removeSelected() {
        val ids = _state.value.selected.toList()
        viewModelScope.launch {
            libraryRepository.removeFromLibrary(ids)
            _state.update { it.copy(selectionMode = false, selected = emptySet()) }
        }
    }

    fun openCreate() = _state.update { it.copy(creating = true) }

    fun dismissCreate() = _state.update { it.copy(creating = false) }

    fun createCategory(name: String) {
        viewModelScope.launch {
            runCatching { libraryRepository.createCategory(name) }
                .onSuccess { _state.update { state -> state.copy(creating = false) } }
                .onFailure { _state.update { state -> state.copy(creating = false) } }
        }
    }

    fun openEdit(category: Category) = _state.update { it.copy(editingCategory = category) }

    fun dismissEdit() = _state.update { it.copy(editingCategory = null) }

    fun renameCategory(category: Category, name: String) {
        viewModelScope.launch {
            runCatching { libraryRepository.renameCategory(category.id, name) }
            _state.update { state -> state.copy(editingCategory = null) }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            libraryRepository.deleteCategory(category.id)
            _state.update { it.copy(editingCategory = null, selectedCategory = null) }
        }
    }

    fun moveCategory(category: Category, direction: Int) {
        val ordered = _state.value.categories.map { it.id }.toMutableList()
        val from = ordered.indexOf(category.id)
        val to = from + direction
        if (from < 0 || to !in ordered.indices) return
        ordered[from] = ordered[to]
        ordered[to] = category.id
        viewModelScope.launch { libraryRepository.reorderCategories(ordered) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDetails: (Series) -> Unit,
    onBrowse: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            singleLine = true,
                            label = { Text("Search library") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(if (state.selectionMode) "${state.selected.size} selected" else "Library")
                    }
                },
                navigationIcon = {
                    if (state.selectionMode) {
                        IconButton(onClick = viewModel::cancelSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    }
                },
                actions = {
                    if (!state.selectionMode) {
                        IconButton(onClick = { searching = !searching; if (!searching) viewModel.setQuery("") }) {
                            Icon(if (searching) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search library")
                        }
                        IconButton(onClick = viewModel::toggleLayout) {
                            Icon(if (state.grid) Icons.Default.ViewList else Icons.Default.GridView, contentDescription = "Toggle layout")
                        }
                        IconButton(onClick = { viewModel.startSelection() }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Select series")
                        }
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Sort and categories")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.selectionMode) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::cancelSelection, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(onClick = viewModel::removeSelected, enabled = state.selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Remove") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = state.selectedCategory == null, onClick = { viewModel.selectCategory(null) }, label = { Text("All") })
                }
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.selectedCategory == category.id,
                        onClick = { viewModel.selectCategory(category.id) },
                        label = { Text(category.name) },
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = viewModel::openCreate,
                        label = { Text("New") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    )
                }
            }

            val visible = state.visible
            when {
                state.loading -> EmptyState(title = "Loading", message = "Reading your library")
                visible.isEmpty() -> EmptyState(
                    title = "Your library is empty",
                    message = "Add a series from any source to see it here.",
                    actionLabel = "Browse",
                    onAction = onBrowse,
                )
                state.grid -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(visible, key = { it.id }) { series ->
                        Box {
                            PosterCard(
                                title = series.title,
                                thumbnailUrl = series.thumbnailUrl,
                                onClick = {
                                    if (state.selectionMode) viewModel.toggleSelection(series.id) else onOpenDetails(series)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (state.selectionMode && state.selected.contains(series.id)) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                                )
                            }
                        }
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(visible, key = { it.id }) { series ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (state.selectionMode) viewModel.toggleSelection(series.id) else onOpenDetails(series)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(width = 48.dp, height = 72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                if (series.thumbnailUrl != null) {
                                    AsyncImage(
                                        model = series.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(series.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    series.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.selected.contains(series.id)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
        DropdownMenuItem(text = { Text("Sort by title") }, onClick = { viewModel.setSort(LibrarySort.TITLE); sortMenu = false })
        DropdownMenuItem(text = { Text("Sort by date added") }, onClick = { viewModel.setSort(LibrarySort.ADDED); sortMenu = false })
        DropdownMenuItem(text = { Text("Sort by last update") }, onClick = { viewModel.setSort(LibrarySort.UPDATED); sortMenu = false })
        DropdownMenuItem(text = { Text("Manage categories") }, onClick = { sortMenu = false; categoryMenu = true })
    }

    if (categoryMenu) {
        AlertDialog(
            onDismissRequest = { categoryMenu = false },
            title = { Text("Categories") },
            text = {
                Column {
                    state.categories.forEach { category ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(category.name, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.moveCategory(category, -1) }) { Text("Up") }
                            TextButton(onClick = { viewModel.moveCategory(category, 1) }) { Text("Down") }
                            TextButton(onClick = { viewModel.openEdit(category) }) { Text("Edit") }
                        }
                    }
                    if (state.categories.isEmpty()) Text("No categories yet.")
                }
            },
            confirmButton = { Button(onClick = { categoryMenu = false; viewModel.openCreate() }) { Text("New category") } },
            dismissButton = { TextButton(onClick = { categoryMenu = false }) { Text("Close") } },
        )
    }

    if (state.creating) {
        NameDialog(
            title = "New category",
            initial = "",
            onDismiss = viewModel::dismissCreate,
            onConfirm = viewModel::createCategory,
        )
    }

    state.editingCategory?.let { category ->
        var name by remember(category.id) { mutableStateOf(category.name) }
        AlertDialog(
            onDismissRequest = viewModel::dismissEdit,
            title = { Text("Edit category") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                    Spacer(Modifier.height(8.dp))
                    Text("Deleting a category leaves its series in your library.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { viewModel.renameCategory(category, name) }, enabled = name.isNotBlank()) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { viewModel.deleteCategory(category) }) { Text("Delete") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
