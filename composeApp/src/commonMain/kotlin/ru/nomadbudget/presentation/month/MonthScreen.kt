package ru.nomadbudget.presentation.month

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

@Composable
fun MonthScreen(
    state: HomeState,
    onSetPlanned: (String, Money) -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
) {
    var editing by remember { mutableStateOf<Category?>(null) }
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

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSignOut) { Text("Выйти из аккаунта") }
            }
        }
    }

    editing?.let { category ->
        val current = state.budgets.firstOrNull { it.category.id == category.id }?.planned ?: Money.zero(Currency.BASE)
        PlanDialog(
            category = category,
            current = current,
            onDismiss = { editing = null },
            onConfirm = { planned ->
                onSetPlanned(category.id, planned)
                editing = null
            },
        )
    }
}

@Composable
private fun SummaryRow(state: HomeState) {
    val summary = state.summary
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("Потрачено", MoneyFormat.format(summary.expenseFact, false), "план ${MoneyFormat.format(summary.expensePlanned, false)}", Modifier.weight(1f))
        SummaryCard("Отложено себе", MoneyFormat.format(summary.savedToSavings, false), "в накопления", Modifier.weight(1f))
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
                            "· план ${MoneyFormat.format(budget.planned, false)}",
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

@Composable
private fun PlanDialog(category: Category, current: Money, onDismiss: () -> Unit, onConfirm: (Money) -> Unit) {
    var text by remember { mutableStateOf(if (current.isZero) "" else (current.minor / Currency.BASE.minorFactor).toString()) }
    val parsed = MoneyFormat.parse(text, Currency.BASE)
    val isZero = text.trim() == "0" || text.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("План: ${category.name}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Сумма на месяц, ₽") },
                singleLine = true,
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
