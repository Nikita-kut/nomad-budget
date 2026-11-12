package ru.nomadbudget.presentation.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.CorrectionCategory
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.components.AddTile
import ru.nomadbudget.presentation.components.AppIcons
import ru.nomadbudget.presentation.components.ArchiveCard
import ru.nomadbudget.presentation.components.ArchivedItem
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.ConfirmDialog
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.PrimaryButton
import ru.nomadbudget.presentation.components.SecondaryButton
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.TextDialog
import ru.nomadbudget.presentation.components.TrashIconButton
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.format.operations
import ru.nomadbudget.presentation.format.plural
import ru.nomadbudget.presentation.home.HomeState

private sealed interface Dialog {
    data class RenameCategory(val category: Category) : Dialog
    data class ArchiveCategory(val category: Category) : Dialog
    data class AddCategory(val kind: CategoryKind) : Dialog
    data class AddSubcategory(val category: Category) : Dialog
    data class RenameSubcategory(val subcategory: Subcategory) : Dialog
    data class DeleteSubcategory(val subcategory: Subcategory) : Dialog
    data class MergeSubcategory(val subcategory: Subcategory) : Dialog
    data class MakeSystem(val category: Category) : Dialog
}

@Composable
fun CategoriesScreen(
    state: HomeState,
    onBack: () -> Unit,
    onAddCategory: (String, CategoryKind) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onArchiveCategory: (String) -> Unit,
    onRestoreCategory: (String) -> Unit = {},
    onAddSubcategory: (String, String, Money?) -> Unit,
    onRenameSubcategory: (String, String) -> Unit,
    onDeleteSubcategory: (String) -> Unit,
    onRestoreSubcategory: (String) -> Unit = {},
    onMergeSubcategory: (String, String) -> Unit = { _, _ -> },
    onSetSystem: (String, Boolean) -> Unit = { _, _ -> },
    onMoveCategory: (String, Boolean) -> Unit = { _, _ -> },
) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val usage = remember(state.transactions) {
        state.transactions.filterIsInstance<Transaction.Expense>()
            .filter { it.subcategoryId != null }
            .groupBy { it.subcategoryId }
            .mapValues { (_, list) -> list.size to list.maxOf { it.date } }
    }

    CenteredLazyColumn(bottom = 12.dp) {
        item { SubScreenHeader("Категории", onBack) }
        listOf(CategoryKind.EXPENSE to "Расходы", CategoryKind.INCOME to "Доходы").forEach { (kind, title) ->
            item { SectionTitle(title, hint = "порядок здесь = порядок в форме записи") }
            val list = state.activeCategories.filter { it.kind == kind }.sortedBy { it.sortOrder }
            items(list, key = Category::id) { category ->
                CategoryCard(
                    category = category,
                    subcategories = state.subcategories.filter { it.categoryId == category.id },
                    usage = usage,
                    canMoveUp = list.firstOrNull() != category,
                    canMoveDown = list.lastOrNull() != category,
                    onMove = { up -> onMoveCategory(category.id, up) },
                    onMakeSystem = { dialog = Dialog.MakeSystem(category) },
                    onRestoreSubcategory = { onRestoreSubcategory(it.id) },
                    onMergeSubcategory = { dialog = Dialog.MergeSubcategory(it) },
                    expanded = expandedId == category.id,
                    onToggle = { expandedId = if (expandedId == category.id) null else category.id },
                    onRename = { dialog = Dialog.RenameCategory(category) },
                    onArchive = { dialog = Dialog.ArchiveCategory(category) },
                    onAddSubcategory = { dialog = Dialog.AddSubcategory(category) },
                    onRenameSubcategory = { dialog = Dialog.RenameSubcategory(it) },
                    onDeleteSubcategory = { dialog = Dialog.DeleteSubcategory(it) },
                )
            }
            item {
                AddTile("Добавить категорию", onClick = { dialog = Dialog.AddCategory(kind) })
            }
        }
        val archived = state.categories.filter { it.isArchived && !it.isSystem }
        if (archived.isNotEmpty()) {
            item { SectionTitle("Архив", hint = "старые операции остаются с категорией") }
            item { ArchiveCard(archived.map { ArchivedItem(it.id, it.name, if (it.kind == CategoryKind.INCOME) "доход" else "расход") }, onRestore = onRestoreCategory) }
        }
        val system = state.categories.filter { it.isSystem && !it.isArchived }
        if (system.isNotEmpty()) {
            item { SectionTitle("Служебные", hint = "в бюджете не участвуют") }
            items(system, key = { "sys-" + it.id }) { category ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(category.name, fontWeight = FontWeight.Medium)
                            Text(if (category.kind == CategoryKind.EXPENSE) "расход · только остатки счетов" else "доход · только остатки счетов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (category.name != CorrectionCategory.NAME) {
                            TextButton(onClick = { onSetSystem(category.id, false) }) { Text("Вернуть в бюджет") }
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is Dialog.AddCategory -> TextDialog(
            title = if (d.kind == CategoryKind.EXPENSE) "Новая категория расходов" else "Новая категория доходов",
            initial = "",
            onDismiss = { dialog = null },
            onConfirm = { onAddCategory(it, d.kind); dialog = null },
        )
        is Dialog.RenameCategory -> TextDialog(
            title = "Переименовать категорию",
            initial = d.category.name,
            onDismiss = { dialog = null },
            onConfirm = { onRenameCategory(d.category.id, it); dialog = null },
        )
        is Dialog.ArchiveCategory -> ConfirmDialog(
            title = "Убрать «${d.category.name}» в архив?",
            text = "Категория исчезнет из списков, старые операции останутся с ней.",
            confirmText = "В архив",
            onDismiss = { dialog = null },
            onConfirm = { onArchiveCategory(d.category.id); dialog = null },
        )
        is Dialog.AddSubcategory -> AddSubcategoryDialog(
            category = d.category,
            periodTitle = DateFormat.periodRange(state.period),
            onDismiss = { dialog = null },
            onConfirm = { name, planned -> onAddSubcategory(d.category.id, name, planned); dialog = null },
        )
        is Dialog.RenameSubcategory -> TextDialog(
            title = "Переименовать подкатегорию",
            initial = d.subcategory.name,
            onDismiss = { dialog = null },
            onConfirm = { onRenameSubcategory(d.subcategory.id, it); dialog = null },
        )
        is Dialog.DeleteSubcategory -> ConfirmDialog(
            title = "Убрать «${d.subcategory.name}»?",
            text = usage[d.subcategory.id]?.let { (count, last) ->
                "Использована ${plural(count, "раз", "раза", "раз")}, последний — ${DateFormat.dayMonth(last)}. Подкатегория уйдёт в архив: пропадёт из формы и плана, история останется. Вернуть можно здесь же."
            } ?: "Подкатегория не использовалась и будет удалена.",
            confirmText = "Убрать",
            onDismiss = { dialog = null },
            onConfirm = { onDeleteSubcategory(d.subcategory.id); dialog = null },
        )
        is Dialog.MergeSubcategory -> MergeDialog(
            subcategory = d.subcategory,
            targets = state.activeSubcategories.filter { it.categoryId == d.subcategory.categoryId && it.id != d.subcategory.id },
            onDismiss = { dialog = null },
            onConfirm = { onMergeSubcategory(d.subcategory.id, it.id); dialog = null },
        )
        is Dialog.MakeSystem -> ConfirmDialog(
            title = "Сделать «${d.category.name}» служебной?",
            text = "Служебная категория не участвует в плане, конвертах и доходах месяца: она влияет только на остатки счетов. Подходит для начальных остатков и технических поправок.",
            confirmText = "Сделать служебной",
            onDismiss = { dialog = null },
            onConfirm = { onSetSystem(d.category.id, true); dialog = null },
        )
    }
}

@Composable
private fun MergeDialog(subcategory: Subcategory, targets: List<Subcategory>, onDismiss: () -> Unit, onConfirm: (Subcategory) -> Unit) {
    var target by remember { mutableStateOf(targets.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Объединить «${subcategory.name}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (targets.isEmpty()) {
                    Text("В этой категории нет другой подкатегории, с которой можно объединить.")
                } else {
                    Text("Все операции и строки плана перейдут в выбранную подкатегорию, «${subcategory.name}» удалится.")
                    Dropdown("Куда", targets, target, Subcategory::name, { target = it })
                }
            }
        },
        confirmButton = { PrimaryButton(onClick = { target?.let(onConfirm) }, enabled = target != null) { Text("Объединить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun CategoryCard(
    category: Category,
    subcategories: List<Subcategory>,
    usage: Map<String?, Pair<Int, LocalDate>>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Boolean) -> Unit,
    onMakeSystem: () -> Unit,
    onRestoreSubcategory: (Subcategory) -> Unit,
    onMergeSubcategory: (Subcategory) -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onAddSubcategory: () -> Unit,
    onRenameSubcategory: (Subcategory) -> Unit,
    onDeleteSubcategory: (Subcategory) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, fontWeight = FontWeight.Medium)
                val active = subcategories.count { !it.isArchived }
                Text(
                    if (active == 0) "без подкатегорий" else plural(active, "подкатегория", "подкатегории", "подкатегорий"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onMove(true) }, enabled = canMoveUp) { Icon(AppIcons.ChevronUp, contentDescription = "Выше") }
            IconButton(onClick = { onMove(false) }, enabled = canMoveDown) { Icon(AppIcons.ChevronDown, contentDescription = "Ниже") }
            IconButton(onClick = onRename) { Icon(AppIcons.Edit, contentDescription = "Переименовать") }
            TrashIconButton("В архив", onClick = onArchive)
        }
        if (expanded) {
            HorizontalDivider()
            Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
                subcategories.filterNot { it.isArchived }.forEach { sub ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.name, style = MaterialTheme.typography.bodyMedium)
                            usage[sub.id]?.let { (count, _) -> Text(operations(count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        IconButton(onClick = { onRenameSubcategory(sub) }) {
                            Icon(AppIcons.Edit, contentDescription = "Переименовать", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onMergeSubcategory(sub) }) {
                            Icon(AppIcons.Merge, contentDescription = "Объединить с другой", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteSubcategory(sub) }) {
                            Icon(AppIcons.Close, contentDescription = "Убрать", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = onAddSubcategory) { Text("Добавить подкатегорию") }
                val archived = subcategories.filter { it.isArchived }
                if (archived.isNotEmpty()) {
                    Text("В архиве", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    archived.forEach { sub ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(sub.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onRestoreSubcategory(sub) }) { Text("Вернуть") }
                        }
                    }
                }
                TextButton(onClick = onMakeSystem) { Text("Сделать категорию служебной") }
            }
        }
    }
}

@Composable
private fun AddSubcategoryDialog(category: Category, periodTitle: String, onDismiss: () -> Unit, onConfirm: (String, Money?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val transformation = remember { ThousandsVisualTransformation() }
    val planned = MoneyFormat.parse(amount, Currency.BASE)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подкатегория для «${category.name}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (category.kind == CategoryKind.EXPENSE) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = ThousandsVisualTransformation.sanitize(it, Currency.BASE.minorUnits) },
                        label = { Text("План на месяц, ${Currency.BASE.code}") },
                        supportingText = { Text("$periodTitle · необязательно") },
                        singleLine = true,
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(onClick = { onConfirm(name, planned) }, enabled = name.isNotBlank() && (amount.isBlank() || planned != null)) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
