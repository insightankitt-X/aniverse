package app.aniverse.local.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aniverse.local.ui.theme.AniThemePreview
import app.aniverse.local.ui.theme.pressScale

enum class PillStyle { Filled, Outlined, Tonal }

/**
 * The reference's two hero actions: a filled accent pill and a transparent outlined pill. The
 * outlined variant keeps a 1dp border on any background, including artwork.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: PillStyle = PillStyle.Filled,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentColorOverride: Color? = null,
    containerColor: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val container = containerColor ?: when (style) {
        PillStyle.Filled -> MaterialTheme.colorScheme.primary
        PillStyle.Tonal -> MaterialTheme.colorScheme.surfaceVariant
        PillStyle.Outlined -> Color.Transparent
    }
    val content = contentColorOverride ?: when {
        containerColor != null -> app.aniverse.local.ui.theme.contrastOn(containerColor)
        style == PillStyle.Filled -> MaterialTheme.colorScheme.onPrimary
        style == PillStyle.Tonal -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = when (style) {
        PillStyle.Outlined -> BorderStroke(1.dp, content.copy(alpha = 0.55f))
        else -> null
    }
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled && !loading,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(50),
        color = if (enabled) container else container.copy(alpha = 0.4f),
        contentColor = content,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            }
            if (loading || icon != null) Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** Two to four fixed choices with a soft sliding highlight. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val background by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(220),
                    label = "segmentBackground",
                )
                val content by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(220),
                    label = "segmentContent",
                )
                Box(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .height(38.dp)
                        .background(background, RoundedCornerShape(50))
                        .clickable { onSelect(index) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = content,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Scrollable pill tabs used where the number of categories is dynamic (My List, filters).
 * Keeping each pill a true 48dp target avoids the tiny-tap problem of text-only tabs.
 */
@Composable
fun PillTabRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(options) { index, label ->
            val selected = index == selectedIndex
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.clickable { onSelect(index) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Preview
@DarkPreview
@LightPreview
@Composable
private fun ButtonsPreview() {
    AniThemePreview {
        androidx.compose.foundation.layout.Column(Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Play", {}, icon = Icons.Outlined.PlayArrow)
                PillButton("My List", {}, icon = Icons.Outlined.Add, style = PillStyle.Outlined)
            }
            Spacer(Modifier.height(16.dp))
            SegmentedToggle(listOf("Episodes", "Details"), 0, {})
            Spacer(Modifier.height(16.dp))
            PillTabRow(listOf("Watching", "Plan to Watch", "Completed", "Favorites"), 0, {}, contentPadding = PaddingValues(0.dp))
        }
    }
}
