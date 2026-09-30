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
    modifier: Modifier = Modifier,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(series) {
        if (series.isNotEmpty() && series.all { it.isNotEmpty() }) {
            producer.runTransaction { columnSeries { series.forEach { series(it) } } }
        }
    }
    key(visibleCount) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        colors.map { LineComponent(fill = Fill(it), thickness = COLUMN_THICKNESS) },
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(valueFormatter = CartesianValueFormatter { _, value, _ -> formatY(value) }),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = CartesianValueFormatter { _, value, _ -> labels.getOrNull(value.toInt()).orEmpty() },
                ),
            ),
            modelProducer = producer,
            zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = Zoom.x(visibleCount.toDouble())),
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
    modifier: Modifier = Modifier,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        if (values.isNotEmpty()) {
            producer.runTransaction { lineSeries { series(values) } }
        }
    }
    key(visibleCount) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.Line(fill = LineCartesianLayer.LineFill.single(Fill(color))),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(valueFormatter = CartesianValueFormatter { _, value, _ -> formatY(value) }),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = CartesianValueFormatter { _, value, _ -> labels.getOrNull(value.toInt()).orEmpty() },
                ),
            ),
            modelProducer = producer,
            zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = Zoom.x(visibleCount.toDouble())),
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
        )
    }
}
