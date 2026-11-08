package ru.nomadbudget.presentation.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.sumIn
import ru.nomadbudget.presentation.components.TagChip
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.CategoryPlanDraft
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.home.PlanItemInput
import ru.nomadbudget.presentation.theme.AppTheme
import ru.nomadbudget.presentation.components.AppIcons

private const val MAX_SUGGESTIONS = 8

private data class PlanRowInput(val key: Int, val subcategoryId: String?, val name: String, val amount: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanEditorDialog(
    state: HomeState,
    categoryId: String,
    onSave: (CategoryPlanDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val budget = state.budgets.firstOrNull { it.category.id == categoryId } ?: return
    val category = budget.category
    val isIncome = category.kind == CategoryKind.INCOME
    val history = state.categoryHistory[category.id]
    val initialRows = remember(categoryId) {
        budget.items
            .filter { it.planned != null && it.subcategory != null }
            .mapIndexed { index, item -> PlanRowInput(index, item.subcategory?.id, item.subcategory?.name.orEmpty(), majorText(item.planned ?: Money.zero(Currency.BASE))) }
    }
    val initialFree = remember(categoryId) { budget.freePlanned.takeIf { it.minor > 0L }?.let(::majorText).orEmpty() }
    var rows by remember(categoryId) { mutableStateOf(initialRows) }
    var nextKey by remember(categoryId) { mutableIntStateOf(initialRows.size) }
    var freeText by remember(categoryId) { mutableStateOf(initialFree) }
    val transformation = remember { ThousandsVisualTransformation() }

    val parsedRows = rows.map { row -> row to MoneyFormat.parse(row.amount, Currency.BASE) }
    val itemsTotal = parsedRows.mapNotNull { it.second }.sumIn(Currency.BASE)
    val free = if (freeText.isBlank()) Money.zero(Currency.BASE) else MoneyFormat.parse(freeText, Currency.BASE)
    val dirty = freeText != initialFree || rows != initialRows
    val error = when {
        free == null -> "Введи сумму в «Прочее»"
        parsedRows.any { (row, money) -> row.name.isBlank() || money == null } -> "У каждой строки нужны название и сумма"
        rows.map { it.name.trim().lowercase() }.let { it.size != it.toSet().size } -> "Подкатегории повторяются"
        else -> null
    }
    val total = (free ?: Money.zero(Currency.BASE)) + itemsTotal

    fun draft(): CategoryPlanDraft? {
        if (error != null || free == null) return null
        val keptIds = rows.mapNotNull { it.subcategoryId }.toSet()
        return CategoryPlanDraft(
            categoryId = category.id,
            free = free.takeIf { it.minor > 0L },
            items = parsedRows.mapNotNull { (row, money) -> money?.let { PlanItemInput(row.name.trim(), it, row.subcategoryId) } },
            removedSubcategoryIds = initialRows.mapNotNull { it.subcategoryId }.filterNot { it in keptIds },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(AppIcons.Close, contentDescription = "Закрыть без сохранения") }
                    Text(category.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { draft()?.let(onSave) }, enabled = dirty && error == null && !state.saving) { Text("Сохранить") }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).widthIn(max = 640.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("План на месяц", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(MoneyFormat.format(total, false), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    history?.let { h ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "обычно ~${MoneyFormat.format(h.average.withoutFraction(), false)} · в прошлом месяце ${MoneyFormat.format(h.previous.withoutFraction(), false)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                val rest = h.average - itemsTotal
                                freeText = if (rest.isNegative || rest.isZero) "" else rest.wholeUnits.toString()
                            }) { Text("Как обычно") }
                        }
                    }
                    HorizontalDivider()
                    if (!isIncome) {
                        Text("Подкатегории", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        rows.forEach { row ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = row.name,
                                    onValueChange = { v -> rows = rows.map { if (it.key == row.key) it.copy(name = v) else it } },
                                    label = { Text("На что") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f),
                                )
                                OutlinedTextField(
                                    value = row.amount,
                                    onValueChange = { v -> rows = rows.map { if (it.key == row.key) it.copy(amount = ThousandsVisualTransformation.sanitize(v, Currency.BASE.minorUnits)) else it } },
                                    label = { Text(Currency.BASE.code) },
                                    singleLine = true,
                                    visualTransformation = transformation,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { rows = rows.filterNot { it.key == row.key } }) {
                                    Icon(AppIcons.Close, contentDescription = "Убрать строку", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        val used = rows.map { it.name.trim().lowercase() }.toSet()
                        val suggestions = state.activeSubcategories
                            .filter { it.categoryId == category.id && it.name.lowercase() !in used }
                            .map { it.name }
                            .take(MAX_SUGGESTIONS)
                        if (suggestions.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                suggestions.forEach { name ->
                                    TagChip(text = "+ $name", onClick = {
                                        val existing = state.activeSubcategories.firstOrNull { it.categoryId == category.id && it.name == name }
                                        rows = rows + PlanRowInput(nextKey, existing?.id, name, "")
                                        nextKey++
                                    })
                                }
                            }
                        }
                        TextButton(onClick = {
                            rows = rows + PlanRowInput(nextKey, null, "", "")
                            nextKey++
                        }) { Text("Новая подкатегория") }
                        HorizontalDivider()
                    }
                    OutlinedTextField(
                        value = freeText,
                        onValueChange = { freeText = ThousandsVisualTransformation.sanitize(it, Currency.BASE.minorUnits) },
                        label = { Text(if (isIncome) "Сумма на месяц, ${Currency.BASE.code}" else "Прочее, ${Currency.BASE.code}") },
                        supportingText = { if (!isIncome) Text("на всё в категории, что не попало в подкатегории") },
                        singleLine = true,
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.takeIf { dirty }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.bad) }
                }
            }
        }
    }
}

@Composable
fun FundSettingsDialog(account: Account, onDismiss: () -> Unit, onConfirm: (Money?, Money?, Int?) -> Unit) {
    val currency = account.currency
    var monthly by remember { mutableStateOf(account.fundMonthly?.let(::majorText).orEmpty()) }
    var goal by remember { mutableStateOf(account.fundGoal?.let(::majorText).orEmpty()) }
    var day by remember { mutableStateOf(account.fundDay?.toString().orEmpty()) }
    val transformation = remember { ThousandsVisualTransformation() }
    val monthlyMoney = if (monthly.isBlank()) null else MoneyFormat.parse(monthly, currency)
    val goalMoney = if (goal.isBlank()) null else MoneyFormat.parse(goal, currency)
    val dayValue = day.toIntOrNull()
    val valid = (monthly.isBlank() || monthlyMoney != null) && (goal.isBlank() || goalMoney != null) && (day.isBlank() || dayValue in 1..31)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Копилка «${account.name}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = monthly,
                    onValueChange = { monthly = ThousandsVisualTransformation.sanitize(it, currency.minorUnits) },
                    label = { Text("Взнос в месяц, ${currency.code}") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = ThousandsVisualTransformation.sanitize(it, currency.minorUnits) },
                    label = { Text("Ближайшая оплата, ${currency.code}") },
                    placeholder = { Text("необязательно") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("День оплаты") },
                    placeholder = { Text("необязательно") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Взнос входит в обязательные платежи месяца. Переводи его на счёт копилки, а трату плати прямо с этого счёта: так она не попадёт в конверты.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(monthlyMoney, goalMoney, dayValue) }, enabled = valid) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
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
