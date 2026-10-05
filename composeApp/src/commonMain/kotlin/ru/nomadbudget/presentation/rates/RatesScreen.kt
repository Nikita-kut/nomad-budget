package ru.nomadbudget.presentation.rates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.presentation.charts.ChartLegend
import ru.nomadbudget.presentation.charts.LegendEntry
import ru.nomadbudget.presentation.charts.LineChart
import ru.nomadbudget.presentation.components.CurrencyChip
import ru.nomadbudget.presentation.components.currencyColor
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

private const val CHART_POINTS = 90
private const val RATE_VISIBLE_POINTS = 30
private const val TABLE_ROWS = 14

@Composable
fun RatesScreen(state: HomeState) {
    val byCurrency = state.rateHistory.groupBy { it.quote }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.foreignCurrencies.isEmpty()) {
            item { EmptyHint("Кроме базовой валюты других нет") }
        }
        state.foreignCurrencies.forEach { currency ->
            val history = byCurrency[currency].orEmpty().sortedBy { it.date }
            val current = state.rates.rateFor(currency)
            item { CurrencyHeader(currency, current, state) }
            if (history.size >= 2) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        val tail = history.takeLast(CHART_POINTS)
                        val color = currencyColor(currency, state)
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChartLegend(listOf(LegendEntry("${unitsLabel(currency, current?.basePerUnit)} по дням", color)))
                            LineChart(
                                values = tail.map { it.basePerUnit * unitsFor(it.basePerUnit) },
                                color = color,
                                labels = tail.map { DateFormat.dayMonth(it.date) },
                                formatY = { MoneyFormat.formatQuote(it) },
                                visibleCount = tail.size.coerceIn(1, RATE_VISIBLE_POINTS),
                                fitToData = true,
                            )
                        }
                    }
                }
            }
            item { SectionTitle("История ${currency.code}", hint = "${history.size} снимков", info = Hints.RATES) }
            item { HistoryTable(currency, history) }
        }
    }
}

@Composable
private fun CurrencyHeader(currency: Currency, current: ExchangeRate?, state: HomeState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CurrencyChip(currency, state)
                    Text(unitsLabel(currency, current?.basePerUnit), fontWeight = FontWeight.SemiBold)
                }
                Text(
                    when {
                        current == null -> "курса нет"
                        current.source == RateSource.MANUAL -> "значение по умолчанию, таблица пуста"
                        else -> buildString {
                            append("${current.sourceName} · за ${DateFormat.dayMonth(current.date)}")
                            current.fetchedAt?.let { append(" · получен ${DateFormat.dayMonthTime(it)}") }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            current?.let { rate ->
                Text(
                    "${MoneyFormat.formatRate(rate.basePerUnit * unitsFor(rate.basePerUnit))} ${Currency.BASE.symbol}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = currencyColor(currency, state),
                )
            }
        }
    }
}

@Composable
private fun HistoryTable(currency: Currency, history: List<ExchangeRate>) {
    if (history.isEmpty()) {
        EmptyHint("Снимков курса ещё нет, cron добавляет по одному в день")
        return
    }
    val rows = history.takeLast(TABLE_ROWS).reversed()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            rows.forEachIndexed { index, rate ->
                val previous = history.getOrNull(history.size - 1 - index - 1)
                val units = unitsFor(rate.basePerUnit)
                val delta = previous?.let { (rate.basePerUnit - it.basePerUnit) * units }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(DateFormat.dayMonth(rate.date), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        delta?.let {
                            Text(
                                (if (it >= 0) "+" else "−") + MoneyFormat.formatRate(kotlin.math.abs(it)),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    it > 0 -> AppTheme.colors.bad
                                    it < 0 -> AppTheme.colors.good
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Text("${MoneyFormat.formatRate(rate.basePerUnit * units)} ${Currency.BASE.symbol}", fontWeight = FontWeight.Medium)
                    }
                }
                if (index < rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

private fun unitsFor(basePerUnit: Double): Int = if (basePerUnit < SMALL_RATE) 1_000 else 1

private fun unitsLabel(currency: Currency, basePerUnit: Double?): String {
    val units = basePerUnit?.let(::unitsFor) ?: 1
    return if (units > 1) "$units ${currency.code} в рублях" else "1 ${currency.code} в рублях"
}

private const val SMALL_RATE = 0.01
