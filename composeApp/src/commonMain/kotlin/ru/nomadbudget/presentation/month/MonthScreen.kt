package ru.nomadbudget.presentation.month

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.FundTarget
import ru.nomadbudget.features.FeatureGate
import ru.nomadbudget.features.Feature
import ru.nomadbudget.presentation.components.SecondaryButton
import ru.nomadbudget.presentation.components.PrimaryButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.BudgetCalculator
import ru.nomadbudget.domain.logic.BudgetStatus
import ru.nomadbudget.domain.logic.CategoryBudget
import ru.nomadbudget.domain.logic.FundState
import ru.nomadbudget.domain.logic.MonthModel
import ru.nomadbudget.domain.logic.Pace
import ru.nomadbudget.domain.logic.SavingsProgress
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.components.AmountCell
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.CurrencyAmount
import ru.nomadbudget.presentation.components.EqualRow
import ru.nomadbudget.presentation.components.HeaderCell
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.ProgressTrack
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.StatTile
import ru.nomadbudget.presentation.components.TileRow
import ru.nomadbudget.presentation.components.Ui
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.runway
import ru.nomadbudget.presentation.format.plural
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

@Composable
fun MonthScreen(
    state: HomeState,
    onCopyPlan: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenPlanning: () -> Unit,
    onRetry: () -> Unit,
    twoColumns: Boolean = false,
    onOpenAccounts: () -> Unit = {},
    onOpenEntry: () -> Unit = {},
) {
    var expandedCategoryId by remember { mutableStateOf<String?>(null) }
    val month = state.month
    val (idle, active) = month.envelopes.filterNot(state::isDebtOnlyEnvelope).partition { it.isIdle }
    val onToggle = { id: String -> expandedCategoryId = if (expandedCategoryId == id) null else id }

    if (twoColumns) {
        WideMonth(
            state = state,
            active = active,
            idle = idle,
            expandedCategoryId = expandedCategoryId,
            onToggle = onToggle,
            onCopyPlan = onCopyPlan,
            onOpenDebts = onOpenDebts,
            onOpenPlanning = onOpenPlanning,
            onOpenAccounts = onOpenAccounts,
            onOpenEntry = onOpenEntry,
            onRetry = onRetry,
        )
        return
    }

    CenteredLazyColumn(maxContentWidth = Ui.NarrowContentMaxWidth) {
        state.error?.let { message -> item { ErrorCard(message, onRetry) } }
        if (state.isFirstRun) {
            item { FirstRunCard(state, onOpenAccounts, onOpenPlanning, onOpenEntry) }
        }
        item { MonthTiles(month, state.saving, onCopyPlan, onOpenPlanning, onOpenDebts, wide = false) }
        if (month.hasPlan || !month.outFact.isZero || !month.incomeFact.isZero) {
            item { SectionTitle("Итоги месяца", hint = "пришло и ушло", info = Hints.MONTH_TOTALS) }
            item { TotalsCard(month, onOpenPlanning) }
        }
        item { SectionTitle("Доходы", hint = "получено и план", info = Hints.INCOME) }
        item { IncomeCard(month) }
        if (!month.corrections.isZero) {
            item { CorrectionsNote(month, Modifier.padding(horizontal = 4.dp)) }
        }
        item { SectionTitle("Конверты", hint = "потрачено и план", info = Hints.ENVELOPES) }
        if (active.isEmpty()) {
            item {
                Text(
                    "Трат и плана по категориям пока нет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        items(active, key = { it.category.id }) { budget ->
            EnvelopeCard(
                budget = budget,
                state = state,
                expanded = expandedCategoryId == budget.category.id,
                onToggle = { onToggle(budget.category.id) },
            )
        }
        if (idle.isNotEmpty()) {
            item { IdleLine(idle.map { it.category.name }, onOpenPlanning) }
        }
        if (month.funds.isNotEmpty() && FeatureGate.isOn(Feature.FUNDS_MONTH)) {
            item { SectionTitle("Копилки", hint = "откладываешь на крупное", info = Hints.FUNDS) }
            item { FundsCard(month.funds, state::fundTargetLabel, onOpenPlanning) }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Повторить") }
        }
    }
}

@Composable
private fun CorrectionsNote(month: MonthModel, modifier: Modifier = Modifier) {
    Text(
        "Расхождения со счетами за месяц: ${MoneyFormat.formatSigned(month.corrections, false)}. Это корректировки при сверке, в бюджете не участвуют.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun SpendCard(month: MonthModel, saving: Boolean, onCopyPlan: () -> Unit, onOpenPlanning: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(Ui.CardPadding + 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("План расходов не задан", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (month.flexibleFact.isZero) "Задай план или скопируй прошлый месяц, и здесь появится, сколько ещё можно потратить."
                else "Потрачено ${MoneyFormat.format(month.flexibleFact, false)}. Задай план, чтобы видеть, сколько ещё можно.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f, fill = false))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(onClick = onCopyPlan, enabled = !saving, modifier = Modifier.weight(1f)) { Text("Скопировать") }
                SecondaryButton(onClick = onOpenPlanning, modifier = Modifier.weight(1f)) { Text("Планировать") }
            }
        }
    }
}

@Composable
private fun IncomeCard(month: MonthModel, modifier: Modifier = Modifier) {
    val incomes = month.incomeBudgets.filterNot { it.isIdle }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Ui.CardPadding, vertical = 6.dp)) {
            if (incomes.isEmpty()) {
                Text("Доходов и плана дохода нет", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            incomes.forEachIndexed { index, budget ->
                if (index > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(budget.category.name)
                    Text(
                        factOfPlan(budget),
                        color = if (budget.hasPlan && budget.fact.wholeUnits >= budget.planned.wholeUnits) AppTheme.colors.good else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (incomes.size > 1) {
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Итого", fontWeight = FontWeight.SemiBold)
                    Text("${MoneyFormat.format(month.incomeFact, false)} / ${MoneyFormat.format(month.incomePlanned, false)}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun fundNote(budget: CategoryBudget, month: MonthModel): String? = listOfNotNull(
    month.funds.filter { it.account.fundTarget?.categoryId == budget.category.id }.takeIf { it.isNotEmpty() }?.joinToString(", ") { fund ->
        "оплата из копилки «${fund.account.name}»" + (fund.monthsLeft?.let { " · ${runway(it)}" } ?: "")
    },
    budget.fundFact.takeUnless { it.isZero }?.let { "из них ${MoneyFormat.format(it, false)} из копилки" },
).joinToString(" · ").ifEmpty { null }

private fun factOfPlan(budget: CategoryBudget): String =
    if (budget.hasPlan) "${MoneyFormat.format(budget.fact, false)} / ${MoneyFormat.format(budget.planned, false)}" else "${MoneyFormat.format(budget.fact, false)} · без плана"

private fun expectedMarker(budget: CategoryBudget, month: MonthModel): Float? =
    if (month.isCurrent) budget.expectedByNow(month.elapsedShare).shareOf(budget.planned).toFloat() else null

@Composable
private fun EnvelopeCard(budget: CategoryBudget, state: HomeState, expanded: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    val over = budget.status == BudgetStatus.OVER || budget.status == BudgetStatus.OVER_SLIGHTLY
    val tone = when (budget.status) {
        BudgetStatus.OVER -> colors.bad
        BudgetStatus.OVER_SLIGHTLY -> colors.warning
        else -> MaterialTheme.colorScheme.onSurface
    }
    val showPace = state.month.isCurrent && budget.hasPlan && !over
    val ahead = showPace && budget.pace(state.month.elapsedShare) == Pace.AHEAD
    Card(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Ui.CardPadding, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(budget.category.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    factOfPlan(budget),
                    color = when {
                        over -> tone
                        !budget.hasPlan -> colors.warning
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (over) FontWeight.SemiBold else null,
                )
            }
            fundNote(budget, state.month)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (budget.hasPlan) {
                ProgressTrack(
                    fraction = budget.progress.toFloat(),
                    marker = expectedMarker(budget, state.month),
                    color = if (over) tone else MaterialTheme.colorScheme.primary,
                )
            }
            when {
                budget.status == BudgetStatus.OVER -> Text("перерасход ${MoneyFormat.format(-budget.remainingWhole, false)}", style = MaterialTheme.typography.bodySmall, color = colors.bad)
                budget.status == BudgetStatus.OVER_SLIGHTLY -> Text("чуть выше плана: +${MoneyFormat.format(-budget.remainingWhole, false)}", style = MaterialTheme.typography.bodySmall, color = colors.warning)
                ahead -> Text("опережаешь план на ${MoneyFormat.format(budget.aheadBy(state.month.elapsedShare), false)}", style = MaterialTheme.typography.bodySmall, color = colors.warning)
                else -> Unit
            }
            if (expanded) {
                HorizontalDivider()
                budget.items.forEach { item ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.subcategory?.name ?: "без подкатегории", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                item.planned?.let { "${MoneyFormat.format(item.fact, false)} / ${MoneyFormat.format(it, false)}" } ?: MoneyFormat.format(item.fact, false),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (!item.fundFact.isZero) {
                            Text("из них ${MoneyFormat.format(item.fundFact, false)} из копилки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item.remainingWhole?.let { left ->
                            Text(
                                when {
                                    left.isNegative -> "перерасход ${MoneyFormat.format(-left, false)}"
                                    left.isZero -> "оплачено"
                                    else -> "осталось ${MoneyFormat.format(left, false)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    left.isNegative -> colors.bad
                                    left.isZero -> colors.good
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                if (budget.freePlanned.minor > 0L && budget.plannedByItems) {
                    Text("прочее по плану ${MoneyFormat.format(budget.freePlanned, false)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                val records = envelopeRecords(state, budget.category.id)
                if (records.isEmpty()) {
                    Text("Записей пока нет", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                records.forEach { record ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(DateFormat.dayMonth(record.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            record.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (record.own.currency != state.base) {
                            CurrencyAmount(MoneyFormat.format(record.own), MaterialTheme.typography.bodySmall)
                        }
                        Text(MoneyFormat.format(record.base, false), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private data class EnvelopeRecord(
    val date: LocalDate,
    val title: String,
    val account: String,
    val own: Money,
    val base: Money,
)

private fun envelopeRecords(state: HomeState, categoryId: String): List<EnvelopeRecord> =
    BudgetCalculator.envelopeSpends(state.inPeriod, state.accountsById).filter { it.categoryId == categoryId }.map { spend ->
        val tx = spend.tx
        EnvelopeRecord(
            date = tx.date,
            title = listOfNotNull(state.subcategoryName(spend.subcategoryId), spend.fund?.let { "из копилки «${it.name}»" }, tx.note.ifBlank { null }).joinToString(" · ").ifEmpty { "—" },
            account = state.accountName(tx.accountId),
            own = tx.amount,
            base = spend.amountBase,
        )
    }.sortedByDescending { it.date }

@Composable
private fun FundsCard(funds: List<FundState>, targetLabel: (FundTarget) -> String?, onOpenPlanning: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Card(onClick = onOpenPlanning, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Ui.CardPadding, vertical = 6.dp)) {
            funds.forEachIndexed { index, fund ->
                if (index > 0) HorizontalDivider()
                Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fund.account.name, fontWeight = FontWeight.Medium)
                        Text(MoneyFormat.format(fund.balance, false), fontWeight = FontWeight.SemiBold)
                    }
                    val parts = buildList {
                        fund.account.fundTarget?.let(targetLabel)?.let { label ->
                            add("оплачивает «$label»" + (fund.targetPlanned?.takeIf { it.minor > 0L }?.let { " по ${MoneyFormat.format(it, false)} в месяц" } ?: ""))
                            fund.monthsLeft?.let { add(runway(it)) }
                        }
                        if (fund.monthlyPlanned == null && !fund.contributed.isZero) add("отложено ${MoneyFormat.format(fund.contributed, false)}")
                        fund.monthlyPlanned?.let { planned ->
                            add(if (fund.contributionLeft.isZero) "взнос ${MoneyFormat.format(planned, false)} внесён" else "взнос: ещё ${MoneyFormat.format(fund.contributionLeft, false)} из ${MoneyFormat.format(planned, false)}")
                        }
                        if (!fund.spent.isZero) add("потрачено ${MoneyFormat.format(fund.spent, false)}")
                    }
                    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    fund.goal?.let { goal ->
                        val shortBy = goal - fund.balance - fund.contributionLeft
                        when {
                            fund.due == null -> Text(
                                if (fund.balance >= goal) "цель ${MoneyFormat.format(goal, false)} собрана" else "цель ${MoneyFormat.format(goal, false)} · осталось накопить ${MoneyFormat.format(goal - fund.balance, false)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (fund.balance >= goal) colors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            !fund.spent.isZero -> Unit
                            fund.isEnough == true -> Text("оплата ${MoneyFormat.format(goal, false)} ${fund.due.day}-го · хватает", style = MaterialTheme.typography.bodySmall, color = colors.good)
                            else -> Text("оплата ${MoneyFormat.format(goal, false)} ${fund.due.day}-го · не хватает ${MoneyFormat.format(shortBy, false)}", style = MaterialTheme.typography.bodySmall, color = colors.warning)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(month: MonthModel, onOpenPlanning: () -> Unit) {
    val colors = AppTheme.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun shown(feature: Feature, fact: Money, plan: Money?): Boolean = FeatureGate.isOn(feature) || !fact.isZero || plan?.isZero == false
    Card(onClick = onOpenPlanning, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Ui.CardPadding, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TotalsRow("", "факт", "план", color = muted, style = MaterialTheme.typography.labelMedium)
            TotalsGroup("Пришло", colors.good)
            TotalsRow("Доходы", month.incomeFact, month.incomePlanned)
            if (!month.carryOver.isZero) TotalsRow("С прошлого месяца", month.carryOver, month.carryOver)
            if (!month.fromFundsFact.isZero || !month.fromFundsPlanned.isZero) TotalsRow("Из копилок", month.fromFundsFact, month.fromFundsPlanned)
            TotalsGroup("Ушло", colors.bad)
            TotalsRow("Конверты", month.flexibleFactWhole, month.flexiblePlannedWhole)
            if (shown(Feature.DEBTS_PLAN, month.debtsPaidFromIncome, month.debtsPlanned)) TotalsRow("Кредиты", month.debtsPaidFromIncome, month.debtsPlanned)
            if (shown(Feature.CUSHION_PLAN, month.savedFromIncome, month.savingsTarget)) TotalsRow("Отложить", month.savedFromIncome, month.savingsTarget ?: Money.zero(month.base))
            if (shown(Feature.FUNDS_PLAN, month.fundsContributed, month.fundsPlanned)) TotalsRow("Отложено в копилки", month.fundsContributed, month.fundsPlanned)
            TotalsRow("Всего ушло", month.outFact, month.outPlanned, bold = true)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            TotalsRow(
                "Остаётся",
                MoneyFormat.format(month.leftFact, false),
                MoneyFormat.format(month.available - month.outPlanned, false),
                bold = true,
                factColor = if (month.leftFact.isNegative) colors.bad else null,
                planColor = if ((month.available - month.outPlanned).isNegative) colors.bad else muted,
            )
            val notes = listOfNotNull(
                month.reservesToDebts.takeUnless { it.isZero }?.let { "на кредиты из резерва и копилок ещё ${MoneyFormat.format(it, false)}" },
                month.spentFromReserves.takeUnless { it.isZero }?.let { "потрачено из резерва ${MoneyFormat.format(it, false)}" },
            )
            if (notes.isNotEmpty()) {
                Text("Не из денег месяца: ${notes.joinToString(", ")}.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

@Composable
private fun TotalsGroup(title: String, color: Color) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun TotalsRow(label: String, fact: Money, plan: Money, bold: Boolean = false) {
    TotalsRow(label, MoneyFormat.format(fact, false), MoneyFormat.format(plan, false), bold = bold)
}

@Composable
private fun TotalsRow(
    label: String,
    fact: String,
    plan: String,
    bold: Boolean = false,
    color: Color? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    factColor: Color? = null,
    planColor: Color? = null,
) {
    val weight = if (bold) FontWeight.SemiBold else null
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style, fontWeight = weight, color = color ?: Color.Unspecified, modifier = Modifier.weight(1f).padding(end = 8.dp), maxLines = 2)
        Text(fact, style = style, fontWeight = weight, color = factColor ?: color ?: Color.Unspecified, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(TOTALS_AMOUNT_WIDTH))
        Text(plan, style = style, fontWeight = weight, color = planColor ?: color ?: muted, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(TOTALS_AMOUNT_WIDTH))
    }
}

private val TOTALS_AMOUNT_WIDTH = 100.dp

@Composable
private fun IdleLine(names: List<String>, onOpenPlanning: () -> Unit) {
    Text(
        "Без плана и трат: ${names.joinToString(", ")}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPlanning).padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun FirstRunCard(state: HomeState, onOpenAccounts: () -> Unit, onOpenPlanning: () -> Unit, onOpenEntry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("С чего начать", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FirstRunStep("1", "Проверь счета и остатки", if (state.activeAccounts.isEmpty()) "добавь счета" else "счетов: ${state.activeAccounts.size}", "Счета", onOpenAccounts)
            FirstRunStep("2", "Задай план месяца", "доход, «Отложить», расходы по категориям", "План", onOpenPlanning)
            FirstRunStep("3", "Запиши первую трату", "кнопка «+» внизу или здесь", "Записать", onOpenEntry)
        }
    }
}

@Composable
private fun FirstRunStep(number: String, title: String, hint: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SecondaryButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun WideMonth(
    state: HomeState,
    active: List<CategoryBudget>,
    idle: List<CategoryBudget>,
    expandedCategoryId: String?,
    onToggle: (String) -> Unit,
    onCopyPlan: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenPlanning: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenEntry: () -> Unit,
    onRetry: () -> Unit,
) {
    val month = state.month
    CenteredLazyColumn {
        state.error?.let { message -> item { ErrorCard(message, onRetry) } }
        if (state.isFirstRun) item { FirstRunCard(state, onOpenAccounts, onOpenPlanning, onOpenEntry) }
        item { MonthTiles(month, state.saving, onCopyPlan, onOpenPlanning, onOpenDebts, wide = true) }
        if (month.hasPlan || !month.outFact.isZero || !month.incomeFact.isZero) {
            item {
                Column {
                    SectionTitle("Итоги месяца", hint = "пришло и ушло", info = Hints.MONTH_TOTALS)
                    TotalsCard(month, onOpenPlanning)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Конверты", hint = "потрачено с карт и наличных и план", info = Hints.ENVELOPES)
                EnvelopeTable(active, state, expandedCategoryId, onToggle)
                if (idle.isNotEmpty()) IdleLine(idle.map { it.category.name }, onOpenPlanning)
            }
        }
        item {
            EqualRow(
                wide = true,
                cells = listOfNotNull(
                    {
                        SectionTitle("Доходы", hint = "получено и план", info = Hints.INCOME)
                        IncomeCard(month, Modifier.weight(1f))
                    },
                    if (month.funds.isNotEmpty() && FeatureGate.isOn(Feature.FUNDS_MONTH)) {
                        {
                            SectionTitle("Копилки", hint = "откладываешь на крупное", info = Hints.FUNDS)
                            FundsCard(month.funds, state::fundTargetLabel, onOpenPlanning, Modifier.weight(1f))
                        }
                    } else {
                        null
                    },
                ),
            )
        }
        if (!month.corrections.isZero) {
            item { CorrectionsNote(month) }
        }
    }
}

@Composable
private fun EnvelopeTable(budgets: List<CategoryBudget>, state: HomeState, expandedCategoryId: String?, onToggle: (String) -> Unit) {
    val colors = AppTheme.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Ui.CardPadding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Категория", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(NAME_WEIGHT))
                Text("Прогресс", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(PROGRESS_WEIGHT))
                HeaderCell("Потрачено")
                HeaderCell("План")
                HeaderCell("Осталось")
            }
            budgets.forEach { budget ->
                HorizontalDivider()
                val over = budget.status == BudgetStatus.OVER || budget.status == BudgetStatus.OVER_SLIGHTLY
                val tone = when {
                    budget.status == BudgetStatus.OVER -> colors.bad
                    over -> colors.warning
                    else -> MaterialTheme.colorScheme.primary
                }
                Column(modifier = Modifier.fillMaxWidth().clickable { onToggle(budget.category.id) }.padding(horizontal = Ui.CardPadding, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(NAME_WEIGHT)) {
                            Text(budget.category.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            fundNote(budget, state.month)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = muted) }
                        }
                        Box(modifier = Modifier.weight(PROGRESS_WEIGHT).padding(end = 16.dp)) {
                            if (budget.hasPlan) {
                                ProgressTrack(fraction = budget.progress.toFloat(), marker = expectedMarker(budget, state.month), color = tone)
                            } else {
                                Text("без плана", style = MaterialTheme.typography.bodySmall, color = colors.warning)
                            }
                        }
                        AmountCell(moneyOrDash(budget.fact), color = if (over) tone else null)
                        AmountCell(moneyOrDash(budget.planned.takeIf { budget.hasPlan }), muted = true)
                        AmountCell(moneyOrDash(budget.remainingWhole.takeIf { budget.hasPlan }), color = if (budget.remainingWhole.isNegative) colors.bad else null)
                    }
                    if (expandedCategoryId == budget.category.id) {
                        budget.items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(NAME_WEIGHT).padding(start = 16.dp)) {
                                    Text(item.subcategory?.name ?: "без подкатегории", style = MaterialTheme.typography.bodyMedium)
                                    if (!item.fundFact.isZero) Text("из них ${MoneyFormat.format(item.fundFact, false)} из копилки", style = MaterialTheme.typography.labelSmall, color = muted)
                                }
                                Spacer(modifier = Modifier.weight(PROGRESS_WEIGHT))
                                AmountCell(moneyOrDash(item.fact), style = MaterialTheme.typography.bodyMedium)
                                AmountCell(moneyOrDash(item.planned), muted = true, style = MaterialTheme.typography.bodyMedium)
                                AmountCell(moneyOrDash(item.remainingWhole), color = item.remainingWhole?.takeIf { it.isNegative }?.let { colors.bad }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        val records = envelopeRecords(state, budget.category.id)
                        if (records.isNotEmpty()) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        records.forEach { record ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(DateFormat.dayMonth(record.date), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.width(DATE_WIDTH))
                                Text(
                                    record.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(record.account, style = MaterialTheme.typography.bodySmall, color = muted)
                                if (record.own.currency != state.base) {
                                    CurrencyAmount(MoneyFormat.format(record.own), MaterialTheme.typography.bodySmall)
                                }
                                AmountCell(MoneyFormat.format(record.base, false), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (budgets.isNotEmpty()) {
                HorizontalDivider()
                val month = state.month
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Ui.CardPadding, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Итого", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(NAME_WEIGHT + PROGRESS_WEIGHT))
                    AmountCell(moneyOrDash(month.flexibleFactWhole), bold = true)
                    AmountCell(moneyOrDash(month.flexiblePlannedWhole), muted = true, bold = true)
                    AmountCell(moneyOrDash(month.canSpend), color = if (month.canSpend.isNegative) colors.bad else null, bold = true)
                }
            }
        }
    }
}

private fun moneyOrDash(money: Money?): String = money?.let { MoneyFormat.format(it, false) } ?: "—"

private const val NAME_WEIGHT = 1.3f
private const val PROGRESS_WEIGHT = 1.6f
private val DATE_WIDTH = 44.dp

@Composable
private fun MonthTiles(month: MonthModel, saving: Boolean, onCopyPlan: () -> Unit, onOpenPlanning: () -> Unit, onOpenDebts: () -> Unit, wide: Boolean) {
    val colors = AppTheme.colors
    val tiles = mutableListOf<@Composable (Modifier) -> Unit>()
    if (month.flexiblePlanned.isZero) {
        tiles += { m -> SpendCard(month, saving, onCopyPlan, onOpenPlanning, m) }
    } else {
        val over = month.canSpend.isNegative
        tiles += { m ->
            StatTile(
                label = if (over) "Перерасход по конвертам" else "Можно ещё потратить",
                value = MoneyFormat.format(if (over) -month.canSpend else month.canSpend, false),
                sub = listOfNotNull(
                    month.perDay?.let { "≈ ${MoneyFormat.format(it, false)} в день на ${plural(month.daysLeft, "день", "дня", "дней")}" },
                    "потрачено ${MoneyFormat.format(month.flexibleFactWhole, false)} из ${MoneyFormat.format(month.flexiblePlannedWhole, false)}",
                ).joinToString(" · "),
                modifier = m,
                onClick = onOpenPlanning,
                progress = month.flexibleFactWhole.shareOf(month.flexiblePlannedWhole).toFloat(),
                progressColor = if (over) colors.bad else MaterialTheme.colorScheme.primary,
                marker = if (month.isCurrent) month.elapsedShare.toFloat() else null,
                accent = true,
                valueColor = if (over) colors.bad else null,
                info = Hints.CAN_SPEND,
            )
        }
    }
    val target = month.savingsTarget
    if (FeatureGate.isOn(Feature.CUSHION_MONTH_TILE)) tiles += { m ->
        StatTile(
            label = "Резерв за месяц",
            value = MoneyFormat.formatSigned(month.cushionNet, false),
            sub = listOfNotNull(
                listOfNotNull(
                    "внесено ${MoneyFormat.format(month.savedIn, false)}",
                    month.cushionToDebts.takeUnless { it.isZero }?.let { "на кредиты ${MoneyFormat.format(it, false)}" },
                    month.cushionOtherOut.takeUnless { it.isZero }?.let { "снято ${MoneyFormat.format(it, false)}" },
                ).joinToString(" · "),
                target?.let { "Отложено ${MoneyFormat.format(month.saved.coerceAtLeastZero(), false)} из ${MoneyFormat.format(it, false)}" + if (month.savingsLeft.isZero) " · выполнено" else " · ещё ${MoneyFormat.format(month.savingsLeft, false)}" } ?: "сколько отложить — не задано",
            ).joinToString("\n"),
            modifier = m,
            progress = target?.let { month.saved.shareOf(it).toFloat() } ?: 0f,
            progressColor = colors.save,
            valueColor = null,
            info = Hints.SAVED,
            subColor = if (month.savingsProgress == SavingsProgress.MISSED) colors.bad else null,
        )
    }
    if (month.debts.isNotEmpty() && (FeatureGate.isOn(Feature.DEBTS_MONTH_TILE) || month.debtsOverdue)) {
        val next = month.nextObligation
        val overdue = month.debtsOverdue
        tiles += { m ->
            StatTile(
                label = "Осталось по кредитам",
                value = MoneyFormat.format(month.debtsLeft, false),
                sub = "оплачено ${month.debtsPaid} из ${month.debts.size}" + (next?.let { " · ближайший: ${it.debt.name}${it.due?.let { d -> " к ${d.day}-му" }.orEmpty()}" } ?: ""),
                modifier = m,
                onClick = onOpenDebts,
                progress = (month.debtsPlanned - month.debtsLeft).shareOf(month.debtsPlanned).toFloat(),
                progressColor = if (overdue) colors.bad else colors.good,
                valueColor = if (overdue) colors.bad else null,
                info = Hints.OBLIGATIONS,
                subColor = if (overdue) colors.bad else null,
            )
        }
    }
    TileRow(wide, tiles)
}
