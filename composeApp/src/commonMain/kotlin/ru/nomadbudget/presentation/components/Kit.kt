package ru.nomadbudget.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.nomadbudget.presentation.charts.ChartLegend
import ru.nomadbudget.presentation.charts.LegendEntry
import ru.nomadbudget.presentation.theme.AppTheme

object Ui {
    val Gap: Dp = 12.dp
    val CardPadding: Dp = 14.dp
    val SideGutter: Dp = 12.dp
    val ScrollBottomPadding: Dp = 96.dp
    val AmountColumn: Dp = 116.dp
    val WideAmountColumn: Dp = 140.dp
    val TileMinHeight: Dp = 132.dp
    val ContentMaxWidth: Dp = 1240.dp
    val NarrowContentMaxWidth: Dp = 760.dp
    val FormMaxWidth: Dp = 640.dp
}

@Composable
fun StatTile(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    marker: Float? = null,
    accent: Boolean = false,
    valueColor: Color? = null,
    info: String? = null,
    subColor: Color? = null,
) {
    val hero = AppTheme.colors
    val shape = MaterialTheme.shapes.large
    val cardModifier = if (accent) modifier.clip(shape).background(Brush.linearGradient(hero.hero)) else modifier
    Card(
        modifier = cardModifier,
        shape = shape,
        colors = if (accent) CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = hero.onHero) else CardDefaults.cardColors(),
    ) {
        val labelColor = if (accent) hero.heroMuted else MaterialTheme.colorScheme.onSurfaceVariant
        val compact = LocalCompactTiles.current
        Column(modifier = Modifier.padding(if (compact) Ui.CardPadding - 2.dp else Ui.CardPadding + 2.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, color = labelColor, maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                info?.let { InfoHint(label, it) }
            }
            Text(value, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, color = if (accent) hero.onHero else valueColor ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (!compact || progress != null) {
                Box(modifier = Modifier.height(TILE_TRACK_HEIGHT), contentAlignment = Alignment.CenterStart) {
                    if (progress != null) {
                        ProgressTrack(
                            fraction = progress,
                            marker = marker,
                            color = if (accent) hero.onHero else progressColor,
                            trackColor = if (accent) hero.onHero.copy(alpha = HERO_TRACK_ALPHA) else MaterialTheme.colorScheme.surfaceVariant,
                            markerColor = if (accent) hero.onHero else MaterialTheme.colorScheme.onSurfaceVariant,
                            height = TILE_TRACK_HEIGHT,
                        )
                    }
                }
            }
            Text(
                sub,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = if (accent) hero.heroMuted else subColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = if (compact) 1 else 2,
                maxLines = if (compact) 4 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ProgressTrack(
    fraction: Float,
    marker: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    markerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    height: Dp = TRACK_HEIGHT,
) {
    Box(modifier = modifier.fillMaxWidth().height(height), contentAlignment = Alignment.CenterStart) {
        Box(modifier = Modifier.fillMaxWidth().height(TRACK_BAR_HEIGHT).clip(TRACK_SHAPE).background(trackColor)) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(TRACK_SHAPE).background(color))
        }
        marker?.let { share ->
            Box(modifier = Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(height), contentAlignment = Alignment.CenterEnd) {
                Box(modifier = Modifier.width(MARKER_WIDTH).height(height).background(markerColor))
            }
        }
    }
}

@Composable
fun TileRow(wide: Boolean, tiles: List<@Composable (Modifier) -> Unit>) {
    if (wide) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Ui.Gap)) {
            tiles.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
        }
    } else {
        CompositionLocalProvider(LocalCompactTiles provides true) {
            Column(verticalArrangement = Arrangement.spacedBy(COMPACT_GAP)) {
                CompositionLocalProvider(LocalCompactTiles provides false) { tiles.firstOrNull()?.invoke(Modifier.fillMaxWidth()) }
                tiles.drop(1).chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(COMPACT_GAP)) {
                        pair.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
        }
    }
}

private val LocalCompactTiles = staticCompositionLocalOf { false }

private val COMPACT_GAP = 8.dp
private val TRACK_HEIGHT = 12.dp
private val TILE_TRACK_HEIGHT = 10.dp
private val TRACK_BAR_HEIGHT = 8.dp
private val MARKER_WIDTH = 2.dp
private val TRACK_SHAPE = RoundedCornerShape(4.dp)
private const val HERO_TRACK_ALPHA = 0.25f

@Composable
fun EqualRow(wide: Boolean, cells: List<@Composable ColumnScope.() -> Unit>) {
    if (wide) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Ui.Gap)) {
            cells.forEach { cell -> Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp), content = cell) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Ui.Gap)) { cells.forEach { cell -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = cell) } }
    }
}

data class ChartPeriod(val label: String, val points: Int?)

@Composable
fun ChartPanel(
    title: String,
    legend: List<LegendEntry>,
    totalPoints: Int,
    periods: List<ChartPeriod>,
    modifier: Modifier = Modifier,
    hint: String? = null,
    info: String? = null,
    stateKey: Any = title,
    chart: @Composable (from: Int, visibleCount: Int) -> Unit,
) {
    var periodIndex by remember(stateKey) { mutableIntStateOf(0) }
    val count = (periods.getOrNull(periodIndex)?.points ?: totalPoints).coerceAtMost(totalPoints).coerceAtLeast(1)
    var zoom by remember(stateKey, periodIndex) { mutableStateOf<Int?>(null) }
    val lower = MIN_VISIBLE.coerceAtMost(count)
    val visible = (zoom ?: count).coerceIn(lower, count)
    val step = (count / ZOOM_STEPS).coerceAtLeast(1)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        info?.let { InfoHint(title, it) }
                    }
                    hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                periods.forEachIndexed { index, period ->
                    FilterChip(selected = index == periodIndex, onClick = { periodIndex = index }, label = { Text(period.label) })
                }
                OutlinedIconButton(onClick = { zoom = (visible + step).coerceAtMost(count) }, enabled = visible < count, modifier = Modifier.size(32.dp)) { Text("−") }
                OutlinedIconButton(onClick = { zoom = (visible - step).coerceAtLeast(lower) }, enabled = visible > lower, modifier = Modifier.size(32.dp)) { Text("+") }
            }
            ChartLegend(legend)
            chart(totalPoints - count, visible)
            Text(
                if (visible < count) "показаны последние $visible из $count · нажми или наведи на график, чтобы увидеть значение" else "нажми или наведи на график, чтобы увидеть значение",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RowScope.HeaderCell(text: String, width: Dp? = Ui.AmountColumn) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = cellModifier(width), textAlign = TextAlign.End, maxLines = 1)
}

@Composable
fun RowScope.AmountCell(
    text: String,
    color: Color? = null,
    bold: Boolean = false,
    width: Dp? = Ui.AmountColumn,
    muted: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text,
        style = style,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        color = color ?: if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = cellModifier(width),
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

private fun RowScope.cellModifier(width: Dp?): Modifier = if (width == null) Modifier.weight(1f) else Modifier.width(width)

private const val MIN_VISIBLE = 3
private const val ZOOM_STEPS = 4
