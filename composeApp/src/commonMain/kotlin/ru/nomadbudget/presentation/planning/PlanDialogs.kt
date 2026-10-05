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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.CategoryBudget
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.sumIn
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.TagChip
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.CategoryPlanDraft
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.home.PlanItemInput
import ru.nomadbudget.presentation.theme.AppTheme

private const val MAX_SUGGESTIONS = 8

private data class PlanRow(val key: Int, val subcategoryId: String?, val name: String, val amount: String)

private data class EditorResult(val draft: CategoryPlanDraft?, val dirty: Boolean, val error: String?)

@Composable
fun PlanEditorDialog(
    state: HomeState,
    initialCategoryId: String,
    onSave: (CategoryPlanDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val categories = state.budgets.filterNot { it.category.isArchived }.map { it.category }
    var categoryId by remember { mutableStateOf(initialCategoryId) }
    var savedCategoryId by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf(EditorResult(null, false, null)) }
    val category = categories.firstOrNull { it.id == categoryId } ?: return
    val budget = state.budgets.firstOrNull { it.category.id == categoryId }
    val index = categories.indexOf(category)

    fun save(): Boolean {
        val draft = current.draft ?: return false
        onSave(draft)
        savedCategoryId = categoryId
        return true
    }

    fun switchTo(target: Category) {
        if (target.id == categoryId) return
        if (current.dirty && !save()) return
        categoryId = target.id
        savedCategoryId = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { categories.getOrNull(index - 1)?.let(::switchTo) }, enabled = index > 0 && !state.saving) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущая категория")
                }
                Dropdown(
                    label = if (category.kind == CategoryKind.INCOME) "Доход" else "Расход",
                    items = categories,
                    selected = category,
                    itemLabel = Category::name,
                    onSelect = ::switchTo,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { categories.getOrNull(index + 1)?.let(::switchTo) }, enabled = index < categories.lastIndex && !state.saving) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующая категория")
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                key(categoryId, budget?.planned, budget?.items?.size) {
                    CategoryEditor(
                        category = category,
                        budget = budget,
                        suggestions = state.subcategories.filter { it.categoryId == category.id }.map { it.name },
                        onChange = {
                            current = it
                            if (it.dirty) savedCategoryId = null
                        },
                    )
                }
                current.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.bad) }
                if (savedCategoryId == categoryId && !current.dirty) {
                    Text("Сохранено. Можно выбрать следующую категорию.", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.good)
                } else if (current.dirty) {
                    Text(
                        "При переходе к другой категории изменения сохранятся.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { save() }, enabled = !state.saving && current.dirty && current.draft != null) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (current.dirty) "Закрыть без сохранения" else "Закрыть") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditor(
    category: Category,
    budget: CategoryBudget?,
    suggestions: List<String>,
    onChange: (EditorResult) -> Unit,
) {
    val isIncome = category.kind == CategoryKind.INCOME
    val initialRows = remember {
        budget?.items.orEmpty()
            .filter { it.planned != null && it.subcategory != null }
            .mapIndexed { index, item ->
                PlanRow(index, item.subcategory?.id, item.subcategory?.name.orEmpty(), majorText(item.planned ?: Money.zero(Currency.BASE)))
            }
    }
    val initialFree = remember { budget?.freePlanned?.takeIf { it.minor > 0L }?.let(::majorText).orEmpty() }
    var rows by remember { mutableStateOf(initialRows) }
    var nextKey by remember { mutableIntStateOf(initialRows.size) }
    var freeText by remember { mutableStateOf(initialFree) }
    val transformation = remember { ThousandsVisualTransformation() }

    val parsedRows = rows.map { row -> row to MoneyFormat.parse(row.amount, Currency.BASE) }
    val rowsValid = parsedRows.all { (row, money) -> row.name.isNotBlank() && money != null }
    val namesUnique = rows.map { it.name.trim().lowercase() }.let { it.size == it.toSet().size }
    val itemsTotal = parsedRows.mapNotNull { it.second }.sumIn(Currency.BASE)
    val free = if (freeText.isBlank()) Money.zero(Currency.BASE) else MoneyFormat.parse(freeText, Currency.BASE)
    val dirty = freeText != initialFree || rows != initialRows
    val error = when {
        free == null -> "Свободная сумма не распознана"
        !rowsValid -> "У каждой подкатегории нужны название и сумма"
        !namesUnique -> "Подкатегории повторяются"
        else -> null
    }
    val draft = if (error == null && free != null) {
        val keptIds = rows.mapNotNull { it.subcategoryId }.toSet()
        CategoryPlanDraft(
            categoryId = category.id,
            free = free.takeIf { it.minor > 0L },
            items = parsedRows.mapNotNull { (row, money) -> money?.let { PlanItemInput(row.name.trim(), it) } },
            removedSubcategoryIds = initialRows.mapNotNull { it.subcategoryId }.filterNot { it in keptIds },
        )
    } else {
        null
    }
    val result = EditorResult(draft, dirty, error.takeIf { dirty })
    SideEffect { onChange(result) }

    OutlinedTextField(
        value = freeText,
        onValueChange = { freeText = ThousandsVisualTransformation.sanitize(it) },
        label = { Text(if (isIncome) "Сумма на месяц, ₽" else "Свободная сумма, ₽") },
        placeholder = { Text(if (isIncome) "" else "без подкатегории") },
        singleLine = true,
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    if (isIncome) return

    Text(
        "Свободная сумма — на всё в категории без разбивки. Подкатегории добавляются к ней, план категории — их сумма.",
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
    val usedNames = rows.map { it.name.trim().lowercase() }.toSet()
    val freeSuggestions = suggestions.filter { it.lowercase() !in usedNames }.take(MAX_SUGGESTIONS)
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
    PlanLine("Свободная сумма", MoneyFormat.format(free ?: Money.zero(Currency.BASE), false))
    PlanLine("Подкатегории", MoneyFormat.format(itemsTotal, false))
    PlanLine("План категории", MoneyFormat.format((free ?: Money.zero(Currency.BASE)) + itemsTotal, false), bold = true)
}

@Composable
internal fun PlanLine(label: String, value: String, bold: Boolean = false, highlight: Color? = null) {
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
