package app.aniverse.local.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aniverse.local.domain.model.Series
import app.aniverse.local.ui.theme.AniThemePreview
import app.aniverse.local.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

@Composable
fun GenreLine(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Staggers the first appearance of a list item only. Saveable state means scrolling back does not
 * replay the animation, and reduced motion shows everything immediately.
 */
@Composable
fun Reveal(index: Int, content: @Composable () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    var visible by rememberSaveable { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        if (!visible) {
            delay((index.coerceAtMost(10) * 32).toLong())
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)) + slideInVertically(tween(280)) { it / 8 },
    ) {
        content()
    }
}

@Composable
fun PosterRow(
    title: String,
    items: List<Series>,
    onItem: (Series) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            actionLabel = actionLabel,
            onAction = onAction,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { _, series -> series.key },
                contentType = { _, _ -> "poster" },
            ) { index, series ->
                Reveal(index) {
                    PosterCard(
                        title = series.title,
                        thumbnailUrl = series.thumbnailUrl,
                        onClick = { onItem(series) },
                    )
                }
            }
        }
    }
}

/**
 * The reference grid: two columns, generous gaps, 12dp posters. Rating/HD chips appear only when
 * the caller has real data for them.
 */
@Composable
fun PosterGrid(
    items: List<Series>,
    onItem: (Series) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    header: (@Composable () -> Unit)? = null,
    onLongItem: ((Series) -> Unit)? = null,
    rating: (Series) -> String? = { null },
    hd: (Series) -> Boolean = { false },
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(
            items = items,
            key = { it.key },
            contentType = { "poster" },
        ) { series ->
            PosterCard(
                title = series.title,
                thumbnailUrl = series.thumbnailUrl,
                onClick = { onItem(series) },
                onLongClick = onLongItem?.let { { it(series) } },
                rating = rating(series),
                hd = hd(series),
                width = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
            )
        }
    }
}

/** A compact source row used on Home and in search results. */
@Composable
fun SourcePill(name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            maxLines = 1,
        )
    }
}

@Preview
@DarkPreview
@LightPreview
@Composable
private fun RowsPreview() {
    AniThemePreview {
        Column {
            PosterRow(
                title = "Top Airing",
                subtitle = "Updated today",
                items = PreviewSeries,
                onItem = {},
                actionLabel = "See all",
                onAction = {},
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = 20.dp)) {
                SourcePill("AnimePahe", selected = true, onClick = {})
                Spacer(Modifier.width(8.dp))
                SourcePill("Gogoanime", selected = false, onClick = {})
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

internal val PreviewSeries = listOf(
    Series(sourcePkg = "demo", sourceId = 1, remoteId = "a", title = "The Lanterns of the North"),
    Series(sourcePkg = "demo", sourceId = 1, remoteId = "b", title = "Paper Cranes at Dawn"),
    Series(sourcePkg = "demo", sourceId = 1, remoteId = "c", title = "Signal in the Rain"),
)
