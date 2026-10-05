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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.CategoryBudget
import ru.nomadbudget.domain.logic.PlanSummary
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.InfoHint
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.CategoryPlanDraft
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.more.SubScreenHeader
import ru.nomadbudget.presentation.theme.AppTheme

@Composable
fun PlanningScreen(
    state: HomeState,
    onBack: (() -> Unit)?,
    onSaveCategoryPlan: (CategoryPlanDraft) -> Unit,
    onCopyPlan: () -> Unit,
) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var confirmCopy by remember { mutableStateOf(false) }
    val summary = state.planSummary
    val income = state.budgets.filter { it.category.kind == CategoryKind.INCOME }
    val expenses = state.budgets.filter { it.category.kind == CategoryKind.EXPENSE }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            item { SubScreenHeader("Планирование", onBack) }
        }
        item {
            Text(
                "План на ${state.period.title()}. Другой месяц выбирается стрелками в шапке.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SummaryCard(summary) }
        item {
            if (summary.expensePlanned.isZero) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("План расходов пока пустой", fontWeight = FontWeight.Medium)
                            InfoHint("Копирование плана", Hints.PLAN_COPY)
                        }
                        Button(onClick = onCopyPlan, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                            Text("Скопировать план с прошлого месяца")
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = { confirmCopy = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                    Text("Скопировать план с прошлого месяца")
                }
            }
        }

        item { SectionTitle("Доходы", hint = "план", info = Hints.INCOME) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (income.isEmpty()) EmptyHint("Категорий доходов нет")
                income.forEachIndexed { index, budget ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { editing = budget.category }.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(budget.category.name)
                        Text(
                            if (budget.hasPlan) MoneyFormat.format(budget.planned, false) else "задать",
                            fontWeight = if (budget.hasPlan) FontWeight.SemiBold else null,
                            color = if (budget.hasPlan) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        item {
            SectionTitle(
                "Расходы",
                hint = "${summary.plannedCategories} из ${summary.expenseCategories} с планом",
                info = Hints.PLAN_CATEGORY,
            )
        }
        items(expenses, key = { it.category.id }) { budget ->
            CategoryPlanCard(budget, onClick = { editing = budget.category })
        }

        if (summary.shares.isNotEmpty()) {
            item { SectionTitle("Структура плана", hint = if (summary.incomePlanned.minor > 0L) "доля от дохода" else "доля от расходов", info = Hints.PLAN_STRUCTURE) }
            item { StructureCard(summary) }
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
private fun SummaryCard(summary: PlanSummary) {
    val free = summary.free
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ЗАПЛАНИРОВАНО", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InfoHint("Сводка плана", Hints.PLAN_SUMMARY)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(MoneyFormat.format(summary.expensePlanned, false), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "из дохода ${MoneyFormat.format(summary.incomePlanned, false)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            summary.allocatedShare?.let { share ->
                LinearProgressIndicator(
                    progress = { share.toFloat().coerceIn(0f, 1f) },
                    color = if (share > 1.0) AppTheme.colors.bad else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            PlanLine(
                if (free.isNegative) "Перепланировано" else "Свободно от дохода",
                MoneyFormat.format(if (free.isNegative) -free else free, false) +
                    (summary.allocatedShare?.let { " · распределено ${MoneyFormat.formatShare(it)}" } ?: ""),
                bold = true,
                highlight = if (free.isNegative) AppTheme.colors.bad else AppTheme.colors.good,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            PlanLine("Свободные суммы", MoneyFormat.format(summary.freeAmounts, false))
            PlanLine("По подкатегориям", MoneyFormat.format(summary.itemized, false))
            PlanLine("Категорий с планом", "${summary.plannedCategories} из ${summary.expenseCategories}")
        }
    }
}

@Composable
private fun CategoryPlanCard(budget: CategoryBudget, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(budget.category.name, fontWeight = FontWeight.Medium)
                Text(
                    if (budget.hasPlan) MoneyFormat.format(budget.planned, false) else "задать план",
                    fontWeight = if (budget.hasPlan) FontWeight.SemiBold else null,
                    color = if (budget.hasPlan) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
            }
            val meta = buildList {
                if (budget.plannedByItems && !budget.freePlanned.isZero) add("свободно ${MoneyFormat.format(budget.freePlanned, false)}")
                if (budget.plannedByItems) add("подкатегории ${MoneyFormat.format(budget.itemsPlanned, false)}")
                if (!budget.fact.isZero) add("потрачено ${MoneyFormat.format(budget.fact, false)}")
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = muted)
            }
            val planned = budget.items.filter { it.planned != null && it.subcategory != null }
            if (planned.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                planned.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            item.subcategory?.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(item.planned?.let { MoneyFormat.format(it, false) }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StructureCard(summary: PlanSummary) {
    val maxShare = summary.shares.maxOfOrNull { it.share }?.coerceAtLeast(0.0001) ?: 1.0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            summary.shares.forEach { share ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(share.category.name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${MoneyFormat.format(share.planned, false)} · ${MoneyFormat.formatShare(share.share)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ShareBar((share.share / maxShare).toFloat())
                }
            }
            if (summary.incomePlanned.minor > 0L && !summary.free.isNegative && !summary.free.isZero) {
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Свободно", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(
                        "${MoneyFormat.format(summary.free, false)} · ${MoneyFormat.formatShare(summary.free.minor.toDouble() / summary.incomePlanned.minor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.good,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
