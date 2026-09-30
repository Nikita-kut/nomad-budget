package ru.nomadbudget.presentation.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.number
import ru.nomadbudget.domain.logic.HistoryCalculator
import ru.nomadbudget.domain.logic.MonthPoint
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.sumIn
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.KeyValueRow
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme
import kotlin.math.roundToLong

private enum class Range(val months: Int?, val title: String) {
    YEAR(12, "12 мес."),
    TWO_YEARS(24, "24 мес."),
    ALL(null, "всё время"),
}

@Composable
fun ChartsScreen(state: HomeState) {
    var range by remember { mutableStateOf(Range.YEAR) }
    val all = state.monthlyHistory
    val points = range.months?.let { all.takeLast(it) } ?: all
    val labels = points.map { monthLabel(it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Range.entries.forEachIndexed { index, r ->
                    SegmentedButton(
                        selected = range == r,
                        onClick = { range = r },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = Range.entries.size),
                    ) { Text(r.title) }
                }
            }
        }
        if (points.isEmpty()) {
            item { EmptyHint("Пока нет данных для графиков") }
            return@LazyColumn
        }
        item { Totals(points) }
        item { SectionTitle("Доходы и расходы по месяцам", hint = "тыс. ₽ · доход слева, расход справа") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ColumnsChart(
                    series = listOf(points.map { it.income.toThousands() }, points.map { it.expense.toThousands() }),
                    labels = labels,
                    formatY = ::thousandsLabel,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        item { SectionTitle("Отложено накопительно", hint = "тыс. ₽, чистое: отложено минус взято") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                LineChart(
                    values = HistoryCalculator.cumulative(points) { it.netSaved }.map { it.toThousands() },
                    labels = labels,
                    formatY = ::thousandsLabel,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        item { SectionTitle("Расходы по категориям", hint = "за выбранный диапазон") }
        val totals = HistoryCalculator.expensesByCategory(
            state.transactions,
            state.categories,
            from = points.first().period.start,
            toExclusive = points.last().period.endExclusive,
        )
        if (totals.isEmpty()) {
            item { EmptyHint("Расходов нет") }
        }
        items(totals, key = { it.category.id }) { total ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(total.category.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${MoneyFormat.format(total.total, false)} · ${(total.share * 100).roundToLong()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { total.share.toFloat() },
                        color = AppTheme.colors.currency(Currency.BASE),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Totals(points: List<MonthPoint>) {
    val income = points.map { it.income }.sumIn(Currency.BASE)
    val expense = points.map { it.expense }.sumIn(Currency.BASE)
    val saved = points.map { it.netSaved }.sumIn(Currency.BASE)
    val months = points.size
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            KeyValueRow("Доход за $months мес.", MoneyFormat.format(income, false), emphasize = true)
            KeyValueRow("Расход", MoneyFormat.format(expense, false))
            KeyValueRow("Отложено чистыми", MoneyFormat.format(saved, false))
            KeyValueRow("Средний расход в месяц", MoneyFormat.format(Money.rub(if (months > 0) expense.minor / months else 0L), false))
        }
    }
}

private fun monthLabel(point: MonthPoint): String {
    val start = point.period.start
    return "${start.month.number.toString().padStart(2, '0')}.${start.year % 100}"
}

private fun Money.toThousands(): Double = minor / 100.0 / 1_000.0

private fun thousandsLabel(value: Double): String = "${value.roundToLong()}к"
