package ru.nomadbudget.presentation.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.components.AccountDropdown
import ru.nomadbudget.presentation.components.CurrencyChip
import ru.nomadbudget.presentation.components.DateField
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.InfoHint
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.TagChip
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.EntryDraft
import ru.nomadbudget.presentation.home.EntryType
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.home.JournalDay
import ru.nomadbudget.presentation.theme.AppTheme
import ru.nomadbudget.domain.model.Draft
import ru.nomadbudget.presentation.home.EntryPrefill
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.presentation.components.AccountsLine
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import ru.nomadbudget.domain.model.sumIn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import ru.nomadbudget.presentation.entry.EditTransactionDialog

private enum class JournalFilter(val label: String) {
    ALL("Все"),
    EXPENSES("Расходы"),
    INCOMES("Доходы"),
    MOVES("Переводы и обмены"),
    ;

    fun matches(tx: Transaction): Boolean = when (this) {
        ALL -> true
        EXPENSES -> tx is Transaction.Expense
        INCOMES -> tx is Transaction.Income
        MOVES -> tx is Transaction.Transfer || tx is Transaction.Exchange
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(
    state: HomeState,
    onDelete: (String) -> Unit,
    onUpdate: (Transaction, String?) -> Unit,
    onRepeat: (Transaction) -> Unit = {},
) {
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(JournalFilter.ALL) }
    var account by remember { mutableStateOf<Account?>(null) }
    val searching = query.isNotBlank()
    val source = if (searching) state.transactions else state.inPeriod
    val filtered = source.filter { tx ->
        filter.matches(tx) && (account == null || tx.touches(account?.id)) && (!searching || tx.matches(query.trim(), state))
    }
    val days = filtered.groupBy { it.date }.entries.sortedByDescending { it.key }.map { (date, list) ->
        JournalDay(date, list, list.filterIsInstance<Transaction.Expense>().map { it.amountBase }.sumIn(Currency.BASE))
    }
    val spent = filtered.filterIsInstance<Transaction.Expense>().map { it.amountBase }.sumIn(Currency.BASE)
    val earned = filtered.filterIsInstance<Transaction.Income>().map { it.amountBase }.sumIn(Currency.BASE)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Поиск по всем месяцам") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (searching) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Очистить поиск") } }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                JournalFilter.entries.forEach { option ->
                    TagChip(text = option.label, selected = filter == option, onClick = { filter = option })
                }
            }
        }
        item {
            Dropdown(
                label = if (account == null) "Все счета" else "Счёт",
                items = listOf<Account?>(null) + state.activeAccounts,
                selected = account,
                itemLabel = { it?.name ?: "Все счета" },
                onSelect = { account = it },
            )
        }
        item {
            SectionTitle(
                if (searching) "Найдено во всех месяцах" else "Журнал",
                hint = listOfNotNull(
                    "${filtered.size} опер.",
                    spent.takeUnless { it.isZero }?.let { "расход ${MoneyFormat.format(it, false)}" },
                    earned.takeUnless { it.isZero }?.let { "доход ${MoneyFormat.format(it, false)}" },
                ).joinToString(" · "),
                info = Hints.JOURNAL,
            )
        }
        if (days.isEmpty()) {
            item { EmptyHint(if (searching) "Ничего не нашлось" else "Пока пусто") }
        }
        items(days, key = { it.date.toString() }) { day ->
            DayCard(day, state, onDelete, onEdit = { editing = it })
        }
    }

    editing?.let { tx ->
        EditTransactionDialog(
            tx = tx,
            state = state,
            onDismiss = { editing = null },
            onSave = { updated, subcategoryName ->
                onUpdate(updated, subcategoryName)
                editing = null
            },
            onDelete = {
                onDelete(tx.id)
                editing = null
            },
            onRepeat = {
                onRepeat(tx)
                editing = null
            },
        )
    }
}

private fun Transaction.touches(accountId: String?): Boolean = when (this) {
    is Transaction.Expense -> this.accountId == accountId
    is Transaction.Income -> this.accountId == accountId
    is Transaction.Transfer -> fromAccountId == accountId || toAccountId == accountId
    is Transaction.Exchange -> fromAccountId == accountId || toAccountId == accountId
}

private fun Transaction.matches(query: String, state: HomeState): Boolean {
    val needle = query.lowercase()
    val digits = needle.filter { it.isDigit() }
    val texts = buildList {
        add(note)
        when (val tx = this@matches) {
            is Transaction.Expense -> {
                add(state.categoryName(tx.categoryId))
                add(state.subcategoryName(tx.subcategoryId).orEmpty())
                add(state.accountName(tx.accountId))
            }
            is Transaction.Income -> {
                add(state.categoryName(tx.categoryId))
                add(state.accountName(tx.accountId))
            }
            is Transaction.Transfer -> {
                add(state.accountName(tx.fromAccountId))
                add(state.accountName(tx.toAccountId))
            }
            is Transaction.Exchange -> {
                add(state.accountName(tx.fromAccountId))
                add(state.accountName(tx.toAccountId))
            }
        }
    }
    if (texts.any { it.lowercase().contains(needle) }) return true
    if (digits.isEmpty()) return false
    val amountDigits = (amountBase.minor / Currency.BASE.minorFactor).toString()
    val ownDigits = when (this) {
        is Transaction.Expense -> amount.wholeUnits.toString()
        is Transaction.Income -> amount.wholeUnits.toString()
        is Transaction.Transfer -> amount.wholeUnits.toString()
        is Transaction.Exchange -> given.wholeUnits.toString()
    }
    return amountDigits.contains(digits) || ownDigits.contains(digits)
}

@Composable
private fun DayCard(day: JournalDay, state: HomeState, onDelete: (String) -> Unit, onEdit: (Transaction) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(DateFormat.dayMonth(day.date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${DateFormat.weekdayFull(day.date)} · ${day.transactions.size} опер.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (day.spentBase.isZero) "без расходов" else "расход ${MoneyFormat.format(day.spentBase, false)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider()
        val expenses = day.transactions.filterIsInstance<Transaction.Expense>()
        val incomes = day.transactions.filterIsInstance<Transaction.Income>()
        val moves = day.transactions.filter { it is Transaction.Transfer || it is Transaction.Exchange }
        val groups = listOf(
            JournalGroup("Расходы", AppTheme.colors.bad, expenses, "−" + MoneyFormat.format(expenses.map { it.amountBase }.sumIn(Currency.BASE), false)),
            JournalGroup("Доходы", AppTheme.colors.good, incomes, "+" + MoneyFormat.format(incomes.map { it.amountBase }.sumIn(Currency.BASE), false)),
            JournalGroup("Переводы и обмены", MaterialTheme.colorScheme.primary, moves, "${moves.size} опер."),
        ).filter { it.items.isNotEmpty() }
        groups.forEach { group ->
            GroupHeader(group)
            group.items.forEach { tx -> TransactionRow(tx, state, onDelete, onEdit) }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private data class JournalGroup(val title: String, val color: Color, val items: List<Transaction>, val total: String)

@Composable
private fun GroupHeader(group: JournalGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(group.color.copy(alpha = 0.12f))
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(group.color))
        Text(
            group.title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = group.color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 8.dp, top = 5.dp, bottom = 5.dp),
        )
        Text(
            group.total,
            style = MaterialTheme.typography.labelMedium,
            color = group.color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 10.dp),
        )
    }
}

@Composable
private fun TransactionRow(tx: Transaction, state: HomeState, onDelete: (String) -> Unit, onEdit: (Transaction) -> Unit) {
    val row = describe(tx, state)
    val primaryColor = when (tx) {
        is Transaction.Income -> AppTheme.colors.good
        is Transaction.Transfer -> MaterialTheme.colorScheme.onSurfaceVariant
        is Transaction.Expense, is Transaction.Exchange -> MaterialTheme.colorScheme.onSurface
    }
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete(tx.id) },
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { onEdit(tx) }
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.title, fontWeight = FontWeight.Medium, maxLines = 1)
                AccountsLine(state, fromId = row.fromAccountId, toId = row.toAccountId, note = row.note)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val currency = txCurrency(tx)
                    if (currency != Currency.BASE) CurrencyChip(currency, state)
                    Text(row.primary, fontWeight = FontWeight.SemiBold, color = primaryColor)
                }
                if (row.secondary.isNotEmpty()) {
                    Text(row.secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (tx.pending) {
                    Text("ждёт отправки", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.warning, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private data class RowText(
    val title: String,
    val fromAccountId: String,
    val toAccountId: String?,
    val note: String,
    val primary: String,
    val secondary: String,
)

private fun txCurrency(tx: Transaction): Currency = when (tx) {
    is Transaction.Expense -> tx.amount.currency
    is Transaction.Income -> tx.amount.currency
    is Transaction.Transfer -> tx.amount.currency
    is Transaction.Exchange -> tx.given.currency
}

private fun describe(tx: Transaction, state: HomeState): RowText = when (tx) {
    is Transaction.Expense -> RowText(
        title = listOfNotNull(state.categoryName(tx.categoryId), state.subcategoryName(tx.subcategoryId)).joinToString(" · "),
        fromAccountId = tx.accountId,
        toAccountId = null,
        note = tx.note,
        primary = "−" + MoneyFormat.format(tx.amount),
        secondary = if (tx.amount.currency == Currency.BASE) "" else "≈ ${MoneyFormat.format(tx.amountBase, false)}",
    )
    is Transaction.Income -> RowText(
        title = state.categoryName(tx.categoryId),
        fromAccountId = tx.accountId,
        toAccountId = null,
        note = tx.note,
        primary = "+" + MoneyFormat.format(tx.amount),
        secondary = "",
    )
    is Transaction.Transfer -> RowText(
        title = "Перевод",
        fromAccountId = tx.fromAccountId,
        toAccountId = tx.toAccountId,
        note = tx.note,
        primary = MoneyFormat.format(tx.amount),
        secondary = if (state.accountsById[tx.toAccountId]?.isSavings == true) "в накопления" else "",
    )
    is Transaction.Exchange -> RowText(
        title = "Обмен ${tx.given.currency.code} на ${tx.received.currency.code}",
        fromAccountId = tx.fromAccountId,
        toAccountId = tx.toAccountId,
        note = tx.note,
        primary = "−" + MoneyFormat.format(tx.given),
        secondary = "+" + MoneyFormat.format(tx.received),
    )
}

