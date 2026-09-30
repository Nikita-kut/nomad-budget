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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

@Composable
fun MonthScreen(
    state: HomeState,
    onSetPlanned: (String, Money) -> Unit,
    onSetItemPlanned: (String, String, Money) -> Unit,
    onRemoveItem: (String, String?) -> Unit,
    onCopyPlan: () -> Unit,
    onRetry: () -> Unit,
) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var editingItem by remember { mutableStateOf<ItemEdit?>(null) }
    var expandedCategoryId by remember { mutableStateOf<String?>(null) }

    val income = state.budgets.filter { it.category.kind == CategoryKind.INCOME }
    val expenses = state.budgets.filter { it.category.kind == CategoryKind.EXPENSE }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

        if (!state.hasExpensePlan && !state.loading) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("План на этот месяц пока пустой", fontWeight = FontWeight.Medium)
                        Text(
                            "Скопируй лимиты с прошлого месяца и поправь, что изменилось, или задай их по одному, нажимая на «план» у категории.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onCopyPlan, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                            Text("Скопировать план с прошлого месяца")
                        }
                    }
                }
            }
        }

        item { SectionTitle("Доходы", hint = "план · факт") }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(MoneyFormat.format(state.summary.incomePlanned, false), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MoneyFormat.format(state.summary.incomeFact, false), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item { SectionTitle("Расходы по конвертам", hint = "нажми на план, чтобы изменить") }
        items(expenses, key = { it.category.id }) { budget ->
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

    editing?.let { category ->
        val budget = state.budgets.firstOrNull { it.category.id == category.id }
        if (budget?.plannedByItems == true) {
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text("План: ${category.name}") },
                text = { Text("План этой категории складывается из строк по подкатегориям. Раскрой категорию и правь строки.") },
                confirmButton = { TextButton(onClick = { editing = null }) { Text("Понятно") } },
            )
        } else {
            PlanDialog(
                category = category,
                current = budget?.planned ?: Money.zero(Currency.BASE),
                onDismiss = { editing = null },
                onConfirm = { planned ->
                    onSetPlanned(category.id, planned)
                    editing = null
                },
            )
        }
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
private fun SummaryRow(state: HomeState) {
    val summary = state.summary
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("Потрачено", MoneyFormat.format(summary.expenseFact, false), "план ${MoneyFormat.format(summary.expensePlanned, false)}", Modifier.weight(1f))
        SummaryCard(
            "Отложено себе",
            MoneyFormat.format(summary.netSaved, false),
            if (summary.takenFromSavings.isZero) "в накопления" else "взято ${MoneyFormat.format(summary.takenFromSavings, false)}",
            Modifier.weight(1f),
            negative = summary.netSaved.isNegative,
        )
        SummaryCard(
            "Остаток месяца",
            MoneyFormat.format(summary.remaining, false),
            "доход ${MoneyFormat.format(summary.incomeFact, false)}",
            Modifier.weight(1f),
            negative = summary.remaining.isNegative,
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
                Text("На жизнь, без накоплений", fontWeight = FontWeight.Medium)
                Text(
                    "на начало месяца ${MoneyFormat.format(state.operationalAtPeriodStartBase, false)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyFormat.format(state.operationalBase, false), fontWeight = FontWeight.SemiBold)
                Text(
                    "${MoneyFormat.formatSigned(delta)} за месяц",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (delta.isNegative) AppTheme.colors.bad else AppTheme.colors.good,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, sub: String, modifier: Modifier = Modifier, negative: Boolean = false) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (negative) AppTheme.colors.bad else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun IncomeRow(budget: CategoryBudget, onEditPlan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(budget.category.name)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                MoneyFormat.format(budget.planned, false),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onEditPlan),
            )
            Text(MoneyFormat.format(budget.fact, false), fontWeight = FontWeight.SemiBold)
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
    val statusColor = AppTheme.colors.status(budget.status)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(budget.category.name, fontWeight = FontWeight.Medium)
                    if (budget.transactionCount > 0) {
                        Text("${budget.transactionCount} зап.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(MoneyFormat.format(budget.fact, false), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = when (budget.status) {
                                BudgetStatus.OVER -> "перерасход ${MoneyFormat.format(-budget.remaining, false)}"
                                BudgetStatus.WARNING, BudgetStatus.OK -> "осталось ${MoneyFormat.format(budget.remaining, false)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (budget.status == BudgetStatus.OVER) AppTheme.colors.bad else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "· план ${MoneyFormat.format(budget.planned, false)}${if (budget.plannedByItems) " (${budget.items.count { it.planned != null }} стр.)" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable(onClick = onEditPlan),
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { budget.progress.toFloat().coerceIn(0f, 1f) },
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    val remaining = item.remaining
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
                                Text(MoneyFormat.format(tx.amount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            filtered.forEach { s -> SuggestionChip(onClick = { name = s }, label = { Text(s) }) }
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
private fun PlanDialog(category: Category, current: Money, onDismiss: () -> Unit, onConfirm: (Money) -> Unit) {
    var text by remember { mutableStateOf(if (current.isZero) "" else (current.minor / Currency.BASE.minorFactor).toString()) }
    val transformation = remember { ThousandsVisualTransformation() }
    val parsed = MoneyFormat.parse(text, Currency.BASE)
    val isZero = text.trim() == "0" || text.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("План: ${category.name}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = ThousandsVisualTransformation.sanitize(it) },
                label = { Text("Сумма на месяц, ₽") },
                singleLine = true,
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(parsed ?: Money.zero(Currency.BASE)) },
                enabled = parsed != null || isZero,
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
