package ru.nomadbudget.presentation.capital

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
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
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.DateField
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme
import ru.nomadbudget.presentation.components.StatTile
import ru.nomadbudget.presentation.components.TileRow

private val HORIZONS = listOf(12 to "1 год", 24 to "2 года", 60 to "5 лет")

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

    CenteredLazyColumn(maxContentWidth = 1240.dp) {
        item { SummaryTiles(capital, wide) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Прогноз капитала", hint = "капитал = все счета − долги", info = Hints.CAPITAL_FORECAST, modifier = Modifier.weight(1f))
                TextButton(onClick = { editingSettings = true }) {
                    Text("доходность ${MoneyFormat.formatPercentValue(capital.settings.annualReturn)} · подушка ${capital.settings.cushionMonths} мес.")
                }
            }
        }
        item {
            ForecastCard(capital, horizon, horizonIndex, onHorizon = { horizonIndex = it })
        }
        item { SectionTitle("Как копилось «Себе»", hint = "по зарплатным месяцам", info = Hints.CAPITAL_MONTHS) }
        item { MonthsTable(capital, wide) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Цели", hint = "из подушки", info = Hints.CAPITAL_GOALS, modifier = Modifier.weight(1f))
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
                "Капитал чистыми",
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
                capital.cushionMonthsCovered?.let { "хватит на ${formatMonths(it)} обычных расходов" } ?: "нет истории расходов",
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
                capital.debtFreeInMonths?.let { "закроются к ${monthYear(capital.dateAfterMonths(it))}, через $it мес." } ?: if (capital.debt.isZero) "долгов нет" else "платежи не покрывают проценты",
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            ChartLegend(scenarios.map { LegendEntry(scenarioTitle(it, capital), scenarioColors.getValue(it)) })
            val step = if (horizon > 24) 3 else 1
            val indices = (0..horizon step step).toList()
            MultiLineChart(
                series = scenarios.map { s -> indices.map { paths.getValue(s)[it].wholeUnits.toDouble() } },
                colors = scenarios.map { scenarioColors.getValue(it) },
                labels = indices.map { shortMonth(capital.dateAfterMonths(it)) },
                formatY = MoneyFormat::formatAxis,
                formatValue = MoneyFormat::formatWhole,
                visibleCount = indices.size,
            )
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Сценарий", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2.2f))
                HeaderCell("В подушку в месяц")
                HeaderCell("Подушка к ${monthYear(capital.dateAfterMonths(horizon))}")
                HeaderCell("Капитал к ${monthYear(capital.dateAfterMonths(horizon))}")
            }
            scenarios.forEachIndexed { index, scenario ->
                if (index > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Row(modifier = Modifier.weight(2.2f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(scenarioColors.getValue(scenario)))
                        Column {
                            Text(scenarioTitle(scenario, capital), fontWeight = FontWeight.Medium)
                            Text(scenarioExplanation(scenario, capital), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ValueCell(MoneyFormat.formatSigned(capital.monthly(scenario), false))
                    ValueCell(MoneyFormat.format(cushionPaths.getValue(scenario).last(), false))
                    ValueCell(MoneyFormat.format(paths.getValue(scenario).last(), false), bold = true)
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

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ValueCell(text: String, bold: Boolean = false, color: Color? = null) {
    Text(
        text,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        color = color ?: MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.End,
    )
}

private fun scenarioTitle(scenario: Scenario, capital: CapitalModel): String = when (scenario) {
    Scenario.PLAN -> "По плану «Себе»"
    Scenario.FACT -> "Как сейчас"
    Scenario.FACT_WITH_RETURN -> "Как сейчас + доходность"
}

private fun scenarioExplanation(scenario: Scenario, capital: CapitalModel): String = when (scenario) {
    Scenario.PLAN -> capital.planMonthly?.let { "откладывать ${MoneyFormat.format(it, false)} каждый месяц, как в плане" } ?: "«Себе» в плане не задано"
    Scenario.FACT -> capital.paceMedian?.let { median ->
        "медиана за ${capital.paceMonths} мес.: ${MoneyFormat.formatSigned(median, false)}" +
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
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Месяц", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(1.2f))
                HeaderCell("Положил")
                HeaderCell("Забрал")
                HeaderCell("Потратил из подушки")
                HeaderCell("Итого «Себе»")
                if (wide) {
                    HeaderCell("Тело кредитов")
                    HeaderCell("Рост капитала")
                }
            }
            HorizontalDivider()
            visible.forEachIndexed { index, month ->
                if (index > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateFormat.monthName(month.period.start) + if (month.isCurrent) " · идёт" else "",
                        modifier = Modifier.weight(1.2f),
                        color = if (month.isCurrent) muted else MaterialTheme.colorScheme.onSurface,
                    )
                    ValueCell(MoneyFormat.format(month.cushionIn, false))
                    ValueCell(if (month.cushionOut.isZero) "—" else "−" + MoneyFormat.format(month.cushionOut, false), color = if (month.cushionOut.isZero) muted else colors.warning)
                    ValueCell(if (month.cushionSpent.isZero) "—" else "−" + MoneyFormat.format(month.cushionSpent, false), color = if (month.cushionSpent.isZero) muted else colors.warning)
                    ValueCell(MoneyFormat.formatSigned(month.saved, false), bold = true, color = if (month.saved.isNegative) colors.bad else null)
                    if (wide) {
                        ValueCell(MoneyFormat.format(month.principalPaid, false), color = muted)
                        ValueCell(MoneyFormat.formatSigned(month.capitalGrowth, false), color = if (month.capitalGrowth.isNegative) colors.bad else colors.good)
                    }
                }
            }
            if (months.size > COLLAPSED_MONTHS) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Свернуть" else "Показать все ${months.size}") }
            }
            HorizontalDivider()
            Text(
                buildString {
                    append("За ${capital.paceMonths} закрытых мес.: ")
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

@Composable
private fun GoalsCard(capital: CapitalModel, onDelete: (String) -> Unit) {
    val colors = AppTheme.colors
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            capital.cushionTarget?.let { target ->
                val byFact = capital.monthsToReach(target, Scenario.FACT)
                val byPlan = capital.monthsToReach(target, Scenario.PLAN)
                GoalRow(
                    title = "Подушка на ${capital.settings.cushionMonths} мес. расходов",
                    current = capital.cushion,
                    target = target,
                    lines = listOf(
                        "обычные расходы (медиана за полгода) ≈ ${MoneyFormat.format(capital.averageExpense ?: Money.zero(Currency.BASE), false)} в месяц",
                        when {
                            byFact == 0 -> "уже собрана"
                            byFact != null -> "как сейчас: к ${monthYear(capital.dateAfterMonths(byFact))}, через $byFact мес."
                            else -> "как сейчас: не наберётся"
                        },
                        byPlan?.takeIf { it > 0 }?.let { "по плану «Себе»: к ${monthYear(capital.dateAfterMonths(it))}, через $it мес." },
                    ).filterNotNull(),
                    onDelete = null,
                )
            }
            capital.goals.forEach { goal ->
                val progress = capital.goalProgress(goal)
                HorizontalDivider()
                GoalRow(
                    title = "${goal.name} к ${DateFormat.dayMonth(goal.targetDate)}.${goal.targetDate.year}",
                    current = capital.cushion,
                    target = goal.amount,
                    lines = listOf(
                        if (progress.overdue) "срок прошёл" else if (progress.requiredMonthly.isZero) "уже хватает" else "нужно откладывать ${MoneyFormat.format(progress.requiredMonthly, false)} в месяц, ${progress.monthsLeft} мес.",
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
    val share = if (target.minor > 0L) (current.minor.toDouble() / target.minor).coerceIn(0.0, 1.0) else 1.0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${MoneyFormat.format(current.coerceAtMost(target), false)} / ${MoneyFormat.format(target, false)}", fontWeight = FontWeight.SemiBold)
            onDelete?.let { IconButton(onClick = it) { Icon(Icons.Filled.Delete, contentDescription = "Удалить цель", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(modifier = Modifier.fillMaxWidth(share.toFloat()).fillMaxHeight().clip(CircleShape).background(AppTheme.colors.save))
        }
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
    val indices = (0..horizon step (if (horizon > 24) 3 else 1)).toList()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Сейчас", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(MoneyFormat.format(capital.debt, false), fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("К ${monthYear(capital.dateAfterMonths(horizon))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(MoneyFormat.format(capital.debtSchedule.getOrElse(horizon) { capital.debtSchedule.last() }, false), fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Всё закроется", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(capital.debtFreeInMonths?.let { monthYear(capital.dateAfterMonths(it)) } ?: "—", fontWeight = FontWeight.SemiBold)
                }
            }
            ChartLegend(listOf(LegendEntry("Остаток долга по графику", AppTheme.colors.bad)))
            LineChart(
                values = indices.map { capital.debtSchedule.getOrElse(it) { capital.debtSchedule.last() }.wholeUnits.toDouble() },
                color = AppTheme.colors.bad,
                labels = indices.map { shortMonth(capital.dateAfterMonths(it)) },
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
    var date by remember { mutableStateOf(today.plusMonths(12)) }
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
                    label = { Text("Сколько, ₽") },
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
    var months by remember { mutableStateOf(settings.cushionMonths.toString()) }
    var rate by remember { mutableStateOf(if (settings.annualReturn == 0.0) "" else settings.annualReturn.toString().removeSuffix(".0")) }
    val monthsValue = months.toIntOrNull()
    val rateValue = if (rate.isBlank()) 0.0 else rate.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки капитала") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = months,
                    onValueChange = { months = it.filter(Char::isDigit).take(2) },
                    label = { Text("Подушка на сколько месяцев расходов") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Доходность подушки, % годовых") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Доходность — сколько в среднем приносят накопительные счета и вклады. Используется только в сценарии «как сейчас + доходность».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (monthsValue != null && rateValue != null) onConfirm(CapitalSettings(monthsValue, rateValue)) },
                enabled = monthsValue != null && monthsValue in 1..36 && rateValue != null && rateValue in 0.0..100.0,
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private const val COLLAPSED_MONTHS = 6

private fun monthYear(date: LocalDate): String = "${date.month.number.toString().padStart(2, '0')}.${date.year}"

private fun shortMonth(date: LocalDate): String = "${date.month.number.toString().padStart(2, '0')}.${(date.year % 100).toString().padStart(2, '0')}"

private fun formatMonths(value: Double): String {
    if (value <= 0.0) return "0 мес."
    val tenths = kotlin.math.round(value * 10).toLong()
    return if (tenths % 10 == 0L) "${tenths / 10} мес." else "${tenths / 10},${tenths % 10} мес."
}

private fun LocalDate.plusMonths(months: Int): LocalDate = plus(months, kotlinx.datetime.DateTimeUnit.MONTH)

private fun Money.coerceAtMost(other: Money): Money = if (this > other) other else this
