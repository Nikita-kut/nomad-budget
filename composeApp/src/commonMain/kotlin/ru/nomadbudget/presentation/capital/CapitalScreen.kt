package ru.nomadbudget.presentation.capital

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.domain.logic.CapitalModel
import ru.nomadbudget.domain.logic.CapitalSettings
import ru.nomadbudget.domain.logic.Scenario
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.presentation.charts.ChartLegend
import ru.nomadbudget.presentation.charts.LegendEntry
import ru.nomadbudget.presentation.charts.LineChart
import ru.nomadbudget.presentation.charts.MultiLineChart
import ru.nomadbudget.presentation.components.AmountCell
import ru.nomadbudget.presentation.components.AppIcons
import ru.nomadbudget.presentation.components.CapitalSettingsFields
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.DateField
import ru.nomadbudget.presentation.components.HeaderCell
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.ProgressTrack
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.StatTile
import ru.nomadbudget.presentation.components.TileRow
import ru.nomadbudget.presentation.components.Ui
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

private val HORIZONS = listOf(CapitalModel.YEAR_MONTHS to "1 год", CapitalModel.YEAR_MONTHS * 2 to "2 года", CapitalModel.YEAR_MONTHS * 5 to "5 лет")

@Composable
fun CapitalScreen(
    state: HomeState,
    onAddGoal: (String, Money, LocalDate) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onSaveSettings: (CapitalSettings) -> Unit,
    wide: Boolean,
) {
    val capital = state.capital
    var horizonIndex by remember { mutableIntStateOf(0) }
    var addingGoal by remember { mutableStateOf(false) }
    var editingSettings by remember { mutableStateOf(false) }
    val horizon = HORIZONS[horizonIndex].first

    CenteredLazyColumn {
        item { SummaryTiles(capital, wide) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Прогноз капитала", hint = "капитал = все счета − долги", info = Hints.CAPITAL_FORECAST, modifier = Modifier.weight(1f))
                TextButton(onClick = { editingSettings = true }) {
                    Text("доходность ${MoneyFormat.formatPercentValue(capital.settings.annualReturn)} · подушка ${months(capital.settings.cushionMonths)}")
                }
            }
        }
        item { ForecastCard(capital, horizon, horizonIndex, onHorizon = { horizonIndex = it }) }
        item { SectionTitle("Подушка по месяцам", hint = "по месяцам бюджета", info = Hints.CAPITAL_MONTHS) }
        item { MonthsTable(capital, wide) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Цели", hint = "копятся в подушке", info = Hints.CAPITAL_GOALS, modifier = Modifier.weight(1f))
                TextButton(onClick = { addingGoal = true }) { Text("+ цель") }
            }
        }
        item { GoalsCard(capital, onDeleteGoal) }
        if (capital.debt.minor > 0L) {
            item { SectionTitle("Долги", hint = "по графику платежей из «Кредитов»") }
            item { DebtCard(capital, horizon) }
        }
    }

    if (addingGoal) {
        GoalDialog(today = state.today, onDismiss = { addingGoal = false }) { name, amount, date ->
            onAddGoal(name, amount, date)
            addingGoal = false
        }
    }
    if (editingSettings) {
        SettingsDialog(capital.settings, onDismiss = { editingSettings = false }) {
            onSaveSettings(it)
            editingSettings = false
        }
    }
}

@Composable
private fun SummaryTiles(capital: CapitalModel, wide: Boolean) {
    val colors = AppTheme.colors
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            StatTile(
                "Капитал",
                MoneyFormat.format(capital.netWorth, false),
                "счета ${MoneyFormat.format(capital.assets, false)} − долги ${MoneyFormat.format(capital.debt, false)}",
                modifier = m,
                accent = true,
                valueColor = if (capital.netWorth.isNegative) colors.bad else null,
            )
        },
        { m ->
            StatTile(
                "Подушка",
                MoneyFormat.format(capital.cushion, false),
                capital.cushionMonthsCovered?.let { "хватит на ${MoneyFormat.formatTenths(it.coerceAtLeast(0.0))} мес. обычных расходов" } ?: "нет истории расходов",
                modifier = m,
            )
        },
        { m ->
            StatTile(
                "Прирост в месяц",
                capital.paceMedian?.let { MoneyFormat.formatSigned(it + capital.principalPace, false) } ?: "—",
                "«Себе» ${capital.paceMedian?.let { MoneyFormat.formatSigned(it, false) } ?: "—"} + тело кредитов ${MoneyFormat.format(capital.principalPace, false)}",
                modifier = m,
            )
        },
        { m ->
            StatTile(
                "Долги",
                MoneyFormat.format(capital.debt, false),
                capital.debtFreeInMonths?.let { "закроются ${DateFormat.byMonthYear(capital.dateAfterMonths(it))}, через ${months(it)}" } ?: if (capital.debt.isZero) "долгов нет" else "платежи не покрывают проценты",
                modifier = m,
            )
        },
    )
    TileRow(wide, tiles)
}

@Composable
private fun ForecastCard(capital: CapitalModel, horizon: Int, horizonIndex: Int, onHorizon: (Int) -> Unit) {
    val colors = AppTheme.colors
    val scenarioColors = mapOf(
        Scenario.PLAN to colors.save,
        Scenario.FACT to MaterialTheme.colorScheme.primary,
        Scenario.FACT_WITH_RETURN to colors.good,
    )
    val scenarios = listOf(Scenario.PLAN, Scenario.FACT, Scenario.FACT_WITH_RETURN)
    val paths = scenarios.associateWith { capital.capitalPath(it, horizon) }
    val cushionPaths = scenarios.associateWith { capital.cushionPath(it, horizon) }
    val indices = CapitalModel.forecastIndices(horizon)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SingleChoiceSegmentedButtonRow {
                HORIZONS.forEachIndexed { index, (_, label) ->
                    SegmentedButton(
                        selected = index == horizonIndex,
                        onClick = { onHorizon(index) },
                        shape = SegmentedButtonDefaults.itemShape(index, HORIZONS.size),
                        icon = {},
                    ) { Text(label) }
                }
            }
            ChartLegend(scenarios.map { LegendEntry(scenarioTitle(it), scenarioColors.getValue(it)) })
            MultiLineChart(
                series = scenarios.map { s -> indices.map { paths.getValue(s)[it].toMajor() } },
                colors = scenarios.map { scenarioColors.getValue(it) },
                names = scenarios.map { scenarioTitle(it) },
                labels = indices.map { DateFormat.monthYearShort(capital.dateAfterMonths(it)) },
                formatY = MoneyFormat::formatAxis,
                formatValue = MoneyFormat::formatWhole,
                visibleCount = indices.size,
            )
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Сценарий", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(SCENARIO_WEIGHT))
                HeaderCell("В подушку в месяц", width = null)
                HeaderCell("Подушка ${DateFormat.byMonthYear(capital.dateAfterMonths(horizon))}", width = null)
                HeaderCell("Капитал ${DateFormat.byMonthYear(capital.dateAfterMonths(horizon))}", width = null)
            }
            scenarios.forEachIndexed { index, scenario ->
                if (index > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Row(modifier = Modifier.weight(SCENARIO_WEIGHT), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(scenarioColors.getValue(scenario)))
                        Column {
                            Text(scenarioTitle(scenario), fontWeight = FontWeight.Medium)
                            Text(scenarioExplanation(scenario, capital), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AmountCell(MoneyFormat.formatSigned(capital.monthly(scenario), false), width = null)
                    AmountCell(MoneyFormat.format(cushionPaths.getValue(scenario).last(), false), width = null)
                    AmountCell(MoneyFormat.format(paths.getValue(scenario).last(), false), bold = true, width = null)
                }
            }
            Text(
                "Сейчас капитал ${MoneyFormat.format(capital.netWorth, false)}: подушка ${MoneyFormat.format(capital.cushion, false)}, на жизнь и в копилках ${MoneyFormat.format(capital.life + capital.funds, false)}, долги ${MoneyFormat.format(capital.debt, false)}. " +
                    "В прогнозе деньги на жизнь и в копилках не меняются, подушка растёт по сценарию, долги уменьшаются по графику платежей.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun scenarioTitle(scenario: Scenario): String = when (scenario) {
    Scenario.PLAN -> "По плану «Себе»"
    Scenario.FACT -> "Как сейчас"
    Scenario.FACT_WITH_RETURN -> "Как сейчас + доходность"
}

private fun scenarioExplanation(scenario: Scenario, capital: CapitalModel): String = when (scenario) {
    Scenario.PLAN -> capital.planMonthly?.let { "откладывать ${MoneyFormat.format(it, false)} каждый месяц, как в плане" } ?: "«Себе» в плане не задано"
    Scenario.FACT -> capital.paceMedian?.let { median ->
        "медиана за ${months(capital.paceMonths)}: ${MoneyFormat.formatSigned(median, false)}" +
            (capital.paceAverage?.let { " (среднее ${MoneyFormat.formatSigned(it, false)})" } ?: "")
    } ?: "пока нет закрытых месяцев"
    Scenario.FACT_WITH_RETURN -> "то же, плюс ${MoneyFormat.formatPercentValue(capital.settings.annualReturn)} годовых на подушку, начисление раз в месяц"
}

@Composable
private fun MonthsTable(capital: CapitalModel, wide: Boolean) {
    val colors = AppTheme.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val months = capital.months.takeLast(CapitalModel.HISTORY_MONTHS).reversed()
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll) months else months.take(COLLAPSED_MONTHS)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Ui.CardPadding, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Месяц", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(MONTH_WEIGHT))
                HeaderCell("Внесено", width = null)
                HeaderCell("Снято", width = null)
                HeaderCell("Потрачено", width = null)
                HeaderCell("Итого «Себе»", width = null)
                if (wide) {
                    HeaderCell("Тело кредитов", width = null)
                    HeaderCell("Рост капитала", width = null)
                }
            }
            HorizontalDivider()
            visible.forEachIndexed { index, month ->
                if (index > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateFormat.monthName(month.period.start) + if (month.isCurrent) " · идёт" else "",
                        modifier = Modifier.weight(MONTH_WEIGHT),
                        color = if (month.isCurrent) muted else MaterialTheme.colorScheme.onSurface,
                    )
                    AmountCell(MoneyFormat.format(month.cushionIn, false), width = null)
                    AmountCell(outflowText(month.cushionOut), color = if (month.cushionOut.isZero) muted else colors.warning, width = null)
                    AmountCell(outflowText(month.cushionSpent), color = if (month.cushionSpent.isZero) muted else colors.warning, width = null)
                    AmountCell(MoneyFormat.formatSigned(month.saved, false), bold = true, color = if (month.saved.isNegative) colors.bad else null, width = null)
                    if (wide) {
                        AmountCell(MoneyFormat.format(month.principalPaid, false), color = muted, width = null)
                        AmountCell(MoneyFormat.formatSigned(month.capitalGrowth, false), color = if (month.capitalGrowth.isNegative) colors.bad else colors.good, width = null)
                    }
                }
            }
            if (months.size > COLLAPSED_MONTHS) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Свернуть" else "Показать все ${months.size}") }
            }
            HorizontalDivider()
            Text(
                buildString {
                    append("За ${months(capital.paceMonths)} закрытых: ")
                    append("медиана ${capital.paceMedian?.let { MoneyFormat.formatSigned(it, false) } ?: "—"}, ")
                    append("среднее ${capital.paceAverage?.let { MoneyFormat.formatSigned(it, false) } ?: "—"}. ")
                    append("Для прогноза берётся медиана: один необычный месяц её почти не сдвигает.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

private fun outflowText(money: Money): String = if (money.isZero) "—" else MoneyFormat.format(-money, false)

@Composable
private fun GoalsCard(capital: CapitalModel, onDelete: (String) -> Unit) {
    val colors = AppTheme.colors
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            capital.cushionTarget?.let { target ->
                val byFact = capital.monthsToReach(target, Scenario.FACT)
                val byPlan = capital.monthsToReach(target, Scenario.PLAN)
                GoalRow(
                    title = "Подушка на ${months(capital.settings.cushionMonths)} расходов",
                    current = capital.cushion,
                    target = target,
                    lines = listOfNotNull(
                        "обычные расходы (медиана за полгода) ≈ ${MoneyFormat.format(capital.averageExpense ?: Money.zero(Currency.BASE), false)} в месяц",
                        when {
                            byFact == 0 -> "уже собрана"
                            byFact != null -> "как сейчас: ${DateFormat.byMonthYear(capital.dateAfterMonths(byFact))}, через ${months(byFact)}"
                            else -> "как сейчас: не наберётся"
                        },
                        byPlan?.takeIf { it > 0 }?.let { "по плану «Себе»: ${DateFormat.byMonthYear(capital.dateAfterMonths(it))}, через ${months(it)}" },
                    ),
                    onDelete = null,
                )
            }
            capital.goals.forEach { goal ->
                val progress = capital.goalProgress(goal)
                HorizontalDivider()
                GoalRow(
                    title = "${goal.name} к ${DateFormat.dayMonthYear(goal.targetDate)}",
                    current = capital.cushion,
                    target = goal.amount,
                    lines = listOf(
                        when {
                            progress.overdue -> "срок прошёл"
                            progress.requiredMonthly.isZero -> "уже хватает"
                            else -> "нужно откладывать ${MoneyFormat.format(progress.requiredMonthly, false)} в месяц в течение ${months(progress.monthsLeft)}"
                        },
                        if (progress.reachableByPace) "как сейчас — успеваешь" else "как сейчас — не успеваешь",
                    ),
                    lineColor = if (progress.reachableByPace) colors.good else colors.warning,
                    onDelete = { onDelete(goal.id) },
                )
            }
            if (capital.cushionTarget == null && capital.goals.isEmpty()) {
                Text("Целей пока нет. Добавь цель кнопкой «+ цель».", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Цели считаются от всей подушки: одна сумма может закрывать несколько целей.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalRow(title: String, current: Money, target: Money, lines: List<String>, onDelete: (() -> Unit)?, lineColor: Color? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${MoneyFormat.format(current.coerceAtMost(target), false)} / ${MoneyFormat.format(target, false)}", fontWeight = FontWeight.SemiBold)
            onDelete?.let { IconButton(onClick = it) { Icon(AppIcons.Trash, contentDescription = "Удалить цель", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        ProgressTrack(fraction = if (target.minor > 0L) current.shareOf(target).toFloat() else 1f, marker = null, color = AppTheme.colors.save)
        lines.forEachIndexed { index, line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = if (index == lines.lastIndex && lineColor != null) lineColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DebtCard(capital: CapitalModel, horizon: Int) {
    val indices = CapitalModel.forecastIndices(horizon)
    fun debtAt(month: Int): Money = capital.debtSchedule.getOrElse(month) { capital.debtSchedule.last() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Сейчас", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(MoneyFormat.format(capital.debt, false), fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(DateFormat.byMonthYear(capital.dateAfterMonths(horizon)).replaceFirstChar(Char::uppercaseChar), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(MoneyFormat.format(debtAt(horizon), false), fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Всё закроется", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(capital.debtFreeInMonths?.let { DateFormat.monthYear(capital.dateAfterMonths(it)) } ?: "—", fontWeight = FontWeight.SemiBold)
                }
            }
            ChartLegend(listOf(LegendEntry("Остаток долга по графику", AppTheme.colors.bad)))
            LineChart(
                values = indices.map { debtAt(it).toMajor() },
                color = AppTheme.colors.bad,
                labels = indices.map { DateFormat.monthYearShort(capital.dateAfterMonths(it)) },
                formatY = MoneyFormat::formatAxis,
                formatValue = MoneyFormat::formatWhole,
                visibleCount = indices.size,
            )
        }
    }
}

@Composable
private fun GoalDialog(today: LocalDate, onDismiss: () -> Unit, onConfirm: (String, Money, LocalDate) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today.plus(CapitalModel.YEAR_MONTHS, DateTimeUnit.MONTH)) }
    val transformation = remember { ThousandsVisualTransformation() }
    val money = MoneyFormat.parse(amount, Currency.BASE)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая цель") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("На что") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = ThousandsVisualTransformation.sanitize(it, Currency.BASE.minorUnits) },
                    label = { Text("Сколько, ${Currency.BASE.code}") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("К какой дате", style = MaterialTheme.typography.labelMedium)
                DateField(date = date, today = today, onChange = { date = it }, allowFuture = true)
            }
        },
        confirmButton = { Button(onClick = { money?.let { onConfirm(name, it, date) } }, enabled = name.isNotBlank() && money != null && date > today) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SettingsDialog(settings: CapitalSettings, onDismiss: () -> Unit, onConfirm: (CapitalSettings) -> Unit) {
    var draft by remember { mutableStateOf<CapitalSettings?>(settings) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки капитала") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CapitalSettingsFields(settings = settings, onChange = { draft = it })
                Text("Эти же значения — в «Ещё → Настройки».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { draft?.let(onConfirm) }, enabled = draft != null) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun months(count: Int): String = "$count мес."

private const val COLLAPSED_MONTHS = 6
private const val SCENARIO_WEIGHT = 2.2f
private const val MONTH_WEIGHT = 1.2f
