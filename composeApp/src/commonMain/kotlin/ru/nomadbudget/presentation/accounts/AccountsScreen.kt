package ru.nomadbudget.presentation.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.sumIn
import ru.nomadbudget.presentation.components.CurrencyAmount
import ru.nomadbudget.presentation.components.CurrencyChip
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.KeyValueRow
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.components.AccountName

@Composable
fun AccountsScreen(
    state: HomeState,
    onAdd: (String, Currency, Boolean) -> Unit,
    onRename: (String, String) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onArchive: (String) -> Unit,
) {
    val daily = state.activeAccounts.filterNot { it.isSavings }.sortedBy { it.sortOrder }
    val savings = state.activeAccounts.filter { it.isSavings }.sortedBy { it.sortOrder }
    var adding by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf<Account?>(null) }
    var renaming by remember { mutableStateOf<Account?>(null) }
    var editMode by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Ежедневные", hint = if (editMode) "стрелки меняют порядок" else "в валюте счёта · в ₽", modifier = Modifier.weight(1f), info = Hints.ACCOUNTS_TOTAL)
                TextButton(onClick = { editMode = !editMode }) { Text(if (editMode) "Готово" else "Изменить") }
            }
        }
        item {
            AccountsCard(daily, state, totalLabel = "Итого ежедневные", editMode = editMode, onRename = { renaming = it }, onMove = onMove, onArchive = { archiving = it })
        }
        item { SectionTitle("Накопления") }
        item {
            AccountsCard(savings, state, totalLabel = "Итого накопления", editMode = editMode, onRename = { renaming = it }, onMove = onMove, onArchive = { archiving = it })
        }
        item {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Добавить счёт") }
        }
        item { SectionTitle("Курсы", hint = rateHint(state), info = Hints.RATES) }
        item { RatesCard(state) }
    }

    if (adding) {
        AddAccountDialog(currencies = state.currencies, onDismiss = { adding = false }) { name, currency, isSavings ->
            onAdd(name, currency, isSavings)
            adding = false
        }
    }
    renaming?.let { account ->
        RenameDialog(initial = account.name, onDismiss = { renaming = null }) { name ->
            onRename(account.id, name)
            renaming = null
        }
    }
    archiving?.let { account ->
        val balance = state.balances[account.id]
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text("Убрать «${account.name}» в архив?") },
            text = {
                Text(
                    buildString {
                        append("Счёт исчезнет из списков и итогов, операции по нему останутся в истории.")
                        if (balance != null && !balance.isZero) {
                            append(" На счёте сейчас ${MoneyFormat.format(balance)}: сначала переведи деньги на другой счёт или сверь остаток в ноль.")
                        }
                    },
                )
            },
            confirmButton = {
                Button(onClick = { onArchive(account.id); archiving = null }, enabled = balance?.isZero != false) { Text("В архив") }
            },
            dismissButton = { TextButton(onClick = { archiving = null }) { Text("Отмена") } },
        )
    }
}

private fun rateHint(state: HomeState): String {
    val fromApi = state.rates.all.firstOrNull { it.source == RateSource.API } ?: return "по умолчанию, таблица пуста"
    return buildString {
        append("${fromApi.sourceName} · за ${DateFormat.dayMonth(fromApi.date)}")
        fromApi.fetchedAt?.let { append(" · получен ${DateFormat.dayMonthTime(it)}") }
    }
}

@Composable
private fun AccountsCard(
    accounts: List<Account>,
    state: HomeState,
    totalLabel: String,
    editMode: Boolean,
    onRename: (Account) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onArchive: (Account) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (accounts.isEmpty()) EmptyHint("Счетов нет")
        accounts.forEachIndexed { index, account ->
            if (index > 0) HorizontalDivider()
            AccountRow(
                account = account,
                state = state,
                editMode = editMode,
                canMoveUp = index > 0,
                canMoveDown = index < accounts.lastIndex,
                onRename = { onRename(account) },
                onMoveUp = { onMove(account.id, true) },
                onMoveDown = { onMove(account.id, false) },
                onArchive = { onArchive(account) },
            )
        }
        HorizontalDivider()
        val total = accounts.mapNotNull { state.rates.toBaseOrNull(state.balances.getValue(it.id)) }.sumIn(Currency.BASE)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(totalLabel, fontWeight = FontWeight.SemiBold)
            Text(MoneyFormat.format(total, false), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    state: HomeState,
    editMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit,
) {
    val balance = state.balances.getValue(account.id)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = if (editMode) 4.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editMode) {
            Column {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Выше") }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Ниже") }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            AccountName(account, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(kindLabel(account.kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CurrencyChip(account.currency, state)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            CurrencyAmount(MoneyFormat.format(balance), account.currency, state, MaterialTheme.typography.bodyLarge, FontWeight.SemiBold)
            if (account.currency != Currency.BASE) {
                val inBase = state.rates.toBaseOrNull(balance)
                Text(
                    if (inBase != null) "≈ ${MoneyFormat.format(inBase, false)}" else "нет курса",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (editMode) {
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Переименовать", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Filled.Delete, contentDescription = "В архив", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать счёт") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun AddAccountDialog(currencies: List<Currency>, onDismiss: () -> Unit, onConfirm: (String, Currency, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(Currency.BASE) }
    var isSavings by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый счёт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    placeholder = { Text("карта, наличные, вклад…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Dropdown("Валюта", currencies, currency, { "${it.code} · ${it.symbol}" }, { currency = it })
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Накопительный", fontWeight = FontWeight.Medium)
                        Text("не входит в «на жизнь», переводы на него считаются отложенными", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isSavings, onCheckedChange = { isSavings = it })
                }
                Text(
                    "Начальный остаток задаётся сверкой: «Ещё → Сверка остатков», введи фактическую сумму.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, currency, isSavings) }, enabled = name.isNotBlank()) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun kindLabel(kind: AccountKind): String = when (kind) {
    AccountKind.CARD -> "карта"
    AccountKind.ACCOUNT -> "счёт"
    AccountKind.CASH -> "наличные"
    AccountKind.SAVINGS -> "накопления"
    AccountKind.INVESTMENT -> "инвестиции"
}

@Composable
private fun RatesCard(state: HomeState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            state.foreignCurrencies.forEach { currency ->
                if (state.rates.hasRate(currency)) {
                    val perUnit = state.rates.basePerUnit(currency)
                    val units = if (perUnit < SMALL_RATE) 1_000 else 1
                    KeyValueRow(
                        "${if (units > 1) "$units " else ""}${currency.code} → ${Currency.BASE.code}",
                        "${MoneyFormat.formatRate(perUnit * units)} ${Currency.BASE.symbol}",
                    )
                } else {
                    KeyValueRow("${currency.code} → ${Currency.BASE.code}", "нет курса")
                }
            }
            state.foreignCurrencies
                .filter { it != Currency.USD && state.rates.hasRate(it) }
                .forEach { currency ->
                    KeyValueRow(
                        "${Currency.USD.code} → ${currency.code} (кросс)",
                        "${MoneyFormat.formatRate(state.rates.cross(Currency.USD, currency))} ${currency.symbol}",
                    )
                }
        }
    }
}

private const val SMALL_RATE = 0.01
