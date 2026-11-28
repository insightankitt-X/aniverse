package app.aniverse.local.ui.screens.updates

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.aniverse.local.domain.model.UpdateFailure
import app.aniverse.local.domain.model.UpdateGroup
import app.aniverse.local.domain.model.UpdateItem
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.ui.components.AniLargeHeader
import app.aniverse.local.ui.components.EmptyState
import app.aniverse.local.ui.components.SectionHeader
import app.aniverse.local.ui.components.formatClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    data class State(
        val groups: List<UpdateGroup> = emptyList(),
        val checking: Boolean = false,
        val progress: Float = 0f,
        val failures: List<UpdateFailure> = emptyList(),
        val lastReport: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.observeUpdates().collect { groups ->
                _state.update { it.copy(groups = groups) }
            }
        }
    }

    fun check() {
        if (_state.value.checking) return
        viewModelScope.launch {
            _state.update { it.copy(checking = true, progress = 0f, failures = emptyList(), lastReport = null) }
            val report = libraryRepository.refreshLibrary { done, total ->
                _state.update { it.copy(progress = if (total > 0) done.toFloat() / total else 0f) }
            }
            _state.update {
                it.copy(
                    checking = false,
                    failures = report.failures,
                    lastReport = "Checked ${report.checked} series, found ${report.newEpisodes} new episode(s)",
                )
            }
        }
    }

    fun clear() {
        viewModelScope.launch { libraryRepository.clearUpdates() }
    }
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private val dayFormatter = DateTimeFormatter.ofPattern("EEE\nd MMM", Locale.getDefault())

private fun LocalDate.range(): LongRange =
    atStartOfDay(zone).toInstant().toEpochMilli() until plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

@Composable
fun UpdatesScreen(
    onOpenSeries: (Long) -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val days = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val todayHasItems = remember(state.groups) {
        state.groups.any { group -> group.items.any { it.discoveredAt.toLocalDate() == today } }
    }
    var selectedDay by rememberSaveable { mutableIntStateOf(-1) }
    var userPicked by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(todayHasItems) {
        if (!userPicked) selectedDay = if (todayHasItems) 6 else -1
    }

    val filtered = remember(state.groups, selectedDay) {
        if (selectedDay < 0) {
            state.groups
        } else {
            val range = days[selectedDay].range()
            state.groups.mapNotNull { group ->
                val matching = group.items.filter { it.discoveredAt in range }
                if (matching.isEmpty()) null else group.copy(items = matching)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        AniLargeHeader(
            title = "Schedule",
            subtitle = "New episodes from your library",
            actions = {
                if (state.checking) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = viewModel::check) {
                        Text("Check now", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            },
        )

        if (state.checking) {
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                DayChip(
                    label = "All",
                    sublabel = "Updates",
                    selected = selectedDay < 0,
                    count = state.groups.sumOf { it.items.size },
                    onClick = { userPicked = true; selectedDay = -1 },
                )
            }
            items(days) { date ->
                val index = days.indexOf(date)
                val count = state.groups.sumOf { group -> group.items.count { it.discoveredAt.toLocalDate() == date } }
                DayChip(
                    label = if (date == today) "Today" else date.format(dayFormatter),
                    sublabel = null,
                    selected = selectedDay == index,
                    count = count,
                    onClick = { userPicked = true; selectedDay = index },
                )
            }
        }

        state.lastReport?.let { report ->
            Text(
                report,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }

        if (state.failures.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Some sources could not be checked", style = MaterialTheme.typography.labelLarge)
                    state.failures.take(3).forEach { failure ->
                        Text(
                            "${failure.seriesTitle}: ${failure.message}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = filtered,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
            },
            label = "schedule",
            modifier = Modifier.fillMaxSize(),
        ) { groups ->
            when {
                state.groups.isEmpty() -> EmptyState(
                    title = "Nothing scheduled yet",
                    message = "AniVerse only knows about episodes your installed sources return. Check your library to fill this week.",
                    actionLabel = "Check now",
                    onAction = viewModel::check,
                )

                groups.isEmpty() -> EmptyState(
                    title = "No episodes this day",
                    message = "Try another day, or check your library again.",
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(groups, key = { it.seriesId }, contentType = { "group" }) { group ->
                        ReleaseCard(group = group, onOpenSeries = onOpenSeries)
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = viewModel::clear, modifier = Modifier.fillMaxWidth()) {
                            Text("Mark all as seen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DayChip(
    label: String,
    sublabel: String?,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .width(74.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            val detail = sublabel ?: if (count > 0) "$count new" else "Free"
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReleaseCard(group: UpdateGroup, onOpenSeries: (Long) -> Unit) {
    val first = group.items.firstOrNull()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSeries(group.seriesId) },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 54.dp, height = 76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (first?.thumbnailUrl != null) {
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(first.thumbnailUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ),
                            ),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.seriesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                group.items.take(3).forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.episodeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatClock(item.discoveredAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (group.items.size > 3) {
                    Text(
                        "+${group.items.size - 3} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
