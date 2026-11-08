package ru.nomadbudget.presentation.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nomadbudget.presentation.format.MoneyFormat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val CHART_HEIGHT = 220.dp

data class LegendEntry(val label: String, val color: Color)

@Composable
fun ChartLegend(entries: List<LegendEntry>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(10.dp).background(entry.color, RoundedCornerShape(2.dp)))
                Text(entry.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ColumnsChart(
    series: List<List<Double>>,
    colors: List<Color>,
    labels: List<String>,
    formatY: (Double) -> String,
    visibleCount: Int,
    formatValue: (Double) -> String = formatY,
    names: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    PlotChart(series, colors, labels, formatY, formatValue, names, visibleCount, includeZero = true, columns = true, modifier = modifier)
}

@Composable
fun LineChart(
    values: List<Double>,
    color: Color,
    labels: List<String>,
    formatY: (Double) -> String,
    visibleCount: Int,
    formatValue: (Double) -> String = formatY,
    names: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    fitToData: Boolean = false,
) {
    PlotChart(listOf(values), listOf(color), labels, formatY, formatValue, names, visibleCount, includeZero = !fitToData, columns = false, modifier = modifier)
}

@Composable
fun MultiLineChart(
    series: List<List<Double>>,
    colors: List<Color>,
    labels: List<String>,
    formatY: (Double) -> String,
    visibleCount: Int,
    formatValue: (Double) -> String = formatY,
    names: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    PlotChart(series, colors, labels, formatY, formatValue, names, visibleCount, includeZero = false, columns = false, modifier = modifier)
}

@Composable
private fun PlotChart(
    allSeries: List<List<Double>>,
    colors: List<Color>,
    allLabels: List<String>,
    formatY: (Double) -> String,
    formatValue: (Double) -> String,
    names: List<String>,
    visibleCount: Int,
    includeZero: Boolean,
    columns: Boolean,
    modifier: Modifier,
) {
    val total = allSeries.maxOfOrNull { it.size } ?: 0
    val count = visibleCount.coerceIn(1, total.coerceAtLeast(1))
    val series = remember(allSeries, count) { allSeries.map { it.takeLast(count) } }
    val labels = remember(allLabels, count) { allLabels.takeLast(count) }
    val scale = remember(series, includeZero) { niceScale(series.flatten(), includeZero) }
    val measurer = rememberTextMeasurer()
    val colorsScheme = MaterialTheme.colorScheme
    val axisStyle = TextStyle(color = colorsScheme.onSurfaceVariant, fontSize = 11.sp)
    val tooltipStyle = TextStyle(color = colorsScheme.inverseOnSurface, fontSize = 12.sp)
    val gridColor = colorsScheme.outlineVariant
    val guideColor = colorsScheme.outline
    val tooltipColor = colorsScheme.inverseSurface
    val ringColor = colorsScheme.surface
    val fallbackColor = colorsScheme.outline
    var hover by remember { mutableStateOf<Int?>(null) }
    val points = series.firstOrNull()?.size ?: 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .pointerInput(points) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position ?: break
                        hover = when (event.type) {
                            PointerEventType.Exit -> null
                            else -> indexAt(position.x, size.width.toFloat(), points)
                        }
                        if (event.type == PointerEventType.Exit) break
                    }
                }
            }
            .drawWithCache {
                val layout = scale?.let { buildLayout(it, series, labels, columns, formatY, measurer, axisStyle, size) }
                val linePaths = layout?.let { l -> if (columns) emptyList() else series.map { values -> linePath(values, l) } }
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    val l = layout ?: return@onDrawBehind
                    l.ticks.forEach { tick ->
                        val y = l.y(tick.value)
                        drawLine(gridColor, Offset(l.left, y), Offset(l.right, y), strokeWidth = 1f)
                        drawText(tick.text, topLeft = Offset(l.left - tick.text.size.width - 8f, y - tick.text.size.height / 2f))
                    }
                    l.xLabels.forEach { (index, text) ->
                        val x = l.x(index)
                        drawText(text, topLeft = Offset((x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width), l.bottom + 6f))
                    }
                    if (columns) {
                        val group = l.slot * COLUMN_GROUP_SHARE
                        val bar = (group / series.size.coerceAtLeast(1)).coerceAtMost(COLUMN_MAX.toPx())
                        val zeroY = l.y(0.0.coerceIn(l.scale.min, l.scale.max))
                        series.forEachIndexed { s, values ->
                            values.forEachIndexed { i, v ->
                                val center = l.x(i)
                                val x = center - bar * series.size / 2f + s * bar + bar * 0.1f
                                val y = l.y(v)
                                val top = minOf(y, zeroY)
                                val height = kotlin.math.abs(zeroY - y).coerceAtLeast(1f)
                                drawRoundRect(colors.getOrElse(s) { fallbackColor }, Offset(x, top), Size(bar * 0.8f, height), CornerRadius(3f, 3f))
                            }
                        }
                    } else {
                        linePaths?.forEachIndexed { s, path ->
                            val color = colors.getOrElse(s) { fallbackColor }
                            if (series[s].size == 1) drawCircle(color, 4.dp.toPx(), Offset(l.x(0), l.y(series[s][0])))
                            else drawPath(path, color, style = stroke)
                        }
                    }
                    val index = hover ?: return@onDrawBehind
                    if (index !in 0 until points) return@onDrawBehind
                    val x = l.x(index)
                    drawLine(guideColor, Offset(x, l.top), Offset(x, l.bottom), strokeWidth = 1f)
                    if (!columns) {
                        series.forEachIndexed { s, values ->
                            values.getOrNull(index)?.let { v ->
                                val center = Offset(x, l.y(v))
                                drawCircle(ringColor, 6.dp.toPx(), center)
                                drawCircle(colors.getOrElse(s) { fallbackColor }, 4.dp.toPx(), center)
                            }
                        }
                    }
                    val parts = series.mapIndexedNotNull { s, values ->
                        values.getOrNull(index)?.let { v -> names.getOrNull(s)?.let { "$it: ${formatValue(v)}" } ?: formatValue(v) }
                    }
                    val text = measurer.measure((listOfNotNull(labels.getOrNull(index)) + parts).joinToString("  ·  "), tooltipStyle)
                    val padX = 10.dp.toPx()
                    val padY = 6.dp.toPx()
                    val w = text.size.width + padX * 2
                    val h = text.size.height + padY * 2
                    val boxX = (x - w / 2f).coerceIn(0f, size.width - w)
                    val boxY = (l.top - h - 4f).coerceAtLeast(0f)
                    drawRoundRect(tooltipColor, Offset(boxX, boxY), Size(w, h), CornerRadius(8.dp.toPx()))
                    drawText(text, topLeft = Offset(boxX + padX, boxY + padY))
                }
            },
    )
}

private class Tick(val value: Double, val text: TextLayoutResult)

private class PlotLayout(
    val scale: NiceScale,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val count: Int,
    val ticks: List<Tick>,
    val xLabels: List<Pair<Int, TextLayoutResult>>,
    val columns: Boolean,
) {
    val slot: Float get() = (right - left) / count.coerceAtLeast(1)

    fun x(index: Int): Float = if (columns || count <= 1) left + slot * (index + 0.5f) else left + (right - left) * index / (count - 1)

    fun y(value: Double): Float {
        val share = ((value - scale.min) / (scale.max - scale.min)).toFloat()
        return bottom - (bottom - top) * share
    }
}

private fun buildLayout(
    scale: NiceScale,
    series: List<List<Double>>,
    labels: List<String>,
    columns: Boolean,
    formatY: (Double) -> String,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    size: Size,
): PlotLayout {
    val decimals = scale.step.takeIf { it < 1.0 }?.let { ceil(-log10(it) - STEP_EPSILON).toInt().coerceIn(0, MAX_AXIS_DECIMALS) }
    val tickValues = generateSequence(scale.min) { it + scale.step }.takeWhile { it <= scale.max + scale.step * STEP_EPSILON }.toList()
    val tickTexts = tickValues.map { v -> measurer.measure(if (decimals != null && decimals > 0) MoneyFormat.formatNumber(v, decimals) else formatY(v), style) }
    val left = (tickTexts.maxOfOrNull { it.size.width } ?: 0) + 12f
    val labelHeight = measurer.measure("0", style).size.height
    val top = TOP_PADDING + labelHeight / 2f + TOOLTIP_SPACE
    val bottom = size.height - labelHeight - 8f
    val right = size.width - 6f
    val count = series.maxOfOrNull { it.size } ?: 0
    val maxLabel = labels.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
    val slot = (right - left) / count.coerceAtLeast(1)
    val every = ceil((maxLabel + LABEL_GAP) / slot.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
    val xLabels = labels.indices.filter { (count - 1 - it) % every == 0 }.map { it to measurer.measure(labels[it], style) }
    return PlotLayout(scale, left, right, top, bottom, count, tickValues.zip(tickTexts) { v, t -> Tick(v, t) }, xLabels, columns)
}

private fun linePath(values: List<Double>, layout: PlotLayout): Path = Path().apply {
    values.forEachIndexed { i, v ->
        val x = layout.x(i)
        val y = layout.y(v)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

private fun indexAt(x: Float, width: Float, points: Int): Int? {
    if (points <= 0 || width <= 0f) return null
    return ((x / width) * points).toInt().coerceIn(0, points - 1)
}

data class NiceScale(val min: Double, val max: Double, val step: Double)

fun niceScale(values: List<Double>, includeZero: Boolean): NiceScale? {
    if (values.isEmpty()) return null
    var lo = values.min()
    var hi = values.max()
    if (includeZero) {
        lo = minOf(lo, 0.0)
        hi = maxOf(hi, 0.0)
    }
    if (hi == lo) {
        val pad = if (hi == 0.0) 1.0 else kotlin.math.abs(hi) * FLAT_PADDING
        lo -= pad
        hi += pad
    }
    val step = niceStep((hi - lo) / TARGET_TICKS)
    return NiceScale(floor(lo / step) * step, ceil(hi / step) * step, step)
}

private fun niceStep(raw: Double): Double {
    val exponent = floor(log10(raw))
    val magnitude = 10.0.pow(exponent)
    val fraction = raw / magnitude
    val nice = when {
        fraction < 1.5 -> 1.0
        fraction < 2.25 -> 2.0
        fraction < 3.5 -> 2.5
        fraction < 7.5 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

private const val TARGET_TICKS = 4.0
private const val STEP_EPSILON = 1e-9
private const val MAX_AXIS_DECIMALS = 6
private const val FLAT_PADDING = 0.05
private const val COLUMN_GROUP_SHARE = 0.6f
private val COLUMN_MAX = 14.dp
private const val TOP_PADDING = 4f
private const val TOOLTIP_SPACE = 30f
private const val LABEL_GAP = 16f
