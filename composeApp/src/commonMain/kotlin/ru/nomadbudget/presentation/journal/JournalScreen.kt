package ru.nomadbudget.presentation.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.JournalCalculator
import ru.nomadbudget.domain.logic.JournalDay
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.TransactionKind
import ru.nomadbudget.presentation.components.AccountsLine
import ru.nomadbudget.presentation.components.AmountCell
import ru.nomadbudget.presentation.components.AppIcons
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.CurrencyChip
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.HeaderCell
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.TagChip
import ru.nomadbudget.presentation.components.Ui
import ru.nomadbudget.presentation.entry.EditTransactionDialog
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.operations
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

private enum class JournalFilter(val label: String, private val kinds: Set<TransactionKind>) {
    ALL("Все", TransactionKind.entries.toSet()),
    EXPENSES("Расходы", setOf(TransactionKind.EXPENSE)),
    INCOMES("Доходы", setOf(TransactionKind.INCOME)),
    MOVES("Переводы и обмены", setOf(TransactionKind.TRANSFER, TransactionKind.EXCHANGE)),
    ;

    fun matches(tx: Transaction): Boolean = tx.kind in kinds
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(
    state: HomeState,
    onDelete: (String) -> Unit,
    onUpdate: (Transaction, String?) -> Unit,
    onRepeat: (Transaction) -> Unit = {},
    twoColumns: Boolean = false,
) {
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(JournalFilter.ALL) }
    var account by remember { mutableStateOf<Account?>(null) }
    val searching = query.isNotBlank()
    val source = if (searching) state.transactions else state.inPeriod
    val filtered = source.filter { tx ->
        filter.matches(tx) && (account == null || tx.touches(account?.id.orEmpty())) && (!searching || JournalCalculator.matchesQuery(tx, query, labels(tx, state)))
    }
    val summary = JournalCalculator.summary(filtered)

    if (twoColumns) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.width(FILTERS_WIDTH), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(query, onChange = { query = it }, clearable = false)
                Text("Тип", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalFilter.entries.forEach { option ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { filter = option }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = filter == option, onClick = { filter = option })
                        Text(option.label)
                    }
                }
                AccountFilter(state, account, label = "Счёт", onSelect = { account = it })
                HorizontalDivider()
                Text(operations(summary.count), style = MaterialTheme.typography.bodyMedium)
                if (!summary.spent.isZero) Text("расход ${MoneyFormat.format(summary.spent, false)}", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.bad)
                if (!summary.earned.isZero) Text("доход ${MoneyFormat.format(summary.earned, false)}", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.good)
            }
            Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { JournalHeaderRow() }
                    if (summary.days.isEmpty()) item { EmptyHint(if (searching) "Ничего не нашлось" else "Пока пусто") }
                    summary.days.forEach { day ->
                        item(key = "d" + day.date.toString()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Ui.CardPadding, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${DateFormat.dayMonth(day.date)} · ${DateFormat.weekdayFull(day.date)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(daySpent(day), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        items(day.transactions, key = { it.id }) { tx -> JournalTableRow(tx, state, onClick = { editing = tx }) }
                    }
                }
            }
        }
    } else {
        CenteredLazyColumn {
            item { SearchField(query, onChange = { query = it }, clearable = true) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    JournalFilter.entries.forEach { option ->
                        TagChip(text = option.label, selected = filter == option, onClick = { filter = option })
                    }
                }
            }
            item { AccountFilter(state, account, label = if (account == null) "Все счета" else "Счёт", onSelect = { account = it }) }
            item {
                SectionTitle(
                    if (searching) "Найдено во всех месяцах" else "Журнал",
                    hint = listOfNotNull(
                        operations(summary.count),
                        summary.spent.takeUnless { it.isZero }?.let { "расход ${MoneyFormat.format(it, false)}" },
                        summary.earned.takeUnless { it.isZero }?.let { "доход ${MoneyFormat.format(it, false)}" },
                    ).joinToString(" · "),
                    info = Hints.JOURNAL,
                )
            }
            if (summary.days.isEmpty()) {
                item { EmptyHint(if (searching) "Ничего не нашлось" else "Пока пусто") }
            }
            items(summary.days, key = { it.date.toString() }) { day ->
                DayCard(day, state, onDelete, onEdit = { editing = it })
            }
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

private fun labels(tx: Transaction, state: HomeState): List<String> = buildList {
    tx.categoryIdOrNull?.let { add(state.categoryName(it)) }
    (tx as? Transaction.Expense)?.let { add(state.subcategoryName(it.subcategoryId).orEmpty()) }
    tx.accountIds.forEach { add(state.accountName(it)) }
}

private fun daySpent(day: JournalDay): String = if (day.spentBase.isZero) "без расходов" else "расход ${MoneyFormat.format(day.spentBase, false)}"

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, clearable: Boolean) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Поиск по всем месяцам") },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        trailingIcon = if (clearable && query.isNotBlank()) ({ IconButton(onClick = { onChange("") }) { Icon(AppIcons.Close, contentDescription = "Очистить поиск") } }) else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccountFilter(state: HomeState, account: Account?, label: String, onSelect: (Account?) -> Unit) {
    Dropdown(
        label = label,
        items = listOf<Account?>(null) + state.activeAccounts,
        selected = account,
        itemLabel = { it?.name ?: "Все счета" },
        onSelect = onSelect,
    )
}

@Composable
private fun DayCard(day: JournalDay, state: HomeState, onDelete: (String) -> Unit, onEdit: (Transaction) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ui.CardPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(DateFormat.dayMonth(day.date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${DateFormat.weekdayFull(day.date)} · ${operations(day.transactions.size)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(daySpent(day), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()
        val byKind = day.transactions.groupBy { it.kind }
        val expenses = byKind[TransactionKind.EXPENSE].orEmpty()
        val incomes = byKind[TransactionKind.INCOME].orEmpty()
        val moves = byKind[TransactionKind.TRANSFER].orEmpty() + byKind[TransactionKind.EXCHANGE].orEmpty()
        val groups = listOf(
            JournalGroup("Расходы", AppTheme.colors.bad, expenses, MoneyFormat.format(-JournalCalculator.summary(expenses).spent, false)),
            JournalGroup("Доходы", AppTheme.colors.good, incomes, MoneyFormat.formatSigned(JournalCalculator.summary(incomes).earned, false)),
            JournalGroup("Переводы и обмены", MaterialTheme.colorScheme.primary, moves, operations(moves.size)),
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
            .background(group.color.copy(alpha = GROUP_TINT_ALPHA))
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(STRIPE_WIDTH).fillMaxHeight().background(group.color))
        Text(
            group.title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = group.color,
            fontWeight = FontWeight.SemiBold,
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
    val typeColor = typeColor(tx)
    val primaryColor = if (tx is Transaction.Expense) MaterialTheme.colorScheme.onSurface else typeColor
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
                Icon(AppIcons.Trash, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.surface)
                .background(rowTint(tx, typeColor))
                .clickable { onEdit(tx) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(STRIPE_WIDTH).fillMaxHeight().background(typeColor))
            Row(modifier = Modifier.weight(1f).padding(start = 10.dp, end = Ui.CardPadding, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.title, fontWeight = FontWeight.Medium, maxLines = 1)
                    AccountsLine(state, fromId = row.fromAccountId, toId = row.toAccountId, note = row.note)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val currency = tx.ownAmount.currency
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
    HorizontalDivider()
}

private fun typeLabel(tx: Transaction): String = when (tx.kind) {
    TransactionKind.EXPENSE -> "Расход"
    TransactionKind.INCOME -> "Доход"
    TransactionKind.TRANSFER -> "Перевод"
    TransactionKind.EXCHANGE -> "Обмен"
}

@Composable
private fun typeColor(tx: Transaction): Color {
    val colors = AppTheme.colors
    return when (tx.kind) {
        TransactionKind.EXPENSE -> colors.bad
        TransactionKind.INCOME -> colors.good
        TransactionKind.TRANSFER -> colors.save
        TransactionKind.EXCHANGE -> colors.warning
    }
}

private fun rowTint(tx: Transaction, typeColor: Color): Color = if (tx is Transaction.Expense) Color.Transparent else typeColor.copy(alpha = ROW_TINT_ALPHA)

private data class RowText(
    val title: String,
    val fromAccountId: String,
    val toAccountId: String?,
    val note: String,
    val primary: String,
    val secondary: String,
)

private fun describe(tx: Transaction, state: HomeState): RowText = when (tx) {
    is Transaction.Expense -> RowText(
        title = listOfNotNull(state.categoryName(tx.categoryId), state.subcategoryName(tx.subcategoryId)).joinToString(" · "),
        fromAccountId = tx.accountId,
        toAccountId = null,
        note = tx.note,
        primary = MoneyFormat.format(-tx.amount),
        secondary = if (tx.amount.currency == Currency.BASE) "" else "≈ ${MoneyFormat.format(tx.amountBase, false)}",
    )
    is Transaction.Income -> RowText(
        title = state.categoryName(tx.categoryId),
        fromAccountId = tx.accountId,
        toAccountId = null,
        note = tx.note,
        primary = MoneyFormat.formatSigned(tx.amount),
        secondary = "",
    )
    is Transaction.Transfer -> RowText(
        title = "Перевод",
        fromAccountId = tx.fromAccountId,
        toAccountId = tx.toAccountId,
        note = tx.note,
        primary = MoneyFormat.format(tx.amount),
        secondary = when {
            state.accountsById[tx.toAccountId]?.isSavings == true -> "в подушку"
            state.accountsById[tx.toAccountId]?.isFund == true -> "в копилку"
            state.accountsById[tx.fromAccountId]?.isSavings == true -> "из подушки"
            state.accountsById[tx.fromAccountId]?.isFund == true -> "из копилки"
            else -> ""
        },
    )
    is Transaction.Exchange -> RowText(
        title = "Обмен ${tx.given.currency.code} на ${tx.received.currency.code}",
        fromAccountId = tx.fromAccountId,
        toAccountId = tx.toAccountId,
        note = tx.note,
        primary = MoneyFormat.format(-tx.given),
        secondary = MoneyFormat.formatSigned(tx.received),
    )
}

@Composable
private fun JournalHeaderRow() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Ui.CardPadding, vertical = 8.dp)) {
        Text("Тип", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.width(TYPE_WIDTH))
        Text("Что", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(WHAT_WEIGHT))
        Text("Счёт", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(ACCOUNT_WEIGHT))
        Text("Заметка", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(NOTE_WEIGHT))
        HeaderCell("Сумма")
        HeaderCell("В рублях")
    }
    HorizontalDivider()
}

@Composable
private fun JournalTableRow(tx: Transaction, state: HomeState, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val what = when (tx) {
        is Transaction.Expense -> listOfNotNull(state.categoryName(tx.categoryId), state.subcategoryName(tx.subcategoryId)).joinToString(" › ")
        is Transaction.Income -> "Доход · ${state.categoryName(tx.categoryId)}"
        is Transaction.Transfer -> "Перевод"
        is Transaction.Exchange -> "Обмен"
    }
    val account = when (tx) {
        is Transaction.Expense -> state.accountName(tx.accountId)
        is Transaction.Income -> state.accountName(tx.accountId)
        is Transaction.Transfer -> "${state.accountName(tx.fromAccountId)} → ${state.accountName(tx.toAccountId)}"
        is Transaction.Exchange -> "${state.accountName(tx.fromAccountId)} → ${state.accountName(tx.toAccountId)}"
    }
    val amount = when (tx) {
        is Transaction.Exchange -> "${MoneyFormat.format(tx.given)} → ${MoneyFormat.format(tx.received)}"
        else -> MoneyFormat.format(tx.ownAmount)
    }
    val signedBase = when (tx.kind) {
        TransactionKind.EXPENSE -> MoneyFormat.format(-tx.amountBase, false)
        TransactionKind.INCOME -> MoneyFormat.formatSigned(tx.amountBase, false)
        TransactionKind.TRANSFER, TransactionKind.EXCHANGE -> MoneyFormat.format(tx.amountBase, false)
    }
    val typeColor = typeColor(tx)
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).background(rowTint(tx, typeColor)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(STRIPE_WIDTH).fillMaxHeight().background(typeColor))
        Row(modifier = Modifier.weight(1f).padding(start = 10.dp, end = Ui.CardPadding, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(typeLabel(tx), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = typeColor, modifier = Modifier.width(TYPE_WIDTH))
            Text(what, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(WHAT_WEIGHT))
            Text(account, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(ACCOUNT_WEIGHT))
            Text(tx.note, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(NOTE_WEIGHT))
            AmountCell(amount, style = MaterialTheme.typography.bodyMedium)
            AmountCell(
                signedBase,
                style = MaterialTheme.typography.bodyMedium,
                color = when (tx.kind) {
                    TransactionKind.EXPENSE -> colors.bad
                    TransactionKind.INCOME -> colors.good
                    TransactionKind.TRANSFER, TransactionKind.EXCHANGE -> typeColor
                },
            )
        }
    }
    HorizontalDivider()
}

private val FILTERS_WIDTH = 260.dp
private val TYPE_WIDTH = 64.dp
private val STRIPE_WIDTH = 4.dp
private const val WHAT_WEIGHT = 1.4f
private const val ACCOUNT_WEIGHT = 1f
private const val NOTE_WEIGHT = 1.2f
private const val GROUP_TINT_ALPHA = 0.12f
private const val ROW_TINT_ALPHA = 0.06f
