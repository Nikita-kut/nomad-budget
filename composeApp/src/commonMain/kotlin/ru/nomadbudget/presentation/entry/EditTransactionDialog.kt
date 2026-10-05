package ru.nomadbudget.presentation.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.components.AccountDropdown
import ru.nomadbudget.presentation.components.DateField
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.TagChip
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

private const val MAX_SUGGESTIONS = 8

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTransactionDialog(
    tx: Transaction,
    state: HomeState,
    onDismiss: () -> Unit,
    onSave: (Transaction, String?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val transformation = remember { ThousandsVisualTransformation() }
    var date by remember { mutableStateOf(tx.date) }
    var note by remember { mutableStateOf(tx.note) }
    var debtEarly by remember { mutableStateOf((tx as? Transaction.Expense)?.debtEarly == true) }

    val firstAccountId = when (tx) {
        is Transaction.Expense -> tx.accountId
        is Transaction.Income -> tx.accountId
        is Transaction.Transfer -> tx.fromAccountId
        is Transaction.Exchange -> tx.fromAccountId
    }
    val secondAccountId = when (tx) {
        is Transaction.Transfer -> tx.toAccountId
        is Transaction.Exchange -> tx.toAccountId
        is Transaction.Expense, is Transaction.Income -> null
    }
    var account by remember { mutableStateOf(state.accountsById[firstAccountId]) }
    var toAccount by remember { mutableStateOf(secondAccountId?.let { state.accountsById[it] }) }
    val firstAmount = when (tx) {
        is Transaction.Expense -> tx.amount
        is Transaction.Income -> tx.amount
        is Transaction.Transfer -> tx.amount
        is Transaction.Exchange -> tx.given
    }
    var amountText by remember { mutableStateOf(majorText(firstAmount)) }
    var receivedText by remember { mutableStateOf((tx as? Transaction.Exchange)?.received?.let(::majorText).orEmpty()) }

    val kind = when (tx) {
        is Transaction.Expense -> CategoryKind.EXPENSE
        is Transaction.Income -> CategoryKind.INCOME
        is Transaction.Transfer, is Transaction.Exchange -> null
    }
    val categoryId = when (tx) {
        is Transaction.Expense -> tx.categoryId
        is Transaction.Income -> tx.categoryId
        is Transaction.Transfer, is Transaction.Exchange -> null
    }
    val categories = kind?.let { k -> state.categories.filter { it.kind == k && (!it.isArchived || it.id == categoryId) } }.orEmpty()
    var category by remember { mutableStateOf(categoryId?.let { state.categoriesById[it] }) }
    var subcategory by remember { mutableStateOf((tx as? Transaction.Expense)?.let { state.subcategoryName(it.subcategoryId) }.orEmpty()) }

    val accountCurrency = account?.currency
    val amount = accountCurrency?.let { MoneyFormat.parse(amountText, it) }
    val received = toAccount?.currency?.let { MoneyFormat.parse(receivedText, it) }
    val suggestions = category?.let { cat ->
        state.subcategories.filter { it.categoryId == cat.id && (subcategory.isBlank() || it.name.contains(subcategory, ignoreCase = true)) }
            .map { it.name }.distinct().take(MAX_SUGGESTIONS)
    }.orEmpty()

    val valid = when (tx) {
        is Transaction.Expense, is Transaction.Income -> account != null && amount != null && category != null
        is Transaction.Transfer -> account != null && toAccount != null && amount != null && account?.id != toAccount?.id && account?.currency == toAccount?.currency
        is Transaction.Exchange -> account != null && toAccount != null && amount != null && received != null && account?.currency != toAccount?.currency
    }

    fun build(): Transaction? {
        val acc = account ?: return null
        val money = amount ?: return null
        return when (tx) {
            is Transaction.Expense -> tx.copy(date = date, accountId = acc.id, amount = money, categoryId = category?.id ?: return null, note = note, debtEarly = tx.debtId != null && debtEarly)
            is Transaction.Income -> tx.copy(date = date, accountId = acc.id, amount = money, categoryId = category?.id ?: return null, note = note)
            is Transaction.Transfer -> tx.copy(date = date, fromAccountId = acc.id, toAccountId = toAccount?.id ?: return null, amount = money, note = note)
            is Transaction.Exchange -> tx.copy(
                date = date, fromAccountId = acc.id, toAccountId = toAccount?.id ?: return null,
                given = money, received = received ?: return null, note = note,
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (tx) {
                    is Transaction.Expense -> "Изменить расход"
                    is Transaction.Income -> "Изменить доход"
                    is Transaction.Transfer -> "Изменить перевод"
                    is Transaction.Exchange -> "Изменить обмен"
                },
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(date = date, today = state.today, onChange = { date = it })
                AccountDropdown(
                    label = when (tx) {
                        is Transaction.Expense -> "Со счёта"
                        is Transaction.Income -> "На счёт"
                        is Transaction.Transfer, is Transaction.Exchange -> "Откуда"
                    },
                    accounts = state.activeAccounts.let { list -> if (account != null && account !in list) list + listOfNotNull(account) else list },
                    selected = account,
                    state = state,
                    onSelect = { account = it },
                )
                if (secondAccountId != null) {
                    val targets = when (tx) {
                        is Transaction.Transfer -> state.activeAccounts.filter { it.currency == accountCurrency && it.id != account?.id }
                        else -> state.activeAccounts.filter { it.id != account?.id }
                    }
                    AccountDropdown(label = "Куда", accounts = targets, selected = toAccount, state = state, onSelect = { toAccount = it })
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = ThousandsVisualTransformation.sanitize(it) },
                    label = { Text(if (tx is Transaction.Exchange) "Отдал, ${accountCurrency?.code.orEmpty()}" else "Сумма, ${accountCurrency?.code.orEmpty()}") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tx is Transaction.Exchange) {
                    OutlinedTextField(
                        value = receivedText,
                        onValueChange = { receivedText = ThousandsVisualTransformation.sanitize(it) },
                        label = { Text("Получил, ${toAccount?.currency?.code.orEmpty()}") },
                        singleLine = true,
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind != null) {
                    Dropdown(label = "Категория", items = categories, selected = category, itemLabel = Category::name, onSelect = { category = it })
                }
                if (tx is Transaction.Expense) {
                    OutlinedTextField(
                        value = subcategory,
                        onValueChange = { subcategory = it },
                        label = { Text("Подкатегория") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (suggestions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestions.forEach { name -> TagChip(text = name, selected = name.equals(subcategory, ignoreCase = true), onClick = { subcategory = name }) }
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
                if (tx is Transaction.Expense && tx.debtId != null) {
                    val debt = state.debts.firstOrNull { it.id == tx.debtId }
                    Text(
                        "Платёж по кредиту${debt?.let { " «${it.name}»" }.orEmpty()}. Остаток кредита пересчитается при сохранении.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EarlyPaymentToggle(checked = debtEarly, onChange = { debtEarly = it }, preview = null, differentCurrency = debt != null && account?.currency != debt.currency)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Сумма в ₽ пересчитается по сегодняшнему курсу, если сумму изменил.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = { build()?.let { onSave(it, (tx as? Transaction.Expense)?.let { subcategory }) } }, enabled = valid && !state.saving) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Row {
                onDelete?.let { delete -> TextButton(onClick = delete) { Text("Удалить", color = AppTheme.colors.bad) } }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

private fun majorText(money: Money): String {
    val major = money.minor / money.currency.minorFactor
    val fraction = money.minor % money.currency.minorFactor
    return if (fraction == 0L) major.toString() else "$major,${fraction.toString().padStart(money.currency.minorUnits, '0')}"
}
