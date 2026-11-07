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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.KeyValueRow
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme
import kotlin.math.roundToLong
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.ChartPanel
import ru.nomadbudget.presentation.components.ChartPeriod
import ru.nomadbudget.presentation.components.StatTile
import ru.nomadbudget.presentation.components.TileRow
import ru.nomadbudget.presentation.components.Ui
import ru.nomadbudget.presentation.components.AmountCell
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height

private val MONTH_PERIODS = listOf(ChartPeriod("12 мес.", 12), ChartPeriod("24 мес.", 24), ChartPeriod("всё", null))

@Composable
fun ChartsScreen(state: HomeState, wide: Boolean = false) {
    val all = state.monthlyHistory
    val labels = all.map { monthLabel(it) }
    val incomeColor = AppTheme.colors.good
    val expenseColor = AppTheme.colors.bad
    val savedColor = AppTheme.colors.save
    val planColor = MaterialTheme.colorScheme.outline
    val netWorth = remember(all, state.accounts, state.transactions, state.rateHistory) {
        HistoryCalculator.netWorth(all.map { it.period }, state.accounts, state.transactions) { currency, date ->
            state.rateHistory.filter { it.quote == currency && it.date <= date }.maxByOrNull { it.date }?.basePerUnit
                ?: state.rates.rateFor(currency)?.basePerUnit
        }
    }
    val closed = if (all.isNotEmpty() && state.today in all.last().period) all.dropLast(1) else all
    val rated = all.filter { it.income.minor > 0L }
    val planned = HistoryCalculator.plannedExpenseByPeriod(state.allPlanLines, state.categories)
    var categoryPeriod by remember { mutableIntStateOf(0) }

    CenteredLazyColumn(maxContentWidth = 1240.dp, bottom = 24.dp) {
        if (all.isEmpty()) {
            item { EmptyHint("Пока нет данных для графиков") }
            return@CenteredLazyColumn
        }
        item { Totals(closed.takeLast(12), wide) }
        item {
            ChartPanel(
                title = "Доходы и расходы по месяцам",
                hint = "в рублях по курсу на день операции, без корректировок остатков",
                info = Hints.CHART_INCOME_EXPENSE,
                legend = listOf(LegendEntry("Доход", incomeColor), LegendEntry("Расход", expenseColor)),
                totalPoints = all.size,
                periods = MONTH_PERIODS,
            ) { from, visible ->
                val slice = all.drop(from)
                ColumnsChart(
                    series = listOf(slice.map { it.income.toRubles() }, slice.map { it.expense.toRubles() }),
                    colors = listOf(incomeColor, expenseColor),
                    labels = labels.drop(from),
                    formatY = MoneyFormat::formatAxis,
                    formatValue = MoneyFormat::formatWhole,
                    visibleCount = visible,
                )
            }
        }
        item {
            ChartPanel(
                title = "Норма сбережений",
                hint = "% дохода, отложенный в подушку; месяцы без дохода не показаны, шкала ограничена ±100%",
                info = Hints.CHART_SAVINGS_RATE,
                legend = listOf(LegendEntry("Отложено / доход месяца", savedColor)),
                totalPoints = rated.size,
                periods = MONTH_PERIODS,
            ) { from, visible ->
                val slice = rated.drop(from)
                LineChart(
                    values = slice.map { ((it.savingsRate ?: 0.0) * PERCENT).coerceIn(-PERCENT, PERCENT) },
                    color = savedColor,
                    labels = slice.map(::monthLabel),
                    formatY = { "${it.roundToLong()}%" },
                    visibleCount = visible,
                )
            }
        }
        val withPlan = all.filter { it.period.start in planned }
        if (withPlan.isNotEmpty()) {
            item {
                ChartPanel(
                    title = "План и факт расходов",
                    hint = "только месяцы, где был план; факт без корректировок остатков",
                    info = Hints.CHART_PLAN_FACT,
                    legend = listOf(LegendEntry("План", planColor), LegendEntry("Факт", expenseColor)),
                    totalPoints = withPlan.size,
                    periods = MONTH_PERIODS,
                ) { from, visible ->
                    val slice = withPlan.drop(from)
                    ColumnsChart(
                        series = listOf(
                            slice.map { (planned[it.period.start] ?: Money.zero(Currency.BASE)).toRubles() },
                            slice.map { it.expense.toRubles() },
                        ),
                        colors = listOf(planColor, expenseColor),
                        labels = slice.map(::monthLabel),
                        formatY = MoneyFormat::formatAxis,
                        formatValue = MoneyFormat::formatWhole,
                        visibleCount = visible,
                    )
                }
            }
        }
        item {
            ChartPanel(
                title = "Все счета на конец месяца",
                hint = "активные счета с начала учёта, по курсу на конец месяца, без долгов; капитал за вычетом долгов — в разделе «Капитал»",
                info = Hints.CHART_NET_WORTH,
                legend = listOf(LegendEntry("Сумма всех счетов", incomeColor)),
                totalPoints = netWorth.size.coerceAtLeast(1),
                periods = MONTH_PERIODS,
            ) { from, visible ->
                val slice = netWorth.drop(from)
                LineChart(
                    values = slice.map { it.second.toRubles() },
                    color = incomeColor,
                    labels = slice.map { periodLabel(it.first.start) },
                    formatY = MoneyFormat::formatAxis,
                    formatValue = MoneyFormat::formatWhole,
                    visibleCount = visible,
                    fitToData = true,
                )
            }
        }
        item {
            ChartPanel(
                title = "Отложено накопительно",
                hint = "накопленный итог с начала выбранного периода",
                info = Hints.CHART_SAVED,
                legend = listOf(LegendEntry("Пришло в подушку минус ушло из неё", savedColor)),
                totalPoints = all.size,
                periods = MONTH_PERIODS,
            ) { from, visible ->
                LineChart(
                    values = HistoryCalculator.cumulative(all.drop(from)) { it.netSaved }.map { it.toRubles() },
                    color = savedColor,
                    labels = labels.drop(from),
                    formatY = MoneyFormat::formatAxis,
                    formatValue = MoneyFormat::formatWhole,
                    visibleCount = visible,
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle("Расходы по категориям", hint = "доля от всех расходов", info = Hints.CHART_CATEGORIES, modifier = Modifier.weight(1f))
                MONTH_PERIODS.forEachIndexed { index, period ->
                    FilterChip(selected = categoryPeriod == index, onClick = { categoryPeriod = index }, label = { Text(period.label) })
                }
            }
        }
        val points = MONTH_PERIODS[categoryPeriod].points?.let { all.takeLast(it) } ?: all
        val totals = HistoryCalculator.expensesByCategory(
            state.transactions,
            state.categories,
            from = points.first().period.start,
            toExclusive = points.last().period.endExclusive,
        )
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (totals.isEmpty()) Text("Расходов нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    totals.forEachIndexed { index, total ->
                        if (index > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(total.category.name, modifier = Modifier.width(200.dp), maxLines = 1)
                            LinearProgressIndicator(
                                progress = { total.share.toFloat() },
                                color = expenseColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                drawStopIndicator = {},
                                modifier = Modifier.weight(1f).height(8.dp),
                            )
                            AmountCell(MoneyFormat.format(total.total, false))
                            AmountCell("${(total.share * 100).roundToLong()}%", muted = true, width = 56.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Totals(points: List<MonthPoint>, wide: Boolean) {
    val income = points.map { it.income }.sumIn(Currency.BASE)
    val expense = points.map { it.expense }.sumIn(Currency.BASE)
    val saved = points.map { it.netSaved }.sumIn(Currency.BASE)
    val months = points.size
    TileRow(
        wide,
        listOf(
            { m -> StatTile("Доход за $months мес.", MoneyFormat.format(income, false), "в среднем ${MoneyFormat.format(Money.rub(if (months > 0) income.minor / months else 0L), false)} в месяц", m) },
            { m -> StatTile("Расход за $months мес.", MoneyFormat.format(expense, false), "в среднем ${MoneyFormat.format(Money.rub(if (months > 0) expense.minor / months else 0L), false)} в месяц", m) },
            { m -> StatTile("Отложено чистыми", MoneyFormat.format(saved, false), "пришло в подушку минус ушло из неё", m) },
            { m ->
                StatTile(
                    "Норма сбережений",
                    if (income.minor > 0L) MoneyFormat.formatShare(saved.minor.toDouble() / income.minor) else "—",
                    "доля дохода, которая осталась в подушке",
                    m,
                    progress = if (income.minor > 0L) (saved.minor.toDouble() / income.minor).toFloat() else null,
                    progressColor = AppTheme.colors.save,
                )
            },
        ),
    )
}

private fun monthLabel(point: MonthPoint): String = periodLabel(point.period.start)

private fun periodLabel(start: kotlinx.datetime.LocalDate): String {
    return "${start.month.number.toString().padStart(2, '0')}.${start.year % 100}"
}

private fun Money.toRubles(): Double = minor / 100.0

private const val PERCENT = 100.0

