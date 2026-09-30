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
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.presentation.components.AccountDropdown
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.KeyValueRow
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

@Composable
fun BalanceCheckScreen(state: HomeState, onBack: () -> Unit, onCheck: (String, Money, String) -> Unit) {
    var account by remember(state.activeAccounts) { mutableStateOf(state.activeAccounts.firstOrNull()) }
    var actualText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val transformation = remember { ThousandsVisualTransformation() }

    val computed = account?.let { state.balances[it.id] }
    val actual = account?.let { acc ->
        if (actualText.trim() == "0") Money.zero(acc.currency) else MoneyFormat.parse(actualText, acc.currency)
    }
    val difference = if (actual != null && computed != null) actual - computed else null
    val canSubmit = !state.saving && account != null && actual != null

    fun submit() {
        val acc = account ?: return
        val money = actual ?: return
        onCheck(acc.id, money, note)
        actualText = ""
        note = ""
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SubScreenHeader("Сверка остатков", onBack) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountDropdown(
                        label = "Счёт",
                        accounts = state.activeAccounts,
                        selected = account,
                        state = state,
                        onSelect = { account = it },
                    )
                    computed?.let { KeyValueRow("По записям в приложении", MoneyFormat.format(it)) }
                    OutlinedTextField(
                        value = actualText,
                        onValueChange = { actualText = ThousandsVisualTransformation.sanitize(it) },
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
                        onValueChange = { note = it },
                        label = { Text("Заметка") },
                        placeholder = { Text("пересчитал кошелёк") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = ::submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.saving) "Сохраняем…" else "Записать сверку")
                    }
                    Text(
                        "Если позже найдёшь забытую трату, внеси её и удали корректировку в журнале.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { SectionTitle("История сверок") }
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
                Text("${DateFormat.dayMonth(check.date)} · ${state.accountName(check.accountId)}", fontWeight = FontWeight.Medium)
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
