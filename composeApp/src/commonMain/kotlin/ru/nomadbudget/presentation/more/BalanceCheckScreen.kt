package ru.nomadbudget.presentation.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.logic.BalanceCalculator
import ru.nomadbudget.presentation.components.DateField
import androidx.compose.material3.OutlinedButton
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.presentation.components.AccountDropdown
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.KeyValueRow
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme
import ru.nomadbudget.presentation.components.AccountsLine
import ru.nomadbudget.presentation.components.CenteredLazyColumn

@Composable
fun BalanceCheckScreen(
    state: HomeState,
    onBack: () -> Unit,
    onCheck: (String, Money, String, LocalDate) -> Unit,
    onAddMissed: (String, LocalDate) -> Unit = { _, _ -> },
    form: BalanceCheckForm = remember { BalanceCheckForm(state.today) },
) {
    val account: Account? = form.accountId?.let { id -> state.activeAccounts.firstOrNull { it.id == id } } ?: state.activeAccounts.firstOrNull()
    val actualText = form.actualText
    val note = form.note
    val date = form.date
    val transformation = remember { ThousandsVisualTransformation() }

    val computed = account?.let { acc -> BalanceCalculator.balanceBefore(acc, state.transactions, date.plus(1, DateTimeUnit.DAY)) }
    val lastCheck = account?.let { acc -> state.balanceChecks.filter { it.accountId == acc.id && it.date < date }.maxByOrNull { it.date } }
    val since = account?.let { acc ->
        state.transactions.filter { tx ->
            tx.date <= date && (lastCheck == null || tx.date > lastCheck.date) && when (tx) {
                is Transaction.Expense -> tx.accountId == acc.id
                is Transaction.Income -> tx.accountId == acc.id
                is Transaction.Transfer -> tx.fromAccountId == acc.id || tx.toAccountId == acc.id
                is Transaction.Exchange -> tx.fromAccountId == acc.id || tx.toAccountId == acc.id
            }
        }.sortedByDescending { it.date }
    }.orEmpty()
    val actual = account?.let { acc ->
        if (actualText.trim() == "0") Money.zero(acc.currency) else MoneyFormat.parse(actualText, acc.currency)
    }
    val difference = if (actual != null && computed != null) actual - computed else null
    val canSubmit = !state.saving && account != null && actual != null

    fun submit() {
        val acc = account ?: return
        val money = actual ?: return
        onCheck(acc.id, money, note, date)
        form.actualText = ""
        form.note = ""
    }

    CenteredLazyColumn(bottom = 12.dp) {
        item { SubScreenHeader("Сверка остатков", onBack) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountDropdown(
                        label = "Счёт",
                        accounts = state.activeAccounts,
                        selected = account,
                        state = state,
                        onSelect = { form.accountId = it.id },
                    )
                    DateField(date = date, today = state.today, onChange = { form.date = it })
                    computed?.let { KeyValueRow("По записям на конец дня", MoneyFormat.format(it)) }
                    OutlinedTextField(
                        value = actualText,
                        onValueChange = { form.actualText = ThousandsVisualTransformation.sanitize(it, account?.currency?.minorUnits ?: 2) },
                        label = { Text("Фактический остаток${account?.let { ", ${it.currency.code}" }.orEmpty()}") },
                        singleLine = true,
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    difference?.let { diff ->
                        val (label, color) = when {
                            diff.isZero -> "Сходится" to AppTheme.colors.good
                            diff.isNegative -> "Не хватает ${MoneyFormat.format(-diff)}, запишу как расход «Корректировка»" to AppTheme.colors.bad
                            else -> "Лишние ${MoneyFormat.format(diff)}, запишу как доход «Корректировка»" to AppTheme.colors.warning
                        }
                        Text(label, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { form.note = it },
                        label = { Text("Заметка") },
                        placeholder = { Text("пересчитал кошелёк") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (difference != null && !difference.isZero && account != null) {
                        OutlinedButton(onClick = { account?.let { onAddMissed(it.id, date) } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Сначала внести пропущенную трату")
                        }
                    }
                    Button(onClick = ::submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            when {
                                state.saving -> "Сохраняем…"
                                difference == null || difference.isZero -> "Записать сверку на ${DateFormat.dayMonth(date)}"
                                else -> "Записать корректировку ${MoneyFormat.formatSigned(difference)} на ${DateFormat.dayMonth(date)}"
                            },
                        )
                    }
                    Text(
                        "Корректировка — служебная запись: она выравнивает остаток счёта и не участвует в бюджете месяца.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (account != null) {
            item {
                SectionTitle(
                    if (lastCheck != null) "После сверки ${DateFormat.dayMonth(lastCheck.date)}" else "Последние операции по счёту",
                    hint = "записей: ${since.size}",
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (since.isEmpty()) Text("Операций нет", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        since.take(MAX_SINCE).forEach { tx ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(DateFormat.dayMonth(tx.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(describe(tx, state), style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(MoneyFormat.format(tx.amountBase, false), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("История сверок", info = Hints.BALANCE_CHECK) }
        if (state.balanceChecks.isEmpty()) {
            item { EmptyHint("Сверок ещё не было") }
        }
        items(state.balanceChecks, key = BalanceCheck::id) { check -> CheckRow(check, state) }
    }
}

@Composable
private fun CheckRow(check: BalanceCheck, state: HomeState) {
    val diff = check.difference
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AccountsLine(
                    state,
                    fromId = check.accountId,
                    prefix = DateFormat.dayMonth(check.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    listOfNotNull("факт ${MoneyFormat.format(check.actual)}", check.note.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (diff.isZero) "сходится" else MoneyFormat.formatSigned(diff),
                fontWeight = FontWeight.SemiBold,
                color = when {
                    diff.isZero -> AppTheme.colors.good
                    diff.isNegative -> AppTheme.colors.bad
                    else -> AppTheme.colors.warning
                },
            )
        }
        HorizontalDivider()
    }
}

private const val MAX_SINCE = 30

private fun describe(tx: Transaction, state: HomeState): String = when (tx) {
    is Transaction.Expense -> listOfNotNull(state.categoryName(tx.categoryId), state.subcategoryName(tx.subcategoryId)).joinToString(" · ")
    is Transaction.Income -> "доход · ${state.categoryName(tx.categoryId)}"
    is Transaction.Transfer -> "перевод"
    is Transaction.Exchange -> "обмен"
}

class BalanceCheckForm(today: LocalDate) {
    var accountId by mutableStateOf<String?>(null)
    var actualText by mutableStateOf("")
    var note by mutableStateOf("")
    var date by mutableStateOf(today)
}
