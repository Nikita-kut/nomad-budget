package ru.nomadbudget.presentation.charts

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.Zoom
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.columnSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.data.lineSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.LineComponent
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.multiplatform.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.multiplatform.common.component.ShapeComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import androidx.compose.foundation.shape.CircleShape
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent
import com.patrykandpatrick.vico.multiplatform.common.Insets
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import ru.nomadbudget.presentation.format.MoneyFormat
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.Axis
import kotlin.math.pow

private val CHART_HEIGHT = 220.dp
private val COLUMN_THICKNESS = 10.dp

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
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(series) {
        if (series.isNotEmpty() && series.all { it.isNotEmpty() }) {
            producer.runTransaction { columnSeries { series.forEach { series(it) } } }
        }
    }
    val (range, axis) = rememberNiceAxis(series.flatten(), includeZero = true, formatY = formatY)
    key(visibleCount) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        colors.map { LineComponent(fill = Fill(it), thickness = COLUMN_THICKNESS) },
                    ),
                    rangeProvider = range,
                ),
                startAxis = axis,
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = CartesianValueFormatter { _, value, _ -> labels.getOrNull(value.toInt()).orEmpty() },
                ),
                marker = rememberHoverMarker(labels, formatValue, names),
                markerController = CartesianMarkerController.rememberShowOnHover(),
            ),
            modelProducer = producer,
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.x(visibleCount.toDouble())),
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
        )
    }
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
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        if (values.isNotEmpty()) {
            producer.runTransaction { lineSeries { series(values) } }
        }
    }
    val (rangeProvider, axis) = rememberNiceAxis(values, includeZero = !fitToData, formatY = formatY)
    key(visibleCount) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.Line(fill = LineCartesianLayer.LineFill.single(Fill(color))),
                    ),
                    rangeProvider = rangeProvider,
                ),
                startAxis = axis,
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = CartesianValueFormatter { _, value, _ -> labels.getOrNull(value.toInt()).orEmpty() },
                ),
                marker = rememberHoverMarker(labels, formatValue, names),
                markerController = CartesianMarkerController.rememberShowOnHover(),
            ),
            modelProducer = producer,
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.x(visibleCount.toDouble())),
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
        )
    }
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
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(series) {
        if (series.isNotEmpty() && series.all { it.isNotEmpty() }) {
            producer.runTransaction { lineSeries { series.forEach { series(it) } } }
        }
    }
    val (rangeProvider, axis) = rememberNiceAxis(series.flatten(), includeZero = false, formatY = formatY)
    key(visibleCount, colors.size) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        colors.map { LineCartesianLayer.Line(fill = LineCartesianLayer.LineFill.single(Fill(it))) },
                    ),
                    rangeProvider = rangeProvider,
                ),
                startAxis = axis,
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = CartesianValueFormatter { _, value, _ -> labels.getOrNull(value.toInt()).orEmpty() },
                ),
                marker = rememberHoverMarker(labels, formatValue, names),
                markerController = CartesianMarkerController.rememberShowOnHover(),
            ),
            modelProducer = producer,
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.x(visibleCount.toDouble())),
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
        )
    }
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
    return NiceScale(kotlin.math.floor(lo / step) * step, kotlin.math.ceil(hi / step) * step, step)
}

private fun niceStep(raw: Double): Double {
    val exponent = kotlin.math.floor(kotlin.math.log10(raw))
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

@Composable
private fun rememberNiceAxis(values: List<Double>, includeZero: Boolean, formatY: (Double) -> String): Pair<CartesianLayerRangeProvider, VerticalAxis<Axis.Position.Vertical.Start>> {
    val scale = remember(values, includeZero) { niceScale(values, includeZero) }
    val range = remember(scale) { scale?.let { CartesianLayerRangeProvider.fixed(minY = it.min, maxY = it.max) } ?: CartesianLayerRangeProvider.auto() }
    val decimals = scale?.step?.takeIf { it < 1.0 }?.let { kotlin.math.ceil(-kotlin.math.log10(it) - STEP_EPSILON).toInt().coerceIn(0, MAX_AXIS_DECIMALS) }
    val axis = VerticalAxis.rememberStart(
        valueFormatter = CartesianValueFormatter { _, value, _ -> if (decimals != null && decimals > 0) MoneyFormat.formatNumber(value, decimals) else formatY(value) },
        itemPlacer = remember(scale) { VerticalAxis.ItemPlacer.step(step = { scale?.step }) },
    )
    return range to axis
}

private const val TARGET_TICKS = 4.0
private const val STEP_EPSILON = 1e-9
private const val MAX_AXIS_DECIMALS = 6
private const val FLAT_PADDING = 0.05

@Composable
private fun rememberHoverMarker(labels: List<String>, formatY: (Double) -> String, names: List<String>): CartesianMarker {
    val ring = MaterialTheme.colorScheme.surface
    val label = rememberTextComponent(
        style = TextStyle(color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 12.sp),
        padding = Insets(10.dp, 6.dp),
        background = rememberShapeComponent(fill = Fill(MaterialTheme.colorScheme.inverseSurface), shape = RoundedCornerShape(8.dp)),
    )
    val guideline = rememberLineComponent(fill = Fill(MaterialTheme.colorScheme.outline), thickness = 1.dp)
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
            val x = targets.firstOrNull()?.x?.toInt()
            val values = targets.flatMap { target ->
                when (target) {
                    is LineCartesianLayerMarkerTarget -> target.points.map { it.entry.y }
                    is ColumnCartesianLayerMarkerTarget -> target.columns.map { it.entry.y }
                    else -> emptyList()
                }
            }
            val lines = values.mapIndexed { index, value -> names.getOrNull(index)?.let { "$it: ${formatY(value)}" } ?: formatY(value) }
            (listOfNotNull(x?.let { labels.getOrNull(it) }) + lines).joinToString("  ·  ")
        },
        indicator = { color -> ShapeComponent(fill = Fill(color), shape = CircleShape, strokeFill = Fill(ring), strokeThickness = 2.dp) },
        indicatorSize = 10.dp,
        guideline = guideline,
    )
}

