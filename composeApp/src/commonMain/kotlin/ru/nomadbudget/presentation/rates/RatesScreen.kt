package ru.nomadbudget.presentation.rates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.BankRateAnalyzer
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
import ru.nomadbudget.presentation.format.plural
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme
import kotlin.time.Clock

private val RATE_PERIODS = listOf(ChartPeriod("30 дн.", 30), ChartPeriod("90 дн.", 90), ChartPeriod("всё", null))

@Composable
fun RatesScreen(state: HomeState, wide: Boolean = false, bankWatch: BankWatchCallbacks) {
    val pairs = remember(state.userCurrencies) { RatePairs.pairs(state.userCurrencies) }
    val series = remember(state.rateHistory, pairs) {
        pairs.associateWith { (a, b) -> RatePairs.series(a, b, state.rateHistory) }
    }
    var editingWatch by remember { mutableStateOf(false) }
    CenteredLazyColumn(bottom = 24.dp) {
        bankRateWatchItems(state, wide, bankWatch, onEdit = { editingWatch = true })
        item(key = "rates-title") { SectionTitle("Курсы по дням", hint = "обновляются раз в сутки") }
        item { SourceLine(state) }
        if (pairs.isEmpty()) item { EmptyHint("Кроме рубля валют нет") }
        pairs.forEach { pair ->
            val points = series.getValue(pair)
            item(key = "pair-${pair.first.code}-${pair.second.code}") { PairBlock(pair.first, pair.second, points, state, wide) }
        }
    }
    if (editingWatch) {
        val watch = state.bankWatch.watch
        BankRateWatchDialog(
            initial = watch,
            currencies = state.userCurrencies,
            suggestedTarget = BankRateAnalyzer.suggestedTarget(state.bankWatch.week, Clock.System.now()),
            onDismiss = { editingWatch = false },
            onConfirm = { draft ->
                if (watch == null) bankWatch.onCreate(draft) else bankWatch.onUpdate(draft)
                editingWatch = false
            },
        )
    }
}

@Composable
private fun SourceLine(state: HomeState) {
    val latest = state.rates.all.maxByOrNull { it.date }
    Text(
        when {
            latest == null -> "Курсов пока нет"
            latest.source == RateSource.MANUAL -> "Курсы ещё не загружены"
            else -> "Источник ${latest.sourceName} · курс за ${DateFormat.dayMonth(latest.date)} · обновляется раз в сутки"
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
            SectionTitle("${a.code} и ${b.code}", hint = if (points.isEmpty()) "курсов нет" else plural(points.size, "день", "дня", "дней"), info = Hints.RATES, modifier = Modifier.weight(1f))
        }
        if (points.size < 2) {
            Text("Для графика нужен курс хотя бы за два дня", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        EqualRow(
            wide,
            listOf(
                { DirectionChart(a, b, points, colorA) },
                { DirectionChart(b, a, RatePairs.inverted(points), colorB) },
            ),
        )
        HistoryTable(a, b, points)
    }
}

@Composable
private fun DirectionChart(from: Currency, to: Currency, points: List<RatePoint>, color: Color) {
    val unit = MoneyFormat.quoteUnit(RatePairs.median(points))
    val scaled = points.map { it.value * unit }
    ChartPanel(
        title = MoneyFormat.rateText(from, to, scaled.last(), unit),
        hint = "сколько ${MoneyFormat.perUnitLabel(unit, from, to)}",
        legend = listOf(LegendEntry("по дням", color)),
        totalPoints = points.size,
        periods = RATE_PERIODS,
        modifier = Modifier.fillMaxWidth(),
        stateKey = from.code + to.code,
    ) { start, visible ->
        LineChart(
            values = scaled.drop(start),
            color = color,
            labels = points.drop(start).map { DateFormat.dayMonth(it.date) },
            formatY = MoneyFormat::formatQuote,
            visibleCount = visible,
            fitToData = true,
        )
    }
}

@Composable
private fun HistoryTable(a: Currency, b: Currency, points: List<RatePoint>) {
    var showAll by remember(a, b) { mutableStateOf(false) }
    val rows = points.indices.reversed().let { if (showAll) it.toList() else it.take(COLLAPSED_ROWS) }
    val median = RatePairs.median(points)
    val unitA = MoneyFormat.quoteUnit(median)
    val unitB = MoneyFormat.quoteUnit(1.0 / median)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Ui.CardPadding, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Дата", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                HeaderCell(MoneyFormat.perUnitLabel(unitA, a, b), width = Ui.WideAmountColumn)
                HeaderCell("за день, %")
                HeaderCell(MoneyFormat.perUnitLabel(unitB, b, a), width = Ui.WideAmountColumn)
                HeaderCell("за день, %")
            }
            HorizontalDivider()
            rows.forEachIndexed { position, index ->
                if (position > 0) HorizontalDivider()
                val point = points[index]
                val prev = points.getOrNull(index - 1)
                val direct = point.value * unitA
                val inverse = unitB / point.value
                val delta = point.changeFrom(prev)
                val inverseDelta = prev?.let { RatePoint(point.date, 1.0 / point.value).changeFrom(RatePoint(it.date, 1.0 / it.value)) }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DateFormat.dayMonthYearShort(point.date), modifier = Modifier.weight(1f))
                    AmountCell(MoneyFormat.formatQuote(direct), bold = true, width = Ui.WideAmountColumn)
                    DeltaCell(delta)
                    AmountCell(MoneyFormat.formatQuote(inverse), bold = true, width = Ui.WideAmountColumn)
                    DeltaCell(inverseDelta)
                }
            }
            if (points.size > COLLAPSED_ROWS) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Свернуть" else "Показать все ${points.size}") }
            }
        }
    }
}

@Composable
private fun RowScope.DeltaCell(delta: Double?) {
    val text = delta?.let(MoneyFormat::formatChange) ?: ""
    val colors = AppTheme.colors
    AmountCell(
        text,
        color = when {
            delta == null || text == "0" -> MaterialTheme.colorScheme.onSurfaceVariant
            delta > 0 -> colors.good
            else -> colors.bad
        },
    )
}

private const val COLLAPSED_ROWS = 5
