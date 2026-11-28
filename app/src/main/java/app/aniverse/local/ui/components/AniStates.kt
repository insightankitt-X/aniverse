package app.aniverse.local.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.aniverse.local.ui.theme.AniThemePreview
import app.aniverse.local.ui.theme.LocalAniDark

@Composable
fun ShimmerPoster(
    modifier: Modifier = Modifier,
    width: Int = 132,
    showTitle: Boolean = true,
) {
    Column(modifier.width(width.dp)) {
        ShimmerBox(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        if (showTitle) {
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.8f).height(12.dp))
            Spacer(Modifier.height(5.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(10.dp))
        }
    }
}

@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ShimmerBox(
            Modifier
                .fillMaxWidth()
                .height(470.dp)
                .then(Modifier.padding(0.dp)),
        )
        Spacer(Modifier.height(18.dp))
        ShimmerBox(Modifier.padding(horizontal = 20.dp).width(170.dp).height(24.dp))
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) { ShimmerPoster(width = 132) }
        }
        Spacer(Modifier.height(24.dp))
        ShimmerBox(Modifier.padding(horizontal = 20.dp).width(140.dp).height(24.dp))
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) { ShimmerPoster(width = 132) }
        }
    }
}

@Composable
fun PosterGridSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 6,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(count) {
            Column {
                ShimmerBox(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clipShimmer(),
                )
                Spacer(Modifier.height(8.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.75f).height(12.dp))
            }
        }
    }
}

private fun Modifier.clipShimmer(): Modifier = clip(RoundedCornerShape(12.dp))

@androidx.compose.ui.tooling.preview.Preview
@DarkPreview
@LightPreview
@Composable
private fun StatesPreview() {
    AniThemePreview {
        if (LocalAniDark.current) {
            HomeSkeleton()
        } else {
            Box(Modifier.fillMaxSize()) { PosterGridSkeleton() }
        }
    }
}
