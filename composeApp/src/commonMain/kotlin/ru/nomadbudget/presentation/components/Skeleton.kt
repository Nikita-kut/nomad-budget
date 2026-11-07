package ru.nomadbudget.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LocalShimmer = staticCompositionLocalOf<Brush?> { null }

@Composable
fun Shimmer(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceBright
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -SHIMMER_WIDTH,
        targetValue = SHIMMER_TRAVEL,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "offset",
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = androidx.compose.ui.geometry.Offset(offset, 0f),
        end = androidx.compose.ui.geometry.Offset(offset + SHIMMER_WIDTH, SHIMMER_WIDTH / 2),
    )
    CompositionLocalProvider(LocalShimmer provides brush, content = content)
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 14.dp, shape: Shape = RoundedCornerShape(6.dp)) {
    val brush = LocalShimmer.current
    val box = modifier.height(height).clip(shape)
    Box(modifier = if (brush != null) box.background(brush) else box.background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

@Composable
fun SkeletonTile(modifier: Modifier = Modifier) {
    Card(modifier = modifier.heightIn(min = Ui.TileMinHeight)) {
        Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(Modifier.width(110.dp), height = 12.dp)
            SkeletonBlock(Modifier.width(150.dp), height = 26.dp)
            SkeletonBlock(Modifier.fillMaxWidth(), height = 8.dp)
            SkeletonBlock(Modifier.fillMaxWidth(0.7f), height = 12.dp)
        }
    }
}

@Composable
fun SkeletonTable(rows: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Ui.CardPadding, vertical = 6.dp)) {
            repeat(rows) { index ->
                if (index > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(Modifier.width(if (index % 2 == 0) 160.dp else 120.dp))
                    Spacer(Modifier.weight(1f))
                    SkeletonBlock(Modifier.width(Ui.AmountColumn - 24.dp))
                    Spacer(Modifier.width(24.dp))
                    SkeletonBlock(Modifier.width(Ui.AmountColumn - 24.dp))
                }
            }
        }
    }
}

@Composable
fun LoadingSkeleton(wide: Boolean, modifier: Modifier = Modifier) {
    Shimmer {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = SKELETON_MAX_WIDTH).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(Ui.Gap),
            ) {
                SkeletonBlock(Modifier.width(180.dp), height = 18.dp)
                TileRow(wide, List(if (wide) 3 else 2) { { m: Modifier -> SkeletonTile(m) } })
                SkeletonBlock(Modifier.width(140.dp), height = 18.dp)
                SkeletonTable(rows = if (wide) 6 else 5)
            }
        }
    }
}

@Composable
fun HeaderSkeleton() {
    Shimmer {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(HEADER_ITEMS) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(Modifier.width(48.dp), height = 10.dp)
                    SkeletonBlock(Modifier.width(if (it == 0) 104.dp else 84.dp), height = 18.dp)
                }
            }
        }
    }
}

private const val SHIMMER_WIDTH = 600f
private const val SHIMMER_TRAVEL = 2400f
private const val SHIMMER_MS = 1300
private const val HEADER_ITEMS = 4
private val SKELETON_MAX_WIDTH = 1240.dp
