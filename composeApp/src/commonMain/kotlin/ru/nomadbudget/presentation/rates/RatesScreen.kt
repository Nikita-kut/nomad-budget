package ru.nomadbudget.presentation.rates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.RatePairs
import ru.nomadbudget.domain.logic.RatePoint
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.presentation.charts.LegendEntry
import ru.nomadbudget.presentation.charts.LineChart
import ru.nomadbudget.presentation.components.AmountCell
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.ChartPanel
import ru.nomadbudget.presentation.components.ChartPeriod
import ru.nomadbudget.presentation.components.CurrencyChip
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.EqualRow
import ru.nomadbudget.presentation.components.HeaderCell
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.Ui
import ru.nomadbudget.presentation.components.currencyColor
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

private val RATE_PERIODS = listOf(ChartPeriod("30 дн.", 30), ChartPeriod("90 дн.", 90), ChartPeriod("всё", null))

@Composable
fun RatesScreen(state: HomeState, wide: Boolean = false) {
    val pairs = remember(state.currencies) { RatePairs.pairs(state.currencies) }
    val series = remember(state.rateHistory, pairs) {
        pairs.associateWith { (a, b) -> RatePairs.series(a, b, state.rateHistory) }
    }
    CenteredLazyColumn(maxContentWidth = 1240.dp, bottom = 24.dp) {
        item { SourceLine(state) }
        if (pairs.isEmpty()) item { EmptyHint("Кроме рубля валют нет") }
        pairs.forEach { pair ->
            val points = series.getValue(pair)
            item(key = "pair-${pair.first.code}-${pair.second.code}") { PairBlock(pair.first, pair.second, points, state, wide) }
        }
    }
}

@Composable
private fun SourceLine(state: HomeState) {
    val latest = state.rates.all.maxByOrNull { it.date }
    Text(
        when {
            latest == null -> "Курсов пока нет"
            latest.source == RateSource.MANUAL -> "Курсы по умолчанию: таблица пуста"
            else -> "Источник ${latest.sourceName} · курс за ${DateFormat.dayMonth(latest.date)}" + (latest.fetchedAt?.let { " · получен ${DateFormat.dayMonthTime(it)}" } ?: "") + " · обновляется раз в сутки"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PairBlock(a: Currency, b: Currency, points: List<RatePoint>, state: HomeState, wide: Boolean) {
    val colorA = currencyColor(if (a == Currency.BASE) b else a, state)
    val colorB = currencyColor(if (b == Currency.BASE) a else b, state)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CurrencyChip(a, state)
            Text("⇄", color = MaterialTheme.colorScheme.onSurfaceVariant)
            CurrencyChip(b, state)
            SectionTitle("${a.code} и ${b.code}", hint = if (points.isEmpty()) "нет снимков" else "${points.size} снимков", info = Hints.RATES, modifier = Modifier.weight(1f))
        }
        if (points.size < 2) {
            Text("Для графика нужно хотя бы два снимка курса", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        EqualRow(
            wide,
            listOf(
                { DirectionChart(a, b, points, colorA) },
                { DirectionChart(b, a, points.map { it.copy(value = 1.0 / it.value) }, colorB) },
            ),
        )
        HistoryTable(a, b, points)
    }
}

@Composable
private fun DirectionChart(from: Currency, to: Currency, points: List<RatePoint>, color: androidx.compose.ui.graphics.Color) {
    val last = points.last().value
    ChartPanel(
        title = "1 ${from.symbol} = ${MoneyFormat.formatQuote(last)} ${to.symbol}",
        hint = "сколько ${to.code} за 1 ${from.code}",
        legend = listOf(LegendEntry("по дням", color)),
        totalPoints = points.size,
        periods = RATE_PERIODS,
        modifier = Modifier.fillMaxWidth(),
        stateKey = from.code + to.code,
    ) { start, visible ->
        val slice = points.drop(start)
        LineChart(
            values = slice.map { it.value },
            color = color,
            labels = slice.map { DateFormat.dayMonth(it.date) },
            formatY = { MoneyFormat.formatQuote(it) },
            visibleCount = visible,
            fitToData = true,
        )
    }
}

@Composable
private fun HistoryTable(a: Currency, b: Currency, points: List<RatePoint>) {
    var showAll by remember(a, b) { mutableStateOf(false) }
    val rows = points.indices.reversed().let { if (showAll) it.toList() else it.take(COLLAPSED_ROWS) }
    val colors = AppTheme.colors
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Ui.CardPadding, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Дата", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                HeaderCell("1 ${a.symbol} в ${b.code}", width = WIDE_CELL)
                HeaderCell("изм.")
                HeaderCell("1 ${b.symbol} в ${a.code}", width = WIDE_CELL)
                HeaderCell("изм.")
            }
            HorizontalDivider()
            rows.forEachIndexed { position, index ->
                if (position > 0) HorizontalDivider()
                val point = points[index]
                val prev = points.getOrNull(index - 1)
                val delta = prev?.let { point.value - it.value }
                val inverseDelta = prev?.let { 1.0 / point.value - 1.0 / it.value }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DateFormat.dayMonth(point.date) + ".${point.date.year % 100}", modifier = Modifier.weight(1f))
                    AmountCell(MoneyFormat.formatQuote(point.value), bold = true, width = WIDE_CELL)
                    AmountCell(delta?.let(::signed) ?: "", color = delta?.let { if (it >= 0) colors.good else colors.bad }, muted = delta == null)
                    AmountCell(MoneyFormat.formatQuote(1.0 / point.value), bold = true, width = WIDE_CELL)
                    AmountCell(inverseDelta?.let(::signed) ?: "", color = inverseDelta?.let { if (it >= 0) colors.good else colors.bad }, muted = inverseDelta == null)
                }
            }
            if (points.size > COLLAPSED_ROWS) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Свернуть" else "Показать все ${points.size}") }
            }
        }
    }
}

private fun signed(value: Double): String = (if (value >= 0) "+" else "−") + MoneyFormat.formatQuote(kotlin.math.abs(value))

private const val COLLAPSED_ROWS = 5
private val WIDE_CELL = 140.dp
