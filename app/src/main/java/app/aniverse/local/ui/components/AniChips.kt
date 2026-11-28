package app.aniverse.local.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aniverse.local.domain.model.SeriesStatus
import app.aniverse.local.ui.theme.AniThemePreview
import app.aniverse.local.ui.theme.LocalAniExtendedColors

/**
 * The age-rating chip is deliberately white with near-black text in every palette, matching the
 * premium streaming look while keeping contrast independent of the accent.
 */
@Composable
fun RatingChip(text: String, modifier: Modifier = Modifier) {
    val colors = LocalAniExtendedColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = colors.chipFill,
        contentColor = colors.chipText,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** HD is shown only when the source actually reports it. It recolours with the theme accent. */
@Composable
fun HdChip(modifier: Modifier = Modifier) {
    val colors = LocalAniExtendedColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = colors.hdFill,
        contentColor = colors.hdText,
    ) {
        Text(
            text = "HD",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun GenreChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = content,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun StatusChip(status: SeriesStatus, modifier: Modifier = Modifier) {
    val label = when (status) {
        SeriesStatus.ONGOING -> "Airing"
        SeriesStatus.COMPLETED -> "Completed"
        SeriesStatus.LICENSED -> "Licensed"
        SeriesStatus.PUBLISHING_FINISHED -> "Finished"
        SeriesStatus.CANCELLED -> "Cancelled"
        SeriesStatus.ON_HIATUS -> "Hiatus"
        SeriesStatus.UNKNOWN -> "Unknown"
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Preview
@DarkPreview
@LightPreview
@Composable
private fun ChipsPreview() {
    AniThemePreview {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            RatingChip("PG-13")
            HdChip()
            GenreChip("Action", selected = true)
            GenreChip("Fantasy")
            StatusChip(SeriesStatus.ONGOING)
        }
    }
}
