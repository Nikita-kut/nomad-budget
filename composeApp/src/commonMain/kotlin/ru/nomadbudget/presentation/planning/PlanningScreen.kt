package ru.nomadbudget.presentation.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import ru.nomadbudget.domain.logic.SavingsForecast
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
) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var confirmCopy by remember { mutableStateOf(false) }
    var editingSavings by remember { mutableStateOf(false) }
    var editingFund by remember { mutableStateOf<Account?>(null) }
    val month = state.month

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            item { SubScreenHeader("Планирование", onBack) }
        }
        item {
            Text(
                "План на ${DateFormat.monthName(state.period.start).lowercase()}: ${DateFormat.periodRange(state.period)}. Другой месяц выбирается стрелками в шапке.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { UnallocatedCard(month) }
        item {
            if (!month.hasPlan) {
                Button(onClick = onCopyPlan, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Скопировать план с прошлого месяца") }
            } else {
                OutlinedButton(onClick = { confirmCopy = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                    Text("Скопировать план с прошлого месяца")
                }
            }
        }

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
        item { SavingsCard(month, state.savingsForecast, onClick = { editingSavings = true }) }

        item { SectionTitle("Обязательные", hint = "собираются сами", info = Hints.OBLIGATIONS) }
        item { ObligationsPlanCard(state, onOpenDebts, onEditFund = { editingFund = it }, onOpenAccounts = onOpenAccounts) }

        item { SectionTitle("Гибкие", hint = "план · обычно", info = Hints.PLAN_CATEGORY) }
        items(month.envelopes.filterNot { it.category.isArchived || state.isDebtOnlyEnvelope(it) }, key = { it.category.id }) { budget ->
            CategoryPlanCard(budget, state.categoryHistory[budget.category.id], onClick = { editing = budget.category })
        }
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
                Button(onClick = {
                    onCopyPlan()
                    confirmCopy = false
                }) { Text("Скопировать") }
            },
            dismissButton = { TextButton(onClick = { confirmCopy = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun UnallocatedCard(month: MonthModel) {
    val colors = AppTheme.colors
    val negative = month.unallocated.isNegative
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (negative) "Не хватает" else "Не распределено", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                InfoHint("Не распределено", Hints.UNALLOCATED)
            }
            Text(
                MoneyFormat.format(if (negative) -month.unallocated else month.unallocated, false),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (negative) colors.bad else colors.good,
            )
            if (month.incomePlanned.minor > 0L) {
                SegmentBar(
                    listOf(
                        month.obligationsPlanned to MaterialTheme.colorScheme.outline,
                        month.flexiblePlanned to MaterialTheme.colorScheme.primary,
                        (month.savingsTarget ?: Money.zero(Currency.BASE)) to colors.save,
                        (if (negative) Money.zero(Currency.BASE) else month.unallocated) to colors.good,
                    ),
                )
            }
            Text(
                buildString {
                    append("доход ${MoneyFormat.format(month.incomePlanned, false)}")
                    append(" = обязательные ${MoneyFormat.format(month.obligationsPlanned, false)}")
                    append(" + гибкие ${MoneyFormat.format(month.flexiblePlanned, false)}")
                    append(" + себе ${MoneyFormat.format(month.savingsTarget ?: Money.zero(Currency.BASE), false)}")
                    month.allocatedShare?.let { append(" · распределено ${MoneyFormat.formatShare(it)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
private fun SavingsCard(month: MonthModel, forecast: SavingsForecast?, onClick: () -> Unit) {
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
                            "${MoneyFormat.formatShare(target.minor.toDouble() / month.incomePlanned.minor)} дохода · отложено ${MoneyFormat.format(month.saved, false)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            forecast?.let { f ->
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Прогноз подушки", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    InfoHint("Прогноз накоплений", Hints.FORECAST)
                }
                PlanLine("В подушке сейчас", MoneyFormat.format(f.balance, false))
                f.byTargetInYear?.let { PlanLine("Через год по плану", MoneyFormat.format(it, false), bold = true) }
                f.byActualInYear?.let { total ->
                    PlanLine(
                        "Через год при твоём темпе",
                        MoneyFormat.format(total, false),
                        highlight = f.actualPerMonth?.takeIf { it.isNegative }?.let { AppTheme.colors.bad },
                    )
                }
                f.actualPerMonth?.let {
                    Text("темп за ${f.monthsUsed} мес.: ${MoneyFormat.formatSigned(it, false)} в месяц", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ObligationsPlanCard(state: HomeState, onOpenDebts: () -> Unit, onEditFund: (Account) -> Unit, onOpenAccounts: () -> Unit) {
    val month = state.month
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            val extra = state.openDebts.mapNotNull { state.rates.toBaseOrNull(it.extraPayment) }.fold(Money.zero(Currency.BASE)) { a, b -> a + b }
            PlanRow(
                if (month.debts.isEmpty()) "Кредитов нет" else "Кредиты, платежей: ${month.debts.size}",
                month.debtsPlanned.takeIf { month.debts.isNotEmpty() },
                onClick = onOpenDebts,
                sub = if (month.debts.isEmpty()) "добавь в «Кредитах», платежи попадут сюда сами" else "из экрана «Кредиты»" + (if (extra.isZero) "" else " · досрочно ${MoneyFormat.format(extra, false)}"),
            )
            state.fundAccounts.forEach { fund ->
                HorizontalDivider()
                val monthly = fund.fundMonthly?.let(state.rates::toBaseOrNull)
                PlanRow(
                    "Взнос в «${fund.name}»",
                    monthly,
                    onClick = { onEditFund(fund) },
                    sub = fund.fundGoal?.let { goal -> fund.fundDay?.let { d -> "копилка · к оплате ${MoneyFormat.format(goal, false)} к $d-му" } ?: "копилка · цель ${MoneyFormat.format(goal, false)}" } ?: "копилка",
                )
            }
            if (state.fundAccounts.isEmpty()) {
                HorizontalDivider()
                PlanRow("Копилок нет", null, onClick = onOpenAccounts, sub = "аренда, подарки, поездки: заведи счёт-копилку в «Счетах»")
            }
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
                val differs = budget.hasPlan && abs(h.average.minor - budget.planned.minor).toDouble() / h.average.minor > DIFF_THRESHOLD
                Text(
                    "обычно ~${MoneyFormat.format(h.average.withoutFraction(), false)} в месяц",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (differs) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val items = budget.items.filter { it.planned != null && it.subcategory != null }
            items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth()) {
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

private const val DIFF_THRESHOLD = 0.2

@Composable
private fun SavingsTargetDialog(current: Money?, income: Money, onDismiss: () -> Unit, onConfirm: (Money?) -> Unit) {
    var text by remember { mutableStateOf(current?.let(::majorText).orEmpty()) }
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
                    label = { Text("Отложить в подушку, ₽") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (income.minor > 0L) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 20, 30).forEach { percent ->
                            TagChip(text = "$percent %", onClick = { text = (income.wholeUnits * percent / 100).toString() })
                        }
                    }
                    Text("Проценты считаются от планового дохода", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(parsed) }, enabled = text.isBlank() || parsed != null) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
