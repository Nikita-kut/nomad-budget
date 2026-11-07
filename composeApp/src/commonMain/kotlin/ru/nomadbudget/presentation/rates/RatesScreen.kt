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
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.domain.logic.ExchangeAnalyzer
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

private const val CHART_POINTS = 90
private const val RATE_VISIBLE_POINTS = 30
private const val COLLAPSED_ROWS = 5
private const val TABLE_ROWS = 14

@Composable
fun RatesScreen(state: HomeState) {
    val byCurrency = state.rateHistory.groupBy { it.quote }
    CenteredLazyColumn(bottom = 12.dp) {
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
                            val inverted = (current?.basePerUnit ?: tail.last().basePerUnit) < 1.0
                            ChartLegend(listOf(LegendEntry(if (inverted) "Сколько ${currency.symbol} за 1 ${Currency.BASE.symbol}, по дням" else "Сколько ${Currency.BASE.symbol} за 1 ${currency.symbol}, по дням", color)))
                            LineChart(
                                values = tail.map { if (inverted) 1.0 / it.basePerUnit else it.basePerUnit },
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
                    Text(currency.code, fontWeight = FontWeight.SemiBold)
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
                    ExchangeAnalyzer.quoteText(ExchangeAnalyzer.baseQuote(currency, rate.basePerUnit), MoneyFormat::formatQuote),
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
    var showAll by remember(currency) { mutableStateOf(false) }
    val rows = history.takeLast(if (showAll) TABLE_ROWS else COLLAPSED_ROWS).reversed()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            rows.forEachIndexed { index, rate ->
                val previous = history.getOrNull(history.size - 1 - index - 1)
                val inverted = rate.basePerUnit < 1.0
                fun shown(r: ExchangeRate) = if (inverted) 1.0 / r.basePerUnit else r.basePerUnit
                val delta = previous?.let { shown(rate) - shown(it) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(DateFormat.dayMonth(rate.date), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        delta?.let {
                            Text(
                                (if (it >= 0) "+" else "−") + MoneyFormat.formatQuote(kotlin.math.abs(it)),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    it > 0 -> if (inverted) AppTheme.colors.good else AppTheme.colors.bad
                                    it < 0 -> if (inverted) AppTheme.colors.bad else AppTheme.colors.good
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Text(ExchangeAnalyzer.quoteText(ExchangeAnalyzer.baseQuote(currency, rate.basePerUnit), MoneyFormat::formatQuote), fontWeight = FontWeight.Medium)
                    }
                }
                if (index < rows.lastIndex) HorizontalDivider()
            }
            if (history.size > COLLAPSED_ROWS) {
                HorizontalDivider()
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(if (showAll) "Свернуть" else "Показать ещё ${minOf(history.size, TABLE_ROWS) - COLLAPSED_ROWS}")
                }
            }
        }
    }
}

