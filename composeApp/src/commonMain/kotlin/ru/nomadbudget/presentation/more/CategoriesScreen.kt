package ru.nomadbudget.presentation.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.HomeState

private sealed interface Dialog {
    data class RenameCategory(val category: Category) : Dialog
    data class ArchiveCategory(val category: Category) : Dialog
    data class AddCategory(val kind: CategoryKind) : Dialog
    data class AddSubcategory(val category: Category) : Dialog
    data class RenameSubcategory(val subcategory: Subcategory) : Dialog
    data class DeleteSubcategory(val subcategory: Subcategory) : Dialog
}

@Composable
fun CategoriesScreen(
    state: HomeState,
    onBack: () -> Unit,
    onAddCategory: (String, CategoryKind) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onArchiveCategory: (String) -> Unit,
    onAddSubcategory: (String, String, Money?) -> Unit,
    onRenameSubcategory: (String, String) -> Unit,
    onDeleteSubcategory: (String) -> Unit,
) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SubScreenHeader("Категории", onBack) }
        listOf(CategoryKind.EXPENSE to "Расходы", CategoryKind.INCOME to "Доходы").forEach { (kind, title) ->
            item { SectionTitle(title) }
            items(state.activeCategories.filter { it.kind == kind }, key = Category::id) { category ->
                CategoryCard(
                    category = category,
                    subcategories = state.subcategories.filter { it.categoryId == category.id },
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
                OutlinedButton(onClick = { dialog = Dialog.AddCategory(kind) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Добавить категорию")
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
            periodTitle = state.period.title(),
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
            title = "Удалить «${d.subcategory.name}»?",
            text = "Удаление возможно, только если подкатегория не используется в операциях.",
            confirmText = "Удалить",
            onDismiss = { dialog = null },
            onConfirm = { onDeleteSubcategory(d.subcategory.id); dialog = null },
        )
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    subcategories: List<Subcategory>,
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
                Text(
                    if (subcategories.isEmpty()) "без подкатегорий" else "${subcategories.size} подкат.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, contentDescription = "Переименовать") }
            IconButton(onClick = onArchive) { Icon(Icons.Filled.Delete, contentDescription = "В архив") }
        }
        if (expanded) {
            HorizontalDivider()
            Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
                subcategories.forEach { sub ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(sub.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { onRenameSubcategory(sub) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Переименовать", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteSubcategory(sub) }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = onAddSubcategory) { Text("Добавить подкатегорию") }
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
                        onValueChange = { amount = ThousandsVisualTransformation.sanitize(it) },
                        label = { Text("План на $periodTitle, ₽ (необязательно)") },
                        singleLine = true,
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, planned) }, enabled = name.isNotBlank() && (amount.isBlank() || planned != null)) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmText: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
