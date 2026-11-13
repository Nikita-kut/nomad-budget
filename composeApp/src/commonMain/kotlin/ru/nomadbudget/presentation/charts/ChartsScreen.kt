package ru.nomadbudget.presentation.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.HistoryCalculator
import ru.nomadbudget.domain.logic.MonthPoint
import ru.nomadbudget.domain.logic.RatePairs
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.presentation.components.AmountCell
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.ChartPanel
import ru.nomadbudget.presentation.components.ChartPeriod
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.StatTile
import ru.nomadbudget.presentation.components.TileRow
import ru.nomadbudget.presentation.components.Ui
import ru.nomadbudget.presentation.format.CurrencyWords
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

private val MONTH_PERIODS = listOf(ChartPeriod("12 мес.", 12), ChartPeriod("24 мес.", 24), ChartPeriod("всё", null))

@Composable
fun ChartsScreen(state: HomeState, wide: Boolean = false) {
    val all = state.monthlyHistory
    val labels = all.map(::monthLabel)
    val incomeColor = AppTheme.colors.good
    val expenseColor = AppTheme.colors.bad
    val savedColor = AppTheme.colors.save
    val planColor = MaterialTheme.colorScheme.outline
    val netWorth = remember(all, state.accounts, state.transactions, state.rateHistory, state.rates) {
        HistoryCalculator.netWorth(all.map { it.period }, state.accounts, state.transactions, state.base) { currency, date ->
            RatePairs.basePerUnitOn(state.rateHistory, currency, date, state.base) ?: state.rates.rateFor(currency)?.basePerUnit
        }
    }
    val closed = if (all.isNotEmpty() && state.today in all.last().period) all.dropLast(1) else all
    val rated = all.filter { it.income.minor > 0L }
    val planned = HistoryCalculator.plannedExpenseByPeriod(state.allPlanLines, state.categories, state.base)
    var categoryPeriod by remember { mutableIntStateOf(0) }

    CenteredLazyColumn(bottom = 24.dp) {
        if (all.isEmpty()) {
            item { EmptyHint("Пока нет данных для графиков") }
            return@CenteredLazyColumn
        }
        item { Totals(closed.takeLast(TOTALS_MONTHS), state.base, wide) }
        item {
            ChartPanel(
                title = "Доходы и расходы по месяцам",
                hint = "${CurrencyWords.inCurrency(state.base)} по курсу на день операции, без корректировок остатков",
                info = Hints.CHART_INCOME_EXPENSE,
                legend = listOf(LegendEntry("Доход", incomeColor), LegendEntry("Расход", expenseColor)),
                totalPoints = all.size,
                periods = MONTH_PERIODS,
            ) { from, visible ->
                val slice = all.drop(from)
                ColumnsChart(
                    series = listOf(slice.map { it.income.toMajor() }, slice.map { it.expense.toMajor() }),
                    colors = listOf(incomeColor, expenseColor),
                    names = listOf("доход", "расход"),
                    labels = labels.drop(from),
                    formatY = MoneyFormat::formatAxis,
                    formatValue = MoneyFormat.whole(state.base),
                    visibleCount = visible,
                )
            }
        }
        item {
            ChartPanel(
                title = "Доля отложенного",
                hint = "доля дохода, отложенная в подушку; месяцы без дохода не показаны",
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
                    formatY = MoneyFormat::formatPercentPoints,
                    visibleCount = visible,
                )
            }
        }
        val withPlan = all.filter { it.period.start in planned }
        if (withPlan.isNotEmpty()) {
            item {
                ChartPanel(
                    title = "План и факт расходов",
                    hint = "только месяцы, где был план; факт — траты по конвертам с карт и наличных",
                    info = Hints.CHART_PLAN_FACT,
                    legend = listOf(LegendEntry("План", planColor), LegendEntry("Факт", expenseColor)),
                    totalPoints = withPlan.size,
                    periods = MONTH_PERIODS,
                ) { from, visible ->
                    val slice = withPlan.drop(from)
                    ColumnsChart(
                        series = listOf(
                            slice.map { (planned[it.period.start] ?: Money.zero(state.base)).toMajor() },
                            slice.map { it.envelopeExpense.toMajor() },
                        ),
                        colors = listOf(planColor, expenseColor),
                        names = listOf("план", "факт"),
                        labels = slice.map(::monthLabel),
                        formatY = MoneyFormat::formatAxis,
                        formatValue = MoneyFormat.whole(state.base),
                        visibleCount = visible,
                    )
                }
            }
        }
        item {
            ChartPanel(
                title = "Все счета на конец месяца",
                hint = "по курсу на конец месяца, без долгов",
                info = Hints.CHART_NET_WORTH,
                legend = listOf(LegendEntry("Сумма всех счетов", incomeColor)),
                totalPoints = netWorth.size.coerceAtLeast(1),
                periods = MONTH_PERIODS,
            ) { from, visible ->
                val slice = netWorth.drop(from)
                LineChart(
                    values = slice.map { it.second.toMajor() },
                    color = incomeColor,
                    labels = slice.map { DateFormat.monthYearShort(it.first.start) },
                    formatY = MoneyFormat::formatAxis,
                    formatValue = MoneyFormat.whole(state.base),
                    visibleCount = visible,
                    fitToData = true,
                )
            }
        }
        item {
            ChartPanel(
                title = "Отложено с начала периода",
                hint = "итог нарастающим итогом с начала выбранного периода",
                info = Hints.CHART_SAVED,
                legend = listOf(LegendEntry("Пришло в подушку минус ушло из неё", savedColor)),
                totalPoints = all.size,
                periods = MONTH_PERIODS,
            ) { from, visible ->
                LineChart(
                    values = HistoryCalculator.cumulative(all.drop(from), state.base) { it.netSaved }.map { it.toMajor() },
                    color = savedColor,
                    labels = labels.drop(from),
                    formatY = MoneyFormat::formatAxis,
                    formatValue = MoneyFormat.whole(state.base),
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
            base = state.base,
        )
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (totals.isEmpty()) Text("Расходов нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    totals.forEachIndexed { index, total ->
                        if (index > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(total.category.name, modifier = Modifier.width(CATEGORY_WIDTH), maxLines = 1)
                            LinearProgressIndicator(
                                progress = { total.share.toFloat() },
                                color = expenseColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                drawStopIndicator = {},
                                modifier = Modifier.weight(1f).height(8.dp),
                            )
                            AmountCell(MoneyFormat.format(total.total, false))
                            AmountCell(MoneyFormat.formatShare(total.share), muted = true, width = SHARE_WIDTH)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Totals(points: List<MonthPoint>, base: Currency, wide: Boolean) {
    val totals = HistoryCalculator.totals(points, base)
    val months = totals.months
    TileRow(
        wide,
        listOf(
            { m -> StatTile("Доход за $months мес.", MoneyFormat.format(totals.income, false), "в среднем ${MoneyFormat.format(totals.averageIncome, false)} в месяц", m) },
            { m -> StatTile("Расход за $months мес.", MoneyFormat.format(totals.expense, false), "в среднем ${MoneyFormat.format(totals.averageExpense, false)} в месяц", m) },
            { m -> StatTile("Отложено чистыми", MoneyFormat.format(totals.netSaved, false), "пришло в подушку минус ушло из неё", m) },
            { m ->
                StatTile(
                    "Доля отложенного",
                    totals.savingsShare?.let(MoneyFormat::formatShare) ?: "—",
                    "доля дохода, которая осталась в подушке",
                    m,
                    progress = totals.savingsShare?.toFloat(),
                    progressColor = AppTheme.colors.save,
                )
            },
        ),
    )
}

private fun monthLabel(point: MonthPoint): String = DateFormat.monthYearShort(point.period.start)

private const val PERCENT = 100.0
private const val TOTALS_MONTHS = 12
private val CATEGORY_WIDTH = 200.dp
private val SHARE_WIDTH = 56.dp
