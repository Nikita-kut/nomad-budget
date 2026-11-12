package ru.nomadbudget.presentation.planning

import ru.nomadbudget.presentation.components.SecondaryButton
import ru.nomadbudget.presentation.components.PrimaryButton
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.Ui
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.CategoryBudget
import ru.nomadbudget.domain.logic.CategoryHistory
import ru.nomadbudget.domain.logic.MonthModel
import ru.nomadbudget.domain.logic.PlanCalculator
import ru.nomadbudget.domain.logic.CapitalModel
import ru.nomadbudget.domain.logic.Scenario
import ru.nomadbudget.presentation.components.incomeDaysHint
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.InfoHint
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.TagChip
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.CategoryPlanDraft
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.more.SubScreenHeader
import ru.nomadbudget.presentation.theme.AppTheme
import kotlin.math.abs
import ru.nomadbudget.presentation.components.StatTile
import ru.nomadbudget.presentation.components.TileRow
import ru.nomadbudget.presentation.components.AmountCell
import ru.nomadbudget.domain.model.Period

@Composable
fun PlanningScreen(
    state: HomeState,
    onBack: (() -> Unit)?,
    onSaveCategoryPlan: (CategoryPlanDraft) -> Unit,
    onCopyPlan: () -> Unit,
    onSetSavingsTarget: (Money?) -> Unit = {},
    onSetFund: (String, Money?, Money?, Int?) -> Unit = { _, _, _, _ -> },
    onOpenDebts: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    twoColumns: Boolean = false,
    onOpenCapital: () -> Unit = {},
) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var confirmCopy by remember { mutableStateOf(false) }
    var editingSavings by remember { mutableStateOf(false) }
    var editingFund by remember { mutableStateOf<Account?>(null) }
    val month = state.month

    val summaryItems: LazyListScope.() -> Unit = {
        if (onBack != null) {
            item { SubScreenHeader("План", onBack) }
        }
        item {
            Text(
                listOfNotNull(
                    "План на ${DateFormat.monthName(state.period.start).lowercase()}: ${DateFormat.periodRange(state.period)}. Другой месяц выбирается стрелками в шапке.",
                    incomeDaysHint(state.settings.incomeDays)?.let { "$it." },
                ).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { PlanSummaryTiles(month, state.period, twoColumns) }
        item { SectionTitle("Куда распределено", hint = "от ${MoneyFormat.format(month.available, false)} на месяц") }
        item { AllocationCard(month) }
        item {
            if (!month.hasPlan) {
                PrimaryButton(onClick = onCopyPlan, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Скопировать план с прошлого месяца") }
            } else {
                SecondaryButton(onClick = { confirmCopy = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                    Text("Скопировать план с прошлого месяца")
                }
            }
        }

    }
    val sectionItems: LazyListScope.() -> Unit = {
        item { SectionTitle("Доход", hint = "только регулярный", info = Hints.INCOME) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    month.incomeBudgets.filterNot { it.category.isArchived }.forEachIndexed { index, budget ->
                        if (index > 0) HorizontalDivider()
                        PlanRow(budget.category.name, budget.planned.takeIf { budget.hasPlan }, onClick = { editing = budget.category })
                    }
                    if (month.incomeBudgets.isEmpty()) {
                        Text("Категорий дохода нет", modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { SectionTitle("Себе", hint = "сначала заплати себе", info = Hints.PLAN_SAVINGS) }
        item { SavingsCard(month, state.capital, onClick = { editingSavings = true }, onOpenCapital = onOpenCapital) }

        item { SectionTitle("Кредиты", hint = "платежи подставляются сами", info = Hints.OBLIGATIONS) }
        item { DebtsPlanCard(state, onOpenDebts) }
        item { SectionTitle("Копилки", hint = "взносы и траты из копилок", info = Hints.FUNDS) }
        item { FundsPlanCard(state, onEditFund = { editingFund = it }, onOpenAccounts = onOpenAccounts) }

        item { SectionTitle("Конверты", hint = "план и среднее за 3 месяца", info = Hints.PLAN_CATEGORY) }
        items(month.envelopes.filterNot { it.category.isArchived || state.isDebtOnlyEnvelope(it) }, key = { it.category.id }) { budget ->
            CategoryPlanCard(budget, state.categoryHistory[budget.category.id], onClick = { editing = budget.category })
        }
    }
    CenteredLazyColumn() {
        summaryItems()
        sectionItems()
    }

    editing?.let { category ->
        PlanEditorDialog(
            state = state,
            categoryId = category.id,
            onSave = {
                onSaveCategoryPlan(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (editingSavings) {
        SavingsTargetDialog(
            current = state.savingsTarget,
            income = month.incomePlanned,
            onDismiss = { editingSavings = false },
            onConfirm = {
                onSetSavingsTarget(it)
                editingSavings = false
            },
        )
    }

    editingFund?.let { fund ->
        FundSettingsDialog(
            account = fund,
            onDismiss = { editingFund = null },
            onConfirm = { monthly, goal, day ->
                onSetFund(fund.id, monthly, goal, day)
                editingFund = null
            },
        )
    }

    if (confirmCopy) {
        AlertDialog(
            onDismissRequest = { confirmCopy = false },
            title = { Text("Скопировать план?") },
            text = { Text(Hints.PLAN_COPY) },
            confirmButton = {
                PrimaryButton(onClick = {
                    onCopyPlan()
                    confirmCopy = false
                }) { Text("Скопировать") }
            },
            dismissButton = { TextButton(onClick = { confirmCopy = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun PlanSummaryTiles(month: MonthModel, period: Period, wide: Boolean) {
    val colors = AppTheme.colors
    val negative = month.unallocated.isNegative
    TileRow(
        wide,
        listOf(
            { m ->
                StatTile(
                    if (negative) "Не хватает" else "Не распределено",
                    MoneyFormat.format(if (negative) -month.unallocated else month.unallocated, false),
                    month.available.takeIf { it.minor > 0L }?.let { "${MoneyFormat.formatShare(month.unallocated.minor.coerceAtLeast(0L).toDouble() / it.minor)} из ${MoneyFormat.format(it, false)} на месяц" }
                        ?: "задай доход в плане",
                    modifier = m,
                    accent = !negative,
                    valueColor = if (negative) colors.bad else null,
                    info = Hints.UNALLOCATED,
                )
            },
            { m -> StatTile("Доход по плану", MoneyFormat.format(month.incomePlanned, false), "регулярный доход этого месяца", modifier = m) },
            { m ->
                StatTile(
                    "Остаток с прошлого месяца",
                    MoneyFormat.formatSigned(month.carryOver, false),
                    "было на картах и наличных ${DateFormat.dayMonth(period.start)}",
                    modifier = m,
                    valueColor = if (month.carryOver.isNegative) colors.bad else null,
                    info = Hints.CARRY_OVER,
                )
            },
        ),
    )
}

@Composable
private fun AllocationCard(month: MonthModel) {
    val colors = AppTheme.colors
    val negative = month.unallocated.isNegative
    val parts = listOf(
        Triple("Кредиты", month.debtsPlanned, MaterialTheme.colorScheme.outline),
        Triple("Взносы в копилки", month.fundsPlanned, colors.warning),
        Triple("Конверты", month.flexiblePlanned, MaterialTheme.colorScheme.primary),
        Triple("Себе", month.savingsTarget ?: Money.zero(Currency.BASE), colors.save),
        Triple(if (negative) "Не хватает" else "Не распределено", if (negative) -month.unallocated else month.unallocated, if (negative) colors.bad else colors.good),
    ).filter { it.second.minor > 0L }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (month.available.minor > 0L) {
                SegmentBar(parts.filterNot { negative && it.first == "Не хватает" }.map { it.second to it.third })
            }
            Column {
                parts.forEachIndexed { index, (label, money, color) ->
                    if (index > 0) HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                        Text(label, modifier = Modifier.weight(1f))
                        AmountCell(MoneyFormat.format(money, false), bold = true)
                        AmountCell(
                            month.available.takeIf { it.minor > 0L }?.let { MoneyFormat.formatShare(money.shareOf(it)) } ?: "",
                            muted = true,
                            width = SHARE_WIDTH,
                        )
                    }
                }
            }
        }
    }
}

private val SHARE_WIDTH = 56.dp


@Composable
private fun SegmentBar(parts: List<Pair<Money, Color>>) {
    val total = parts.sumOf { it.first.minor.coerceAtLeast(0L) }
    if (total <= 0L) return
    Row(
        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        parts.filter { it.first.minor > 0L }.forEach { (money, color) ->
            Box(modifier = Modifier.weight(money.minor.toFloat() / total).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun PlanRow(name: String, planned: Money?, onClick: () -> Unit, sub: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            planned?.let { MoneyFormat.format(it, false) } ?: "задать",
            fontWeight = if (planned != null) FontWeight.SemiBold else null,
            color = if (planned != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SavingsCard(month: MonthModel, capital: CapitalModel?, onClick: () -> Unit, onOpenCapital: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val target = month.savingsTarget
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (target == null) "Задать «Себе»" else "${MoneyFormat.format(target, false)} в месяц",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (target == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (target != null && month.incomePlanned.minor > 0L) {
                        Text(
                            "${MoneyFormat.formatShare(target.shareOf(month.incomePlanned))} дохода · внесено ${MoneyFormat.format(month.saved.coerceAtLeastZero(), false)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            capital?.let { c ->
                HorizontalDivider()
                val byPlan = c.cushionPath(Scenario.PLAN, CapitalModel.YEAR_MONTHS).last()
                val byFact = c.cushionPath(Scenario.FACT, CapitalModel.YEAR_MONTHS).last()
                PlanLine("В подушке сейчас", MoneyFormat.format(c.cushion, false))
                HorizontalDivider()
                PlanLine("Через 12 мес., если откладывать по плану", MoneyFormat.format(byPlan, false), bold = true)
                HorizontalDivider()
                c.paceMedian?.let { median ->
                    PlanLine("Через 12 мес. «как сейчас»: ${MoneyFormat.formatSigned(median, false)} в месяц, медиана за ${c.paceMonths} мес.", MoneyFormat.format(byFact, false))
                }
                TextButton(onClick = onOpenCapital, modifier = Modifier.padding(start = 0.dp)) { Text("Подробнее в «Капитале» →") }
            }
        }
    }
}

@Composable
private fun DebtsPlanCard(state: HomeState, onOpenDebts: () -> Unit) {
    val month = state.month
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDebts)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (month.debts.isEmpty()) {
                Text("Кредитов нет. Добавь их в «Кредитах», платежи попадут в план сами.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            }
            month.debts.sortedWith(compareBy(nullsLast()) { it.due }).forEachIndexed { index, d ->
                if (index > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.debt.name, modifier = Modifier.weight(1f))
                    Text(d.due?.let { "к ${it.day}-му" }.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                    Text(MoneyFormat.format(d.planned, false), fontWeight = FontWeight.Medium)
                }
            }
            if (month.debts.isNotEmpty()) {
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("Итого по кредитам", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(MoneyFormat.format(month.debtsPlanned, false), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FundsPlanCard(state: HomeState, onEditFund: (Account) -> Unit, onOpenAccounts: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            state.fundAccounts.forEachIndexed { index, fund ->
                if (index > 0) HorizontalDivider()
                val monthly = fund.fundMonthly?.let(state.rates::toBaseOrNull)
                val balance = state.rates.toBaseOrNull(state.balances.getValue(fund.id))
                PlanRow(
                    fund.name,
                    monthly,
                    onClick = { onEditFund(fund) },
                    sub = listOfNotNull(
                        balance?.let { "в копилке ${MoneyFormat.format(it, false)}" },
                        if (monthly == null) "взнос в этом месяце не планируется" else "взнос в месяц",
                        fund.fundGoal?.let { goal -> fund.fundDay?.let { d -> "оплата ${MoneyFormat.format(goal, false)} $d-го" } ?: "цель ${MoneyFormat.format(goal, false)}" },
                    ).joinToString(" · "),
                )
            }
            if (state.fundAccounts.isEmpty()) {
                PlanRow("Копилок нет", null, onClick = onOpenAccounts, sub = "аренда, подарки, поездки: заведи счёт-копилку в «Счетах»")
            }
            Text(
                "Трату, которую платишь частично из копилки, запиши двумя операциями: часть со счёта копилки, часть с карты. В плане категории оставь только часть из дохода.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun CategoryPlanCard(budget: CategoryBudget, history: CategoryHistory?, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(budget.category.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    if (budget.hasPlan) MoneyFormat.format(budget.planned, false) else "задать",
                    fontWeight = if (budget.hasPlan) FontWeight.SemiBold else null,
                    color = if (budget.hasPlan) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
            }
            history?.takeIf { it.average.minor > 0L }?.let { h ->
                val differs = budget.hasPlan && PlanCalculator.differsFromUsual(budget.planned, h)
                Text(
                    "обычно ~${MoneyFormat.format(h.average.withoutFraction(), false)} в месяц",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (differs) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val items = budget.items.filter { it.planned != null && it.subcategory != null }
            if (items.isNotEmpty()) HorizontalDivider()
            items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(item.subcategory?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(item.planned?.let { MoneyFormat.format(it, false) }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (items.isNotEmpty() && budget.freePlanned.minor > 0L) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("прочее", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(MoneyFormat.format(budget.freePlanned, false), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private val SAVINGS_SHARES = listOf(10, 20, 30)
private const val PERCENT = 100L

@Composable
private fun SavingsTargetDialog(current: Money?, income: Money, onDismiss: () -> Unit, onConfirm: (Money?) -> Unit) {
    var text by remember { mutableStateOf(current?.let(MoneyFormat::editText).orEmpty()) }
    val transformation = remember { ThousandsVisualTransformation() }
    val parsed = if (text.isBlank()) null else MoneyFormat.parse(text, Currency.BASE)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Себе в этом месяце") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = ThousandsVisualTransformation.sanitize(it, Currency.BASE.minorUnits) },
                    label = { Text("Отложить в подушку, ${Currency.BASE.code}") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (income.minor > 0L) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SAVINGS_SHARES.forEach { percent ->
                            TagChip(text = "$percent %", onClick = { text = (income.wholeUnits * percent / PERCENT).toString() })
                        }
                    }
                    Text("10, 20 или 30 % от планового дохода", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { PrimaryButton(onClick = { onConfirm(parsed) }, enabled = text.isBlank() || parsed != null) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
