package ru.nomadbudget.presentation.entry

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

private const val MAX_SUGGESTIONS = 8

@Composable
fun EntryScreen(
    state: HomeState,
    onSubmit: (EntryDraft) -> Unit,
    onDelete: (String) -> Unit,
    onUpdate: (Transaction, String?) -> Unit,
    onAddDraft: (String) -> Unit,
    onUseDraft: (Draft) -> Unit,
    onRemoveDraft: (String) -> Unit,
    onClearPrefill: () -> Unit,
) {
    var editing by remember { mutableStateOf<Transaction?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { EntryForm(state, onSubmit, onClearPrefill) }
        item { SectionTitle("Входящие", hint = if (state.drafts.isEmpty()) "заметок нет" else "${state.drafts.size} заметок", info = Hints.INBOX) }
        item { InboxCard(state.drafts, onAddDraft, onUseDraft, onRemoveDraft) }
        item { SectionTitle("Журнал", hint = "${state.inPeriod.size} операций за месяц", info = Hints.JOURNAL) }
        if (state.journal.isEmpty()) {
            item { EmptyHint("Пока пусто") }
        }
        items(state.journal, key = { it.date.toString() }) { day ->
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
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryForm(state: HomeState, onSubmit: (EntryDraft) -> Unit, onClearPrefill: () -> Unit) {
    var type by remember { mutableStateOf(EntryType.EXPENSE) }
    var date by remember { mutableStateOf(state.today) }
    var account by remember(state.activeAccounts) {
        mutableStateOf(state.activeAccounts.firstOrNull { it.kind == AccountKind.CASH && it.currency != Currency.BASE } ?: state.activeAccounts.firstOrNull())
    }
    var toAccount by remember { mutableStateOf<Account?>(null) }
    var category by remember { mutableStateOf<Category?>(null) }
    var amountText by remember { mutableStateOf("") }
    var subcategory by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var debt by remember { mutableStateOf<Debt?>(null) }
    val amountTransformation = remember { ThousandsVisualTransformation() }
    val prefill = state.entryPrefill

    LaunchedEffect(prefill) {
        if (prefill != null) {
            type = EntryType.EXPENSE
            amountText = prefill.amountText.orEmpty()
            note = prefill.note
        }
    }

    val kind = if (type == EntryType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.activeCategories.filter { it.kind == kind }
    val selectedCategory = category?.takeIf { it.kind == kind } ?: categories.firstOrNull()
    val currency = account?.currency
    val amount = currency?.let { MoneyFormat.parse(amountText, it) }
    val transferTargets = state.activeAccounts.filter { it.currency == currency && it.id != account?.id }
    val selectedTo = toAccount?.takeIf { it in transferTargets } ?: transferTargets.firstOrNull()
    val suggestions = selectedCategory?.let { cat ->
        state.subcategories
            .filter { it.categoryId == cat.id && (subcategory.isBlank() || it.name.contains(subcategory, ignoreCase = true)) }
            .map { it.name }
            .distinct()
            .take(MAX_SUGGESTIONS)
    }.orEmpty()

    val debtCategory = type == EntryType.EXPENSE && state.isDebtCategory(selectedCategory?.id)

    LaunchedEffect(subcategory, debtCategory) {
        if (debtCategory && debt == null) state.debtBySubcategoryName(subcategory)?.let { debt = it }
        if (!debtCategory) debt = null
    }

    val canSubmit = !state.saving && account != null && amount != null &&
        (type == EntryType.TRANSFER && selectedTo != null || type != EntryType.TRANSFER && selectedCategory != null)

    fun submit() {
        val acc = account ?: return
        val money = amount ?: return
        onSubmit(
            EntryDraft(
                type = type,
                date = date,
                accountId = acc.id,
                amount = money,
                toAccountId = selectedTo?.id.takeIf { type == EntryType.TRANSFER },
                categoryId = selectedCategory?.id.takeIf { type != EntryType.TRANSFER },
                subcategoryName = if (type == EntryType.EXPENSE) subcategory else "",
                note = note,
                debtId = debt?.id.takeIf { debtCategory },
                fromDraftId = prefill?.draftId,
            ),
        )
        amountText = ""
        subcategory = ""
        note = ""
        debt = null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (prefill != null) {
                PrefillBanner(prefill, onClearPrefill)
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EntryType.entries.forEachIndexed { index, entryType ->
                    SegmentedButton(
                        selected = type == entryType,
                        onClick = { type = entryType },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = EntryType.entries.size),
                    ) {
                        Text(
                            when (entryType) {
                                EntryType.EXPENSE -> "Расход"
                                EntryType.INCOME -> "Доход"
                                EntryType.TRANSFER -> "Перевод"
                            },
                        )
                    }
                }
            }

            DateField(date = date, today = state.today, onChange = { date = it })

            AccountDropdown(
                label = when (type) {
                    EntryType.EXPENSE -> "Со счёта"
                    EntryType.INCOME -> "На счёт"
                    EntryType.TRANSFER -> "Откуда"
                },
                accounts = state.activeAccounts,
                selected = account,
                state = state,
                onSelect = { account = it },
            )

            if (type == EntryType.TRANSFER) {
                AccountDropdown(
                    label = "Куда",
                    accounts = transferTargets,
                    selected = selectedTo,
                    state = state,
                    onSelect = { toAccount = it },
                )
                if (transferTargets.isEmpty()) {
                    Text("Нет второго счёта в этой валюте. Другая валюта — это обмен.", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.warning)
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = ThousandsVisualTransformation.sanitize(it) },
                label = { Text("Сумма${currency?.let { ", ${it.code}" }.orEmpty()}") },
                singleLine = true,
                visualTransformation = amountTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            AmountHint(state, amount)

            if (type != EntryType.TRANSFER) {
                Dropdown(
                    label = "Категория",
                    items = categories,
                    selected = selectedCategory,
                    itemLabel = Category::name,
                    onSelect = { category = it },
                )
            }
            if (debtCategory) {
                Dropdown(
                    label = "Кредит, если это платёж по нему",
                    items = listOf<Debt?>(null) + state.openDebts,
                    selected = debt,
                    itemLabel = { it?.let { d -> "${d.name} · ${MoneyFormat.format(d.monthlyPayment, false)}" } ?: "не платёж по кредиту" },
                    onSelect = { selected ->
                        debt = selected
                        selected?.let { d ->
                            subcategory = d.name
                            if (amountText.isBlank() && account?.currency == d.currency) {
                                amountText = (d.monthlyPayment.minor / d.currency.minorFactor).toString()
                            }
                        }
                    },
                )
            }
            if (type == EntryType.EXPENSE) {
                OutlinedTextField(
                    value = subcategory,
                    onValueChange = { subcategory = it },
                    label = { Text("Подкатегория") },
                    placeholder = { Text("начни печатать или выбери") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { name ->
                            TagChip(text = name, selected = name.equals(subcategory, ignoreCase = true), onClick = { subcategory = name })
                        }
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Заметка") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = ::submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        state.saving -> "Сохраняем…"
                        type == EntryType.EXPENSE -> "Записать расход"
                        type == EntryType.INCOME -> "Записать доход"
                        else -> "Записать перевод"
                    },
                )
            }
        }
    }
}


@Composable
private fun AmountHint(state: HomeState, amount: ru.nomadbudget.domain.model.Money?) {
    val text = when {
        amount == null -> "В рублях по курсу на сегодня: —"
        amount.currency == Currency.BASE -> "Счёт в рублях, курс не нужен"
        !state.rates.hasRate(amount.currency) -> "Нет курса для ${amount.currency.code}, записать нельзя"
        else -> "≈ ${MoneyFormat.format(state.rates.toBase(amount))} по курсу ${MoneyFormat.formatRate(state.rates.basePerUnit(amount.currency))} ₽ за 1 ${amount.currency.code}"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        InfoHint("Пересчёт в рубли", Hints.AMOUNT_BASE)
    }
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
        val groups = listOf(
            "Расходы" to day.transactions.filterIsInstance<Transaction.Expense>(),
            "Доходы" to day.transactions.filterIsInstance<Transaction.Income>(),
            "Переводы и обмены" to day.transactions.filter { it is Transaction.Transfer || it is Transaction.Exchange },
        ).filter { it.second.isNotEmpty() }
        groups.forEachIndexed { index, (title, list) ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
            )
            list.forEach { tx -> TransactionRow(tx, state, onDelete, onEdit) }
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction, state: HomeState, onDelete: (String) -> Unit, onEdit: (Transaction) -> Unit) {
    val (title, subtitle, primary, secondary) = describe(tx, state)
    val primaryColor = when (tx) {
        is Transaction.Income -> AppTheme.colors.good
        is Transaction.Transfer -> MaterialTheme.colorScheme.onSurfaceVariant
        is Transaction.Expense, is Transaction.Exchange -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(tx) }.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CurrencyChip(txCurrency(tx), state)
                Text(primary, fontWeight = FontWeight.SemiBold, color = primaryColor)
            }
            if (secondary.isNotEmpty()) {
                Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tx.pending) {
                Text("ждёт отправки", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.warning, fontWeight = FontWeight.Medium)
            }
        }
        IconButton(onClick = { onDelete(tx.id) }) {
            Icon(Icons.Filled.Clear, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class RowText(val title: String, val subtitle: String, val primary: String, val secondary: String)

private fun txCurrency(tx: Transaction): Currency = when (tx) {
    is Transaction.Expense -> tx.amount.currency
    is Transaction.Income -> tx.amount.currency
    is Transaction.Transfer -> tx.amount.currency
    is Transaction.Exchange -> tx.given.currency
}

private fun describe(tx: Transaction, state: HomeState): RowText = when (tx) {
    is Transaction.Expense -> RowText(
        title = listOfNotNull(state.categoryName(tx.categoryId), state.subcategoryName(tx.subcategoryId)).joinToString(" · "),
        subtitle = listOfNotNull(state.accountName(tx.accountId), tx.note.ifBlank { null }).joinToString(" · "),
        primary = "−" + MoneyFormat.format(tx.amount),
        secondary = if (tx.amount.currency == Currency.BASE) "" else "≈ ${MoneyFormat.format(tx.amountBase, false)}",
    )
    is Transaction.Income -> RowText(
        title = state.categoryName(tx.categoryId),
        subtitle = listOfNotNull(state.accountName(tx.accountId), tx.note.ifBlank { null }).joinToString(" · "),
        primary = "+" + MoneyFormat.format(tx.amount),
        secondary = "",
    )
    is Transaction.Transfer -> RowText(
        title = "Перевод",
        subtitle = listOfNotNull("${state.accountName(tx.fromAccountId)} → ${state.accountName(tx.toAccountId)}", tx.note.ifBlank { null }).joinToString(" · "),
        primary = MoneyFormat.format(tx.amount),
        secondary = if (state.accountsById[tx.toAccountId]?.isSavings == true) "в накопления" else "",
    )
    is Transaction.Exchange -> RowText(
        title = "Обмен ${tx.given.currency.code} → ${tx.received.currency.code}",
        subtitle = listOfNotNull("${state.accountName(tx.fromAccountId)} → ${state.accountName(tx.toAccountId)}", tx.note.ifBlank { null }).joinToString(" · "),
        primary = "−" + MoneyFormat.format(tx.given),
        secondary = "+" + MoneyFormat.format(tx.received),
    )
}

@Composable
private fun PrefillBanner(prefill: EntryPrefill, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Из Входящих: «${prefill.note}»" + (prefill.amountText?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text("Отменить") }
        }
    }
}

@Composable
private fun InboxCard(
    drafts: List<Draft>,
    onAdd: (String) -> Unit,
    onUse: (Draft) -> Unit,
    onRemove: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Быстрая заметка") },
                    placeholder = { Text("кофе 60к") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = {
                        onAdd(text)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                ) { Text("В список") }
            }
            if (drafts.isEmpty()) {
                Text(
                    "Заметки с виджета и отсюда попадают в этот список. Нажми «Внести», чтобы превратить заметку в операцию.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            drafts.forEach { draft ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(draft.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            DateFormat.dayMonthTime(draft.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onUse(draft) }) { Text("Внести") }
                    IconButton(onClick = { onRemove(draft.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить заметку")
                    }
                }
            }
        }
    }
}
