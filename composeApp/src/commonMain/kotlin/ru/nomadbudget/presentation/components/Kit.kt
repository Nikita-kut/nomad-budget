package ru.nomadbudget.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ru.nomadbudget.presentation.theme.AppTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.nomadbudget.presentation.charts.ChartLegend
import ru.nomadbudget.presentation.charts.LegendEntry

object Ui {
    val Gap: Dp = 12.dp
    val CardPadding: Dp = 14.dp
    val AmountColumn: Dp = 116.dp
    val TileMinHeight: Dp = 132.dp
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
    badge: (@Composable () -> Unit)? = null,
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
        Column(modifier = Modifier.padding(Ui.CardPadding + 2.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    info?.let { InfoHint(label, it) }
                }
                badge?.invoke()
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, color = if (accent) hero.onHero else valueColor ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
            TileTrack(progress, if (accent) hero.onHero else progressColor, marker, onHero = accent)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = if (accent) hero.heroMuted else subColor ?: MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TileTrack(progress: Float?, color: Color, marker: Float?, onHero: Boolean = false) {
    Box(modifier = Modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.CenterStart) {
        if (progress != null) {
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (onHero) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)) {
                Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
            }
            marker?.let { share ->
                Box(modifier = Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(10.dp), contentAlignment = Alignment.CenterEnd) {
                    Box(modifier = Modifier.width(2.dp).height(10.dp).background(if (onHero) Color.White else MaterialTheme.colorScheme.onSurfaceVariant))
                }
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
        Column(verticalArrangement = Arrangement.spacedBy(Ui.Gap)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
    }
}

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
    defaultPeriod: Int = 0,
    minVisible: Int = MIN_VISIBLE,
    stateKey: Any = title,
    chart: @Composable (from: Int, visibleCount: Int) -> Unit,
) {
    var periodIndex by remember(stateKey) { mutableIntStateOf(defaultPeriod.coerceIn(0, periods.lastIndex)) }
    val count = (periods.getOrNull(periodIndex)?.points ?: totalPoints).coerceAtMost(totalPoints).coerceAtLeast(1)
    var zoom by remember(stateKey, periodIndex) { mutableStateOf<Int?>(null) }
    val lower = minVisible.coerceAtMost(count)
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
                if (visible < count) "на экране $visible из $count · листай график по горизонтали · наведи, чтобы увидеть значение" else "наведи на график, чтобы увидеть значение",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RowScope.HeaderCell(text: String, width: Dp = Ui.AmountColumn) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(width), textAlign = TextAlign.End, maxLines = 1)
}

@Composable
fun RowScope.AmountCell(
    text: String,
    color: Color? = null,
    bold: Boolean = false,
    width: Dp = Ui.AmountColumn,
    muted: Boolean = false,
) {
    Text(
        text,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        color = color ?: if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.width(width),
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

private const val MIN_VISIBLE = 3
private const val ZOOM_STEPS = 4
