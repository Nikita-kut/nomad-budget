package ru.nomadbudget.presentation.month

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.BudgetStatus
import ru.nomadbudget.domain.logic.CategoryBudget
import ru.nomadbudget.domain.logic.SubcategoryBudget
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.components.CurrencyAmount
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.InfoHint
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.TagChip
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.CategoryPlanDraft
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.planning.PlanEditorDialog
import ru.nomadbudget.presentation.theme.AppTheme
import ru.nomadbudget.domain.logic.Pace
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight

@Composable
fun MonthScreen(
    state: HomeState,
    onSetItemPlanned: (String, String, Money) -> Unit,
    onRemoveItem: (String, String?) -> Unit,
    onSaveCategoryPlan: (CategoryPlanDraft) -> Unit,
    onCopyPlan: () -> Unit,
    onOpenPlanning: () -> Unit,
    onRetry: () -> Unit,
    twoColumns: Boolean = false,
) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var editingItem by remember { mutableStateOf<ItemEdit?>(null) }
    var expandedCategoryId by remember { mutableStateOf<String?>(null) }

    val income = state.budgets.filter { it.category.kind == CategoryKind.INCOME }
    val expenses = state.budgets.filter { it.category.kind == CategoryKind.EXPENSE }
    val (idleExpenses, activeExpenses) = expenses.partition { it.isIdle }

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

        item { SummaryRow(state) }
        item { OperationalRow(state) }

        if (!state.loading) {
            item { PlanTeaser(state, onCopyPlan, onOpenPlanning) }
        }

        item { SectionTitle("Доходы", hint = "нажми, чтобы изменить план", info = Hints.INCOME) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (income.isEmpty()) EmptyHint("Категорий доходов нет")
                income.forEachIndexed { index, budget ->
                    if (index > 0) HorizontalDivider()
                    IncomeRow(budget, onEditPlan = { editing = budget.category })
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Итого", fontWeight = FontWeight.SemiBold)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(MoneyFormat.format(state.summary.incomeFact, false), fontWeight = FontWeight.SemiBold)
                        Text("план ${MoneyFormat.format(state.summary.incomePlanned, false)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

    }
    val envelopes: LazyListScope.() -> Unit = {
        item { SectionTitle("Расходы по конвертам", hint = if (state.isCurrentPeriod) "метка на полосе — где должен быть расход сегодня" else null, info = Hints.ENVELOPES) }
        items(activeExpenses, key = { it.category.id }) { budget ->
            ExpenseCard(
                budget = budget,
                state = state,
                expanded = expandedCategoryId == budget.category.id,
                onToggle = { expandedCategoryId = if (expandedCategoryId == budget.category.id) null else budget.category.id },
                onEditPlan = { editing = budget.category },
                onAddItem = { editingItem = ItemEdit(budget.category, null) },
                onEditItem = { item -> editingItem = ItemEdit(budget.category, item) },
            )
        }
        if (idleExpenses.isNotEmpty()) {
            item { IdleCategoriesCard(idleExpenses.map { it.category }, onPlan = { editing = it }) }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Итого расходы", fontWeight = FontWeight.SemiBold)
                Text(
                    "${MoneyFormat.format(state.summary.expenseFact, false)} из ${MoneyFormat.format(state.summary.expensePlanned, false)}",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

    }
    val padding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp)
    if (twoColumns) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp), content = overview)
            LazyColumn(modifier = Modifier.weight(1.2f).fillMaxHeight(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp), content = envelopes)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            overview()
            envelopes()
        }
    }

    editing?.let { category ->
        PlanEditorDialog(
            state = state,
            initialCategoryId = category.id,
            onSave = onSaveCategoryPlan,
            onDismiss = { editing = null },
        )
    }

    editingItem?.let { edit ->
        ItemPlanDialog(
            edit = edit,
            suggestions = state.subcategories.filter { it.categoryId == edit.category.id }.map { it.name },
            onDismiss = { editingItem = null },
            onConfirm = { name, planned ->
                onSetItemPlanned(edit.category.id, name, planned)
                editingItem = null
            },
            onDelete = {
                onRemoveItem(edit.category.id, edit.item?.subcategory?.id)
                editingItem = null
            },
        )
    }
}

private data class ItemEdit(val category: Category, val item: SubcategoryBudget?)

@Composable
private fun PlanTeaser(state: HomeState, onCopyPlan: () -> Unit, onOpenPlanning: () -> Unit) {
    val plan = state.planSummary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("План месяца", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                InfoHint("План месяца", Hints.PLAN_SUMMARY)
            }
            if (plan.expensePlanned.isZero) {
                Text(
                    "Пока пустой. Скопируй лимиты с прошлого месяца или задай их в планировании.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onCopyPlan, enabled = !state.saving, modifier = Modifier.weight(1f)) { Text("Скопировать") }
                    OutlinedButton(onClick = onOpenPlanning, modifier = Modifier.weight(1f)) { Text("Планировать") }
                }
            } else {
                val free = plan.free
                Text(
                    "Расходы ${MoneyFormat.format(plan.expensePlanned, false)}" +
                        (if (plan.savingsTarget.isZero) "" else " и себе ${MoneyFormat.format(plan.savingsTarget, false)}") +
                        " из дохода ${MoneyFormat.format(plan.incomePlanned, false)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (free.isNegative) "перепланировано на ${MoneyFormat.format(-free, false)}" else "свободно ${MoneyFormat.format(free, false)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (free.isNegative) AppTheme.colors.bad else AppTheme.colors.good,
                )
                OutlinedButton(onClick = onOpenPlanning, modifier = Modifier.fillMaxWidth()) { Text("Открыть планирование") }
            }
        }
    }
}

@Composable
private fun SummaryRow(state: HomeState) {
    val summary = state.summary
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("Потрачено", MoneyFormat.format(summary.expenseFact, false), "план ${MoneyFormat.format(summary.expensePlanned, false)}", Modifier.weight(1f), info = Hints.SPENT)
        val target = state.savingsTarget
        SummaryCard(
            "Отложено себе",
            MoneyFormat.format(summary.netSaved, false),
            when {
                target != null -> "из ${MoneyFormat.format(target, false)} по плану"
                summary.takenFromSavings.isZero -> "в накопления"
                else -> "взято ${MoneyFormat.format(summary.takenFromSavings, false)}"
            },
            Modifier.weight(1f),
            negative = summary.netSaved.isNegative,
            positive = target != null && summary.netSaved.wholeUnits >= target.wholeUnits,
            info = Hints.SAVED,
        )
        SummaryCard(
            "Остаток месяца",
            MoneyFormat.format(summary.remaining, false),
            "доход ${MoneyFormat.format(summary.incomeFact, false)}",
            Modifier.weight(1f),
            negative = summary.remaining.isNegative,
            info = Hints.REMAINING,
        )
    }
}

@Composable
private fun OperationalRow(state: HomeState) {
    val delta = state.operationalBase - state.operationalAtPeriodStartBase
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("На жизнь, без накоплений", fontWeight = FontWeight.Medium)
                    InfoHint("На жизнь", Hints.OPERATIONAL)
                }
                Text(
                    "на начало месяца ${MoneyFormat.format(state.operationalAtPeriodStartBase, false)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyFormat.format(state.operationalBase, false), fontWeight = FontWeight.SemiBold)
                Text(
                    "${MoneyFormat.formatSigned(delta, showFraction = false)} за месяц",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (delta.isNegative) AppTheme.colors.bad else AppTheme.colors.good,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, sub: String, modifier: Modifier = Modifier, negative: Boolean = false, positive: Boolean = false, info: String? = null) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                info?.let { InfoHint(label, it) }
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    negative -> AppTheme.colors.bad
                    positive -> AppTheme.colors.good
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun IncomeRow(budget: CategoryBudget, onEditPlan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEditPlan).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(budget.category.name, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                MoneyFormat.format(budget.fact, false),
                fontWeight = FontWeight.SemiBold,
                color = if (budget.hasPlan && budget.fact.wholeUnits >= budget.planned.wholeUnits) AppTheme.colors.good else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (budget.hasPlan) "план ${MoneyFormat.format(budget.planned, false)}" else "без плана",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpenseCard(
    budget: CategoryBudget,
    state: HomeState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditPlan: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (SubcategoryBudget) -> Unit,
) {
    val colors = AppTheme.colors
    val showPace = state.isCurrentPeriod && budget.hasPlan && budget.status in setOf(BudgetStatus.OK, BudgetStatus.WARNING)
    val pace = if (showPace) budget.pace(state.elapsedShare) else Pace.ON_TRACK
    val tone = when (budget.status) {
        BudgetStatus.OVER -> colors.bad
        BudgetStatus.OVER_SLIGHTLY -> colors.warning
        BudgetStatus.DONE -> colors.good
        BudgetStatus.WARNING, BudgetStatus.OK -> if (pace == Pace.AHEAD) colors.warning else colors.good
    }
    val headline = when {
        !budget.hasPlan -> "без плана: ${MoneyFormat.format(budget.fact, false)}"
        budget.status == BudgetStatus.OVER -> "перерасход ${MoneyFormat.format(-budget.remainingWhole, false)}"
        budget.status == BudgetStatus.OVER_SLIGHTLY -> "чуть выше плана: +${MoneyFormat.format(-budget.remainingWhole, false)}"
        budget.status == BudgetStatus.DONE -> "ровно по плану"
        else -> "осталось ${MoneyFormat.format(budget.remainingWhole, false)}"
    }
    val paceNote = when {
        !showPace -> null
        pace == Pace.AHEAD -> "быстрее плана на ${MoneyFormat.format(budget.aheadBy(state.elapsedShare), false)}"
        else -> "в темпе"
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(budget.category.name, fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(if (budget.transactionCount > 0) "${budget.transactionCount} зап." else null, paceNote).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pace == Pace.AHEAD) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = tone, modifier = Modifier.padding(end = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${MoneyFormat.format(budget.fact, false)} из ${MoneyFormat.format(budget.planned, false)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = onEditPlan, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Изменить план «${budget.category.name}»", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            PaceBar(
                progress = budget.progress.toFloat(),
                marker = if (state.isCurrentPeriod && budget.hasPlan) (budget.expectedByNow(state.elapsedShare).minor.toDouble() / budget.planned.minor).toFloat() else null,
                color = tone,
                modifier = Modifier.padding(top = 6.dp, end = 10.dp),
            )
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (budget.items.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Подкатегория", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("план · факт · остаток", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                budget.items.forEach { item ->
                    val remaining = item.remainingWhole
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onEditItem(item) }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.subcategory?.name ?: "без подкатегории", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.planned?.let { MoneyFormat.format(it, false) } ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(MoneyFormat.format(item.fact, false), style = MaterialTheme.typography.bodySmall)
                            Text(
                                remaining?.let { MoneyFormat.formatSigned(it) } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    remaining == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    remaining.isNegative -> AppTheme.colors.bad
                                    remaining.isZero -> AppTheme.colors.good
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                TextButton(onClick = onAddItem) { Text("Добавить строку плана") }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                val lines = state.inPeriod.filterIsInstance<Transaction.Expense>().filter { it.categoryId == budget.category.id }
                if (lines.isEmpty()) {
                    Text("Записей пока нет", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                lines.forEach { tx ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Text(DateFormat.dayMonth(tx.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                listOfNotNull(state.subcategoryName(tx.subcategoryId), tx.note.ifBlank { null }).joinToString(" · ").ifEmpty { "—" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemPlanDialog(
    edit: ItemEdit,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, Money) -> Unit,
    onDelete: () -> Unit,
) {
    val existing = edit.item
    var name by remember { mutableStateOf(existing?.subcategory?.name.orEmpty()) }
    var text by remember {
        mutableStateOf(existing?.planned?.let { (it.minor / Currency.BASE.minorFactor).toString() }.orEmpty())
    }
    val transformation = remember { ThousandsVisualTransformation() }
    val parsed = MoneyFormat.parse(text, Currency.BASE)
    val nameLocked = existing?.subcategory != null
    val filtered = suggestions.filter { name.isBlank() || it.contains(name, ignoreCase = true) }.take(MAX_SUGGESTIONS)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Строка плана: ${edit.category.name}" else "План: ${existing.subcategory?.name ?: "без подкатегории"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!nameLocked) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Подкатегория") },
                        placeholder = { Text("выбери или впиши новую") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (filtered.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filtered.forEach { s -> TagChip(text = s, selected = s.equals(name, ignoreCase = true), onClick = { name = s }) }
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = ThousandsVisualTransformation.sanitize(it) },
                    label = { Text("План на месяц, ₽") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing?.subcategory == null && existing != null) {
                    Text(
                        "Траты без подкатегории. Чтобы планировать их, задай подкатегорию у самих операций.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let { onConfirm(name, it) } },
                enabled = parsed != null && name.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (existing?.planned != null) {
                    TextButton(onClick = onDelete) { Text("Удалить", color = AppTheme.colors.bad) }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

private const val MAX_SUGGESTIONS = 8

@Composable
private fun PaceBar(progress: Float, marker: Float?, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        marker?.let { share ->
            Box(modifier = Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(12.dp), contentAlignment = Alignment.CenterEnd) {
                Box(modifier = Modifier.width(2.dp).height(12.dp).background(MaterialTheme.colorScheme.onSurface))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleCategoriesCard(categories: List<Category>, onPlan: (Category) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Без плана и трат: ${categories.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { category -> TagChip(text = category.name, onClick = { onPlan(category) }) }
            }
        }
    }
}
