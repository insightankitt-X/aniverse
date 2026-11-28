package app.aniverse.local.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aniverse.local.domain.model.Series
import app.aniverse.local.ui.theme.AniThemePreview
import app.aniverse.local.ui.theme.LocalReduceMotion
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** Everything a hero page needs. Missing data (rating, year) is simply absent, never faked. */
data class HeroItem(
    val series: Series,
    val inList: Boolean = false,
    val resumeEpisodeId: Long? = null,
)

/**
 * Full-bleed featured banner. The image draws behind the status bar and parallax-shifts as the
 * pager moves; the page body fades into the app background instead of ending on a hard edge.
 */
@Composable
fun HeroBanner(
    items: List<HeroItem>,
    onPlay: (HeroItem) -> Unit,
    onToggleList: (HeroItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(items.size, reduceMotion) {
        if (reduceMotion || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(5_200)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val heroHeight = (maxWidth * 1.16f).coerceIn(430.dp, 560.dp)
        Box(Modifier.fillMaxWidth().height(heroHeight)) {
            HorizontalPager(
                state = pagerState,
                key = { items[it].series.key },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = items[page]
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Scroll-linked offset is read in draw, so a swipe never recomposes the page.
                            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            translationX = offset * size.width * 0.14f
                        },
                ) {
                    if (item.series.thumbnailUrl.isNullOrBlank()) {
                        LetterTile(item.series.title, Modifier.fillMaxSize())
                    } else {
                        AsyncImage(
                            model = item.series.thumbnailUrl,
                            contentDescription = item.series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // The top scrim protects status-bar icons and the floating top bar.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Transparent,
                        ),
                    ),
            )

            // The bottom scrim carries the content into the page background.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
            )

            val page = pagerState.currentPage.coerceIn(0, items.lastIndex)
            HeroPage(
                item = items[page],
                onPlay = { onPlay(items[page]) },
                onToggleList = { onToggleList(items[page]) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 34.dp),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEachIndexed { index, _ ->
                    val width by animateDpAsState(
                        targetValue = if (index == pagerState.currentPage) 18.dp else 6.dp,
                        animationSpec = tween(220),
                        label = "dotWidth",
                    )
                    Box(
                        Modifier
                            .width(width)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPage(
    item: HeroItem,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(18.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "FEATURED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.series.title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.series.genres.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            GenreLine(
                item.series.genres.take(4).joinToString(" • "),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = if (item.resumeEpisodeId != null) "Resume" else "Play",
                onClick = onPlay,
                icon = Icons.Outlined.PlayArrow,
                style = PillStyle.Filled,
            )
            PillButton(
                text = if (item.inList) "In My List" else "My List",
                onClick = onToggleList,
                icon = if (item.inList) Icons.Outlined.Check else Icons.Outlined.Add,
                style = PillStyle.Outlined,
            )
        }
    }
}

@Preview
@DarkPreview
@LightPreview
@Composable
private fun HeroPreview() {
    AniThemePreview {
        Box(Modifier.statusBarsPadding()) {
            HeroBanner(
                items = listOf(
                    HeroItem(
                        series = Series(
                            sourcePkg = "demo",
                            sourceId = 1,
                            remoteId = "1",
                            title = "The Lanterns of the North",
                            genres = listOf("Action", "Comedy", "Fantasy", "Romance", "Isekai"),
                        ),
                    ),
                ),
                onPlay = {},
                onToggleList = {},
            )
        }
    }
}
