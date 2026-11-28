package app.aniverse.local.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aniverse.local.ui.theme.AniThemePreview

data class AniTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val badge: Int = 0,
)

val AniTabs = listOf(
    AniTab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    AniTab("schedule", "Schedule", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    AniTab("library", "My List", Icons.Outlined.Bookmarks, Icons.Filled.Bookmarks),
    AniTab("profile", "Profile", Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun AniBottomBar(
    tabs: List<AniTab>,
    currentRoute: String?,
    onSelect: (AniTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(66.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val content by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(220),
                        label = "tabContent",
                    )
                    val pill by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                        },
                        animationSpec = tween(220),
                        label = "tabPill",
                    )
                    val pillWidth by animateDpAsState(
                        targetValue = if (selected) 48.dp else 34.dp,
                        animationSpec = tween(240),
                        label = "tabPillWidth",
                    )
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(tab)
                            }
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .width(pillWidth)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(pill),
                            )
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label,
                                tint = content,
                                modifier = Modifier.size(23.dp),
                            )
                            if (tab.badge > 0) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(end = 5.dp, top = 1.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = content,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@DarkPreview
@LightPreview
@Composable
private fun BottomBarPreview() {
    AniThemePreview {
        AniBottomBar(tabs = AniTabs, currentRoute = "home", onSelect = {})
    }
}
