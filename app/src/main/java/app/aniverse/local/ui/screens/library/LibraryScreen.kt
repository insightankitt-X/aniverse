package app.aniverse.local.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.aniverse.local.domain.model.Category
import app.aniverse.local.domain.model.DownloadState
import app.aniverse.local.domain.model.Series
import app.aniverse.local.domain.repository.CategoryMembership
import app.aniverse.local.domain.repository.DownloadRepository
import app.aniverse.local.domain.repository.HistoryRepository
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.ui.components.AniBottomSheet
import app.aniverse.local.ui.components.AniLargeHeader
import app.aniverse.local.ui.components.AniSheetAction
import app.aniverse.local.ui.components.EmptyState
import app.aniverse.local.ui.components.PillButton
import app.aniverse.local.ui.components.PillStyle
import app.aniverse.local.ui.components.PillTabRow
import app.aniverse.local.ui.components.PosterCard
import app.aniverse.local.ui.components.PosterGrid
import app.aniverse.local.ui.components.PosterGridSkeleton
import app.aniverse.local.ui.components.GenreLine
import androidx.compose.material3.HorizontalDivider
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibrarySort { TITLE, ADDED, UPDATED }
enum class SmartFilter { WATCHING, DOWNLOADED }

internal data class Segment(val label: String, val categoryId: Long?, val smart: SmartFilter?)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    membership: CategoryMembership,
    downloadRepository: DownloadRepository,
    historyRepository: HistoryRepository,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val all: List<Series> = emptyList(),
        val categories: List<Category> = emptyList(),
        val members: Map<Long, Set<Long>> = emptyMap(),
        val selectedCategory: Long? = null,
        val smart: SmartFilter? = null,
        val watchingIds: Set<Long> = emptySet(),
        val downloadedIds: Set<Long> = emptySet(),
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
                val base = when {
                    smart == SmartFilter.WATCHING -> all.filter { it.id in watchingIds }
                    smart == SmartFilter.DOWNLOADED -> all.filter { it.id in downloadedIds }
                    selectedCategory != null -> all.filter { it.id in (members[selectedCategory] ?: emptySet()) }
                    else -> all
                }
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
        viewModelScope.launch {
            downloadRepository.observeDownloads().collect { groups ->
                val downloaded = groups.filter { group ->
                    group.items.any { it.state == DownloadState.COMPLETED }
                }.map { it.seriesId }.toSet()
                _state.update { it.copy(downloadedIds = downloaded) }
            }
        }
        viewModelScope.launch {
            historyRepository.observeContinueWatching().collect { items ->
                _state.update { it.copy(watchingIds = items.map { item -> item.series.id }.toSet()) }
            }
        }
    }

    internal fun selectSegment(index: Int, segments: List<Segment>) {
        val segment = segments.getOrNull(index) ?: return
        _state.update { it.copy(selectedCategory = segment.categoryId, smart = segment.smart) }
    }

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

    fun remove(series: Series) {
        viewModelScope.launch { libraryRepository.removeFromLibrary(listOf(series.id)) }
    }

    fun moveToCategory(series: Series, category: Category) {
        viewModelScope.launch { libraryRepository.setCategories(series.id, listOf(category.id)) }
    }

    fun openCreate() = _state.update { it.copy(creating = true) }

    fun dismissCreate() = _state.update { it.copy(creating = false) }

    fun createCategory(name: String) {
        viewModelScope.launch {
            runCatching { libraryRepository.createCategory(name) }
            _state.update { state -> state.copy(creating = false) }
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

@Composable
fun LibraryScreen(
    onOpenDetails: (Series) -> Unit,
    onBrowse: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Segment filtering and sorting happen once per state change, not on every recomposition.
    val visible = remember(state.all, state.smart, state.selectedCategory, state.members, state.query, state.sort) {
        state.visible
    }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var actionSheet by remember { mutableStateOf<Series?>(null) }
    var categorySheet by remember { mutableStateOf<Series?>(null) }

    val segments = remember(state.categories, state.watchingIds, state.downloadedIds) {
        buildList {
            add(Segment("All", null, null))
            if (state.watchingIds.isNotEmpty()) add(Segment("Watching", null, SmartFilter.WATCHING))
            if (state.downloadedIds.isNotEmpty()) add(Segment("Downloaded", null, SmartFilter.DOWNLOADED))
            state.categories.forEach { add(Segment(it.name, it.id, null)) }
        }
    }
    val selectedIndex = segments.indexOfFirst {
        it.categoryId == state.selectedCategory && it.smart == state.smart
    }.coerceAtLeast(0)

    Column(Modifier.fillMaxSize()) {
        AniLargeHeader(
            title = if (state.selectionMode) "${state.selected.size} selected" else "My List",
            subtitle = if (state.selectionMode) "Tap posters to select" else "${state.all.size} series in your library",
            actions = {
                if (state.selectionMode) {
                    IconButton(onClick = viewModel::cancelSelection) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel selection")
                    }
                } else {
                    IconButton(onClick = { searching = !searching; if (!searching) viewModel.setQuery("") }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search my list")
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            DropdownMenuItem(text = { Text("Sort by title") }, onClick = { viewModel.setSort(LibrarySort.TITLE); sortMenu = false })
                            DropdownMenuItem(text = { Text("Sort by date added") }, onClick = { viewModel.setSort(LibrarySort.ADDED); sortMenu = false })
                            DropdownMenuItem(text = { Text("Sort by last update") }, onClick = { viewModel.setSort(LibrarySort.UPDATED); sortMenu = false })
                            DropdownMenuItem(
                                text = { Text(if (state.grid) "List view" else "Grid view") },
                                leadingIcon = { Icon(if (state.grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView, contentDescription = null) },
                                onClick = { viewModel.toggleLayout(); sortMenu = false },
                            )
                            DropdownMenuItem(text = { Text("Select series") }, onClick = { viewModel.startSelection(); sortMenu = false })
                            DropdownMenuItem(text = { Text("Manage categories") }, onClick = { sortMenu = false; categoryMenu = true })
                            DropdownMenuItem(text = { Text("New category") }, leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) }, onClick = { sortMenu = false; viewModel.openCreate() })
                        }
                    }
                }
            },
        )

        if (searching) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text("Search my list") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        PillTabRow(
            options = segments.map { it.label },
            selectedIndex = selectedIndex,
            onSelect = { index -> viewModel.selectSegment(index, segments) },
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )

        when {
            state.loading -> PosterGridSkeleton(Modifier.weight(1f))
            visible.isEmpty() -> EmptyState(
                modifier = Modifier.weight(1f),
                title = if (state.all.isEmpty()) "Your list is empty" else "Nothing here yet",
                message = if (state.all.isEmpty()) {
                    "Add a series from any installed source and it will appear here."
                } else {
                    "This filter has no series. Try another segment or clear the search."
                },
                actionLabel = if (state.all.isEmpty()) "Browse sources" else null,
                onAction = if (state.all.isEmpty()) onBrowse else null,
            )

            state.grid -> PosterGrid(
                items = visible,
                onItem = { series ->
                    if (state.selectionMode) viewModel.toggleSelection(series.id) else onOpenDetails(series)
                },
                onLongItem = { series ->
                    if (state.selectionMode) viewModel.toggleSelection(series.id) else actionSheet = series
                },
                modifier = Modifier.weight(1f),
            )

            else -> LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
                items(visible, key = { it.id }, contentType = { "list" }) { series ->
                    ListRow(
                        series = series,
                        selected = state.selected.contains(series.id),
                        selectionMode = state.selectionMode,
                        onClick = {
                            if (state.selectionMode) viewModel.toggleSelection(series.id) else onOpenDetails(series)
                        },
                        onLongClick = { if (!state.selectionMode) actionSheet = series },
                    )
                }
            }
        }

        if (state.selectionMode) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton("Close", viewModel::cancelSelection, style = PillStyle.Outlined, modifier = Modifier.weight(1f))
                PillButton(
                    "Remove",
                    viewModel::removeSelected,
                    style = PillStyle.Filled,
                    enabled = state.selected.isNotEmpty(),
                    icon = Icons.Outlined.Delete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    actionSheet?.let { series ->
        AniBottomSheet(onDismiss = { actionSheet = null }, title = series.title) {
            AniSheetAction("Open details", { actionSheet = null; onOpenDetails(series) })
            AniSheetAction("Move to category", { actionSheet = null; categorySheet = series })
            if (state.categories.isEmpty()) {
                Text(
                    "Create a category first from the menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            AniSheetAction("Remove from My List", { actionSheet = null; viewModel.remove(series) }, destructive = true)
        }
    }

    categorySheet?.let { series ->
        AniBottomSheet(onDismiss = { categorySheet = null }, title = "Move to category") {
            if (state.categories.isEmpty()) {
                Text("No categories yet.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PillButton("New category", { categorySheet = null; viewModel.openCreate() })
            } else {
                state.categories.forEach { category ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.moveToCategory(series, category); categorySheet = null }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (state.members[category.id]?.contains(series.id) == true) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "In category", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
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
                            IconButton(onClick = { viewModel.moveCategory(category, -1) }) { Text("↑") }
                            IconButton(onClick = { viewModel.moveCategory(category, 1) }) { Text("↓") }
                            IconButton(onClick = { viewModel.openEdit(category) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Rename")
                            }
                        }
                    }
                    if (state.categories.isEmpty()) Text("No categories yet.")
                }
            },
            confirmButton = {
                Button(onClick = { categoryMenu = false; viewModel.openCreate() }) { Text("New category") }
            },
            dismissButton = { TextButton(onClick = { categoryMenu = false }) { Text("Close") } },
            shape = RoundedCornerShape(24.dp),
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
                    TextButton(onClick = { viewModel.deleteCategory(category); name = "" }) {
                        Text("Delete category", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { if (name.isNotBlank()) viewModel.renameCategory(category, name.trim()) }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissEdit) { Text("Cancel") } },
            shape = RoundedCornerShape(24.dp),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    series: Series,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 80.dp)
                .clip(RoundedCornerShape(10.dp))
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
            Text(series.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            GenreLine(series.genres.take(3).joinToString(" • ").ifBlank { "From your sources" })
        }
        if (selectionMode && selected) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(24.dp),
    )
}
