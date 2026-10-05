package ru.nomadbudget.presentation.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.CategoryBudget
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.sumIn
import ru.nomadbudget.presentation.components.TagChip
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.CategoryPlanDraft
import ru.nomadbudget.presentation.home.PlanItemInput
import ru.nomadbudget.presentation.theme.AppTheme

private const val MAX_SUGGESTIONS = 8

private data class PlanRow(val key: Int, val subcategoryId: String?, val name: String, val amount: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPlanDialog(
    category: Category,
    budget: CategoryBudget?,
    suggestions: List<String>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (CategoryPlanDraft) -> Unit,
) {
    val initialRows = remember(budget) {
        budget?.items.orEmpty()
            .filter { it.planned != null && it.subcategory != null }
            .mapIndexed { index, item ->
                PlanRow(index, item.subcategory?.id, item.subcategory?.name.orEmpty(), majorText(item.planned ?: Money.zero(Currency.BASE)))
            }
    }
    var rows by remember { mutableStateOf(initialRows) }
    var nextKey by remember { mutableIntStateOf(initialRows.size) }
    var limitText by remember { mutableStateOf(budget?.limit?.let(::majorText).orEmpty()) }
    val transformation = remember { ThousandsVisualTransformation() }

    val parsedRows = rows.map { row -> row to MoneyFormat.parse(row.amount, Currency.BASE) }
    val rowsValid = parsedRows.all { (row, money) -> row.name.isNotBlank() && money != null }
    val namesUnique = rows.map { it.name.trim().lowercase() }.let { it.size == it.toSet().size }
    val itemsTotal = parsedRows.mapNotNull { it.second }.sumIn(Currency.BASE)
    val limit = MoneyFormat.parse(limitText, Currency.BASE)?.takeIf { it.minor > 0L }
    val limitValid = limitText.isBlank() || MoneyFormat.parse(limitText, Currency.BASE) != null
    val effective = when {
        limit == null -> itemsTotal
        rows.isEmpty() -> limit
        else -> maxOf(limit, itemsTotal)
    }
    val usedNames = rows.map { it.name.trim().lowercase() }.toSet()
    val freeSuggestions = suggestions.filter { it.lowercase() !in usedNames }.take(MAX_SUGGESTIONS)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("План: ${category.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = ThousandsVisualTransformation.sanitize(it) },
                    label = { Text("Лимит категории, ₽") },
                    placeholder = { Text("можно не задавать") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Общая сумма на категорию. Подкатегории ниже расписывают её часть. Без лимита план сложится из подкатегорий.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text("Подкатегории", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                rows.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { v -> rows = rows.map { if (it.key == row.key) it.copy(name = v) else it } },
                            label = { Text("На что") },
                            singleLine = true,
                            enabled = row.subcategoryId == null,
                            modifier = Modifier.weight(1.2f),
                        )
                        OutlinedTextField(
                            value = row.amount,
                            onValueChange = { v -> rows = rows.map { if (it.key == row.key) it.copy(amount = ThousandsVisualTransformation.sanitize(v)) else it } },
                            label = { Text("₽") },
                            singleLine = true,
                            visualTransformation = transformation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { rows = rows.filterNot { it.key == row.key } }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Убрать подкатегорию", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (freeSuggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        freeSuggestions.forEach { name ->
                            TagChip(text = "+ $name", onClick = {
                                rows = rows + PlanRow(nextKey, null, name, "")
                                nextKey++
                            })
                        }
                    }
                }
                TextButton(onClick = {
                    rows = rows + PlanRow(nextKey, null, "", "")
                    nextKey++
                }) { Text("Добавить подкатегорию") }
                HorizontalDivider()
                if (rows.isNotEmpty()) {
                    PlanLine("Расписано по подкатегориям", MoneyFormat.format(itemsTotal, false))
                }
                if (limit != null && rows.isNotEmpty()) {
                    when {
                        itemsTotal > limit -> PlanLine(
                            "Больше лимита на",
                            MoneyFormat.format(itemsTotal - limit, false),
                            highlight = AppTheme.colors.bad,
                        )
                        itemsTotal < limit -> PlanLine("Не расписано", MoneyFormat.format(limit - itemsTotal, false))
                        else -> Unit
                    }
                }
                PlanLine("План категории", MoneyFormat.format(effective, false), bold = true)
                if (!namesUnique) {
                    Text("Подкатегории повторяются", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.bad)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val keptIds = rows.mapNotNull { it.subcategoryId }.toSet()
                    val removed = initialRows.mapNotNull { it.subcategoryId }.filterNot { it in keptIds }
                    val items = parsedRows.mapNotNull { (row, money) -> money?.let { PlanItemInput(row.name.trim(), it) } }
                    onSave(CategoryPlanDraft(category.id, limit, items, removed))
                },
                enabled = !saving && rowsValid && limitValid && namesUnique,
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun AmountPlanDialog(title: String, current: Money, onDismiss: () -> Unit, onConfirm: (Money) -> Unit) {
    var text by remember { mutableStateOf(if (current.isZero) "" else majorText(current)) }
    val transformation = remember { ThousandsVisualTransformation() }
    val parsed = MoneyFormat.parse(text, Currency.BASE)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                enabled = parsed != null || text.isBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
internal fun PlanLine(
    label: String,
    value: String,
    bold: Boolean = false,
    highlight: androidx.compose.ui.graphics.Color? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            color = highlight ?: if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            color = highlight ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

internal fun majorText(money: Money): String {
    val major = money.minor / money.currency.minorFactor
    val fraction = money.minor % money.currency.minorFactor
    return if (fraction == 0L) major.toString() else "$major,${fraction.toString().padStart(money.currency.minorUnits, '0')}"
}
