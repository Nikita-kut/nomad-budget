package ru.nomadbudget.presentation.month

import ru.nomadbudget.presentation.components.Ui
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.BudgetCalculator
import ru.nomadbudget.domain.logic.BudgetStatus
import ru.nomadbudget.domain.logic.CategoryBudget
import ru.nomadbudget.domain.logic.FundState
import ru.nomadbudget.domain.logic.MonthModel
import ru.nomadbudget.domain.logic.ObligationStatus
import ru.nomadbudget.domain.logic.Pace
import ru.nomadbudget.domain.logic.SavingsProgress
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.components.CurrencyAmount
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.InfoHint
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.StatusPill
import ru.nomadbudget.presentation.components.PillTone
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.centeredSide
import ru.nomadbudget.presentation.components.StatTile
import ru.nomadbudget.presentation.components.TileRow
import ru.nomadbudget.presentation.components.EqualRow

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

    val overview: LazyListScope.() -> Unit = {
        state.error?.let { message ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Повторить") }
                    }
                }
            }
        }
        if (state.isFirstRun) {
            item { FirstRunCard(state, onOpenAccounts, onOpenPlanning, onOpenEntry) }
        }
        item { MonthTiles(month, state.saving, onCopyPlan, onOpenPlanning, onOpenDebts, wide = false) }
        item { SectionTitle("Доходы", hint = "факт из плана", info = Hints.INCOME) }
        item { IncomeCard(month) }
        if (!month.corrections.isZero) {
            item {
                Text(
                    "Расхождения со счетами за месяц: ${MoneyFormat.formatSigned(month.corrections, false)}. Это корректировки при сверке, в бюджете не участвуют.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
    val envelopes: LazyListScope.() -> Unit = {
        item { SectionTitle("Конверты", hint = "потрачено из плана", info = Hints.ENVELOPES) }
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
                onToggle = { expandedCategoryId = if (expandedCategoryId == budget.category.id) null else budget.category.id },
            )
        }
        if (idle.isNotEmpty()) {
            item { IdleLine(idle.map { it.category.name }, onOpenPlanning) }
        }
        if (month.funds.isNotEmpty()) {
            item { SectionTitle("Копилки", hint = "откладываешь на крупное", info = Hints.FUNDS) }
            item { FundsCard(month.funds, onOpenPlanning) }
        }
    }
    val padding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp)
    if (twoColumns) {
        WideMonth(
            state = state,
            active = active,
            idle = idle,
            expandedCategoryId = expandedCategoryId,
            onToggle = { id -> expandedCategoryId = if (expandedCategoryId == id) null else id },
            onCopyPlan = onCopyPlan,
            onOpenDebts = onOpenDebts,
            onOpenPlanning = onOpenPlanning,
            onOpenAccounts = onOpenAccounts,
            onOpenEntry = onOpenEntry,
            onRetry = onRetry,
        )
    } else {
        CenteredLazyColumn(maxContentWidth = 760.dp) {
            overview()
            envelopes()
        }
    }
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
                Button(onClick = onCopyPlan, enabled = !saving, modifier = Modifier.weight(1f)) { Text("Скопировать") }
                OutlinedButton(onClick = onOpenPlanning, modifier = Modifier.weight(1f)) { Text("Планировать") }
            }
        }
    }
}

private fun paceNote(month: MonthModel): String? {
    val planned = month.flexiblePlannedWhole.minor
    if (planned <= 0L) return null
    val expected = (planned * month.elapsedShare).toLong()
    val delta = month.flexibleFactWhole.minor - expected
    val tolerance = (planned * BudgetCalculator.PACE_TOLERANCE).toLong()
    return when {
        delta > tolerance -> "идёшь быстрее плана"
        delta < -tolerance -> "идёшь медленнее плана"
        else -> "в темпе плана"
    }
}



@Composable
private fun IncomeCard(month: MonthModel, modifier: Modifier = Modifier) {
    val incomes = month.incomeBudgets.filterNot { it.isIdle }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            if (incomes.isEmpty()) {
                Text("Доходов и плана дохода нет", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            incomes.forEachIndexed { index, budget ->
                if (index > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(budget.category.name)
                    Text(
                        if (budget.hasPlan) "${MoneyFormat.format(budget.fact, false)} / ${MoneyFormat.format(budget.planned, false)}" else "${MoneyFormat.format(budget.fact, false)} · без плана",
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
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(budget.category.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (budget.hasPlan) "${MoneyFormat.format(budget.fact, false)} / ${MoneyFormat.format(budget.planned, false)}" else "${MoneyFormat.format(budget.fact, false)} · без плана",
                    color = when {
                        over -> tone
                        !budget.hasPlan -> colors.warning
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (over) FontWeight.SemiBold else null,
                )
            }
            if (!budget.fundFact.isZero) {
                Text("+ ${MoneyFormat.format(budget.fundFact, false)} оплачено из копилки, в конверт не входит", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (budget.hasPlan) {
                ProgressTrack(
                    fraction = budget.progress.toFloat(),
                    marker = if (state.month.isCurrent) (budget.expectedByNow(state.month.elapsedShare).minor.toDouble() / budget.planned.minor).toFloat() else null,
                    color = if (over) tone else MaterialTheme.colorScheme.primary,
                )
            }
            when {
                budget.status == BudgetStatus.OVER -> Text("перерасход ${MoneyFormat.format(-budget.remainingWhole, false)}", style = MaterialTheme.typography.bodySmall, color = colors.bad)
                budget.status == BudgetStatus.OVER_SLIGHTLY -> Text("чуть выше плана: +${MoneyFormat.format(-budget.remainingWhole, false)}", style = MaterialTheme.typography.bodySmall, color = colors.warning)
                ahead -> Text("быстрее плана на ${MoneyFormat.format(budget.aheadBy(state.month.elapsedShare), false)}", style = MaterialTheme.typography.bodySmall, color = colors.warning)
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
                            Text(
                                "${MoneyFormat.format(item.fact, false)} с карт + ${MoneyFormat.format(item.fundFact, false)} из копилки = ${MoneyFormat.format(item.fact + item.fundFact, false)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item.remainingWhole?.let { left ->
                            Text(
                                if (left.isNegative) "перерасход ${MoneyFormat.format(-left, false)}" else if (left.isZero) "оплачено" else "осталось ${MoneyFormat.format(left, false)}",
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
                val accountsById = state.accountsById
                val records = state.inPeriod.filterIsInstance<Transaction.Expense>()
                    .filter { it.categoryId == budget.category.id && BudgetCalculator.isEnvelopeSpend(it, accountsById) }
                    .sortedByDescending { it.date }
                if (records.isEmpty()) {
                    Text("Записей пока нет", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                records.forEach { tx ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(DateFormat.dayMonth(tx.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            listOfNotNull(state.subcategoryName(tx.subcategoryId), tx.note.ifBlank { null }).joinToString(" · ").ifEmpty { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (tx.amount.currency != Currency.BASE) {
                            CurrencyAmount(MoneyFormat.format(tx.amount), tx.amount.currency, state, MaterialTheme.typography.bodySmall)
                        }
                        Text(MoneyFormat.format(tx.amountBase, false), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FundsCard(funds: List<FundState>, onOpenPlanning: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onOpenPlanning)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            funds.forEachIndexed { index, fund ->
                if (index > 0) HorizontalDivider()
                Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fund.account.name, fontWeight = FontWeight.Medium)
                        Text(MoneyFormat.format(fund.balance, false), fontWeight = FontWeight.SemiBold)
                    }
                    val parts = buildList {
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
                            fund.isEnough == true -> Text("к оплате ${MoneyFormat.format(goal, false)} к ${fund.due.day}-му · хватает", style = MaterialTheme.typography.bodySmall, color = colors.good)
                            else -> Text("к оплате ${MoneyFormat.format(goal, false)} к ${fund.due.day}-му · не хватает ${MoneyFormat.format(shortBy, false)}", style = MaterialTheme.typography.bodySmall, color = colors.warning)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
fun ProgressTrack(fraction: Float, marker: Float?, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
        }
        marker?.let { share ->
            Box(modifier = Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(12.dp), contentAlignment = Alignment.CenterEnd) {
                Box(modifier = Modifier.width(2.dp).height(12.dp).background(MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}

private fun share(part: Money, whole: Money): Float =
    if (whole.minor <= 0L) 0f else (part.minor.toDouble() / whole.minor).toFloat()

@Composable
private fun FirstRunCard(state: HomeState, onOpenAccounts: () -> Unit, onOpenPlanning: () -> Unit, onOpenEntry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("С чего начать", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FirstRunStep("1", "Проверь счета и остатки", if (state.activeAccounts.isEmpty()) "добавь счета" else "счетов: ${state.activeAccounts.size}", "Счета", onOpenAccounts)
            FirstRunStep("2", "Задай план месяца", "доход, «Себе», расходы по категориям", "План", onOpenPlanning)
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
        OutlinedButton(onClick = onClick) { Text(action) }
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
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val side = centeredSide(maxWidth, 1240.dp)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = side, end = side, top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text("Повторить") }
                }
            }
        }
        if (state.isFirstRun) FirstRunCard(state, onOpenAccounts, onOpenPlanning, onOpenEntry)
        MonthTiles(month, state.saving, onCopyPlan, onOpenPlanning, onOpenDebts, wide = true)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Конверты", hint = "потрачено с карт и наличных из плана", info = Hints.ENVELOPES)
            EnvelopeTable(active, state, expandedCategoryId, onToggle)
            if (idle.isNotEmpty()) IdleLine(idle.map { it.category.name }, onOpenPlanning)
        }
        EqualRow(
            wide = true,
            cells = listOfNotNull(
                {
                    SectionTitle("Доходы", hint = "факт из плана", info = Hints.INCOME)
                    IncomeCard(month, Modifier.weight(1f))
                },
                if (month.funds.isNotEmpty()) {
                    {
                        SectionTitle("Копилки", hint = "откладываешь на крупное", info = Hints.FUNDS)
                        FundsCard(month.funds, onOpenPlanning, Modifier.weight(1f))
                    }
                } else {
                    null
                },
            ),
        )
        if (!month.corrections.isZero) {
            Text(
                "Расхождения со счетами: ${MoneyFormat.formatSigned(month.corrections, false)}. Это корректировки при сверке, в бюджете не участвуют.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
}

@Composable
private fun EnvelopeTable(budgets: List<CategoryBudget>, state: HomeState, expandedCategoryId: String?, onToggle: (String) -> Unit) {
    val colors = AppTheme.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Категория", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(1.3f))
                Text("Прогресс", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(1.6f))
                Text("Потрачено", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.width(AMOUNT_COL), textAlign = TextAlign.End)
                Text("План", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.width(AMOUNT_COL), textAlign = TextAlign.End)
                Text("Осталось", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.width(AMOUNT_COL), textAlign = TextAlign.End)
            }
            budgets.forEach { budget ->
                HorizontalDivider()
                val over = budget.status == BudgetStatus.OVER || budget.status == BudgetStatus.OVER_SLIGHTLY
                val tone = if (budget.status == BudgetStatus.OVER) colors.bad else if (over) colors.warning else MaterialTheme.colorScheme.primary
                Column(modifier = Modifier.fillMaxWidth().clickable { onToggle(budget.category.id) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text(budget.category.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!budget.fundFact.isZero) Text("+ ${MoneyFormat.format(budget.fundFact, false)} из копилки", style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                        Box(modifier = Modifier.weight(1.6f).padding(end = 16.dp)) {
                            if (budget.hasPlan) {
                                ProgressTrack(
                                    fraction = budget.progress.toFloat(),
                                    marker = if (state.month.isCurrent) (budget.expectedByNow(state.month.elapsedShare).minor.toDouble() / budget.planned.minor).toFloat() else null,
                                    color = tone,
                                )
                            } else {
                                Text("без плана", style = MaterialTheme.typography.bodySmall, color = colors.warning)
                            }
                        }
                        AmountCell(budget.fact, if (over) tone else null)
                        AmountCell(budget.planned.takeIf { budget.hasPlan }, null, muted)
                        AmountCell(budget.remainingWhole.takeIf { budget.hasPlan }, if (budget.remainingWhole.isNegative) colors.bad else null)
                    }
                    if (expandedCategoryId == budget.category.id) {
                        budget.items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1.3f).padding(start = 16.dp)) {
                                    Text(item.subcategory?.name ?: "без подкатегории", style = MaterialTheme.typography.bodyMedium)
                                    if (!item.fundFact.isZero) Text("+ ${MoneyFormat.format(item.fundFact, false)} из копилки", style = MaterialTheme.typography.labelSmall, color = muted)
                                }
                                Spacer(modifier = Modifier.weight(1.6f))
                                AmountCell(item.fact, null, style = MaterialTheme.typography.bodyMedium)
                                AmountCell(item.planned, null, muted, style = MaterialTheme.typography.bodyMedium)
                                AmountCell(item.remainingWhole, item.remainingWhole?.takeIf { it.isNegative }?.let { colors.bad }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        val records = state.inPeriod.filterIsInstance<Transaction.Expense>()
                            .filter { it.categoryId == budget.category.id && it.debtId == null }
                            .sortedByDescending { it.date }
                        if (records.isNotEmpty()) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        records.forEach { tx ->
                            val fromFund = state.accountsById[tx.accountId]?.isFund == true
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(DateFormat.dayMonth(tx.date), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.width(44.dp))
                                Text(
                                    listOfNotNull(state.subcategoryName(tx.subcategoryId), tx.note.ifBlank { null }).joinToString(" · ").ifEmpty { "—" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(state.accountName(tx.accountId) + if (fromFund) " · копилка" else "", style = MaterialTheme.typography.bodySmall, color = muted)
                                if (tx.amount.currency != Currency.BASE) {
                                    CurrencyAmount(MoneyFormat.format(tx.amount), tx.amount.currency, state, MaterialTheme.typography.bodySmall)
                                }
                                Text(MoneyFormat.format(tx.amountBase, false), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(AMOUNT_COL), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
            if (budgets.isNotEmpty()) {
                HorizontalDivider()
                val month = state.month
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Итого", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2.9f))
                    AmountCell(month.flexibleFactWhole, null, bold = true)
                    AmountCell(month.flexiblePlannedWhole, null, muted, bold = true)
                    AmountCell(month.canSpend, if (month.canSpend.isNegative) colors.bad else null, bold = true)
                }
            }
        }
    }
}

@Composable
private fun AmountCell(
    money: Money?,
    color: Color?,
    fallback: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    bold: Boolean = false,
) {
    Text(
        money?.let { MoneyFormat.format(it, false) } ?: "—",
        style = style,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        color = color ?: fallback,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(AMOUNT_COL),
    )
}

private val AMOUNT_COL = 112.dp

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
                    month.perDay?.let { "≈ ${MoneyFormat.format(it, false)} в день · ${month.daysLeft} дн." },
                    "потрачено ${MoneyFormat.format(month.flexibleFactWhole, false)} из ${MoneyFormat.format(month.flexiblePlannedWhole, false)}",
                ).joinToString(" · "),
                modifier = m.clickable(onClick = onOpenPlanning),
                progress = share(month.flexibleFactWhole, month.flexiblePlannedWhole),
                progressColor = if (over) colors.bad else MaterialTheme.colorScheme.primary,
                marker = if (month.isCurrent) month.elapsedShare.toFloat() else null,
                accent = true,
                valueColor = if (over) colors.bad else null,
                info = Hints.CAN_SPEND,
            )
        }
    }
    val target = month.savingsTarget
    tiles += { m ->
        StatTile(
            label = "Отложено «Себе»",
            value = MoneyFormat.format(month.saved, false),
            sub = listOfNotNull(
                target?.let { "из ${MoneyFormat.format(it, false)}" + if (month.savingsLeft.isZero) " · выполнено" else " · ещё ${MoneyFormat.format(month.savingsLeft, false)}" } ?: "цель «Себе» не задана",
                month.takenFromCushion.takeUnless { it.isZero }?.let { "взято из подушки ${MoneyFormat.format(it, false)}" },
            ).joinToString(" · "),
            modifier = m,
            progress = target?.let { share(month.saved, it) } ?: 0f,
            progressColor = colors.save,
            valueColor = if (month.saved.isNegative) colors.bad else null,
            info = Hints.SAVED,
            subColor = if (month.savingsProgress == SavingsProgress.MISSED) colors.bad else null,
        )
    }
    if (month.debts.isNotEmpty()) {
        val left = month.debts.map { it.left }.fold(Money.zero(Currency.BASE)) { a, b -> a + b }
        val paid = month.debts.count { it.status == ObligationStatus.PAID }
        val next = month.nextObligation
        val overdue = month.debts.any { it.status == ObligationStatus.OVERDUE }
        tiles += { m ->
            StatTile(
                label = "Осталось по кредитам",
                value = MoneyFormat.format(left, false),
                sub = "оплачено $paid из ${month.debts.size}" + (next?.let { " · ближайший: ${it.debt.name}${it.due?.let { d -> " к ${d.day}-му" }.orEmpty()}" } ?: ""),
                modifier = m.clickable(onClick = onOpenDebts),
                progress = share(month.debtsPlanned - left, month.debtsPlanned),
                progressColor = if (overdue) colors.bad else colors.good,
                valueColor = if (overdue) colors.bad else null,
                info = Hints.OBLIGATIONS,
                subColor = if (overdue) colors.bad else null,
            )
        }
    }
    TileRow(wide, tiles)
}
