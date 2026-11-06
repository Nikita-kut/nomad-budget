package ru.nomadbudget.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.Debt

data class CategoryChoice(
    val category: Category,
    val subcategory: String? = null,
    val debt: Debt? = null,
    val isNew: Boolean = false,
) {
    val label: String
        get() = when {
            isNew -> "+ подкатегория «$subcategory» в «${category.name}»"
            subcategory != null -> "${category.name} › $subcategory"
            else -> category.name
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(
    label: String,
    choices: List<CategoryChoice>,
    selected: CategoryChoice?,
    onSelect: (CategoryChoice) -> Unit,
    allowCreate: Boolean,
    onEnterWhenClosed: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supporting: String? = null,
    fieldModifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var highlighted by remember { mutableIntStateOf(0) }

    val normalized = query.trim().lowercase().replace("/", "›")
    val tokens = normalized.split(" ", "›").filter { it.isNotBlank() }
    val filtered = if (tokens.isEmpty()) choices else choices.filter { choice ->
        val key = (choice.category.name + " " + choice.subcategory.orEmpty()).lowercase()
        tokens.all { key.contains(it) }
    }
    val createOptions = if (!allowCreate || normalized.isEmpty() || (filtered.isNotEmpty() && "›" !in normalized)) emptyList() else {
        val (catPart, subPart) = if ("›" in normalized) normalized.substringBefore("›").trim() to query.substringAfter(if ("›" in query) "›" else "/").trim() else "" to query.trim()
        val exists = choices.any { it.subcategory?.equals(subPart, ignoreCase = true) == true && (catPart.isEmpty() || it.category.name.lowercase().startsWith(catPart)) }
        if (subPart.isBlank() || exists) emptyList() else {
            choices.map { it.category }.distinct()
                .filter { catPart.isEmpty() || it.name.lowercase().startsWith(catPart) }
                .sortedByDescending { it.id == selected?.category?.id }
                .take(if (catPart.isEmpty() && selected != null) 1 else MAX_CREATE)
                .map { CategoryChoice(it, subPart, isNew = true) }
        }
    }
    val options = filtered + createOptions
    if (highlighted > options.lastIndex) highlighted = 0

    fun choose(choice: CategoryChoice) {
        onSelect(choice)
        editing = false
        expanded = false
        query = ""
    }

    ExposedDropdownMenuBox(expanded = expanded && options.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = if (editing) query else selected?.label.orEmpty(),
            onValueChange = {
                editing = true
                query = it
                expanded = true
                highlighted = 0
            },
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text("начни вводить: кафе, такси…") },
            isError = isError,
            supportingText = supporting?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = fieldModifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .onFocusChanged { focus ->
                    if (focus.isFocused && !editing) {
                        editing = true
                        query = ""
                        highlighted = choices.indexOf(selected).coerceAtLeast(0)
                    }
                    if (!focus.isFocused) {
                        editing = false
                        expanded = false
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            expanded = true
                            if (options.isNotEmpty()) highlighted = (highlighted + 1).coerceAtMost(options.lastIndex)
                            true
                        }
                        Key.DirectionUp -> {
                            if (options.isNotEmpty()) highlighted = (highlighted - 1).coerceAtLeast(0)
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            if (expanded && options.isNotEmpty()) {
                                choose(options[highlighted])
                            } else {
                                onEnterWhenClosed()
                            }
                            true
                        }
                        Key.Tab -> {
                            if (expanded && editing && query.isNotBlank() && options.isNotEmpty()) choose(options[highlighted])
                            false
                        }
                        Key.Escape -> {
                            if (expanded) {
                                expanded = false
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                },
        )
        ExposedDropdownMenu(
            expanded = expanded && options.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            options.forEachIndexed { index, choice ->
                if (choice.isNew && (index == 0 || !options[index - 1].isNew)) HorizontalDivider()
                val indent = choice.subcategory != null && !choice.isNew && normalized.isEmpty()
                DropdownMenuItem(
                    text = {
                        Text(
                            if (indent) "    ${choice.subcategory}" else choice.label,
                            fontWeight = if (choice.subcategory == null && !choice.isNew) FontWeight.SemiBold else null,
                            color = if (choice.isNew) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    },
                    onClick = { choose(choice) },
                    modifier = if (index == highlighted) Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer) else Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val MAX_CREATE = 4
