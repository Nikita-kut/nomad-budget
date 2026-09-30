package ru.nomadbudget.presentation.exchange

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
import kotlinx.datetime.plus
import ru.nomadbudget.domain.logic.ExchangeAnalyzer
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.components.AccountDropdown
import ru.nomadbudget.presentation.components.CurrencyAmount
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.KeyValueRow
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.ExchangeDraft
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

@Composable
fun ExchangeScreen(state: HomeState, onSubmit: (ExchangeDraft) -> Unit, onDelete: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ExchangeForm(state, onSubmit) }
        item { SectionTitle("Журнал обменов", hint = "спред к кросс-курсу", info = Hints.EXCHANGE_SPREAD) }
        if (state.exchanges.isEmpty()) {
            item { EmptyHint("Обменов пока нет") }
        }
        items(state.exchanges, key = Transaction.Exchange::id) { exchange ->
            ExchangeRow(exchange, state, onDelete)
        }
    }
}

@Composable
private fun ExchangeForm(state: HomeState, onSubmit: (ExchangeDraft) -> Unit) {
    val daily = state.activeAccounts.filterNot { it.isSavings }
    var from by remember(daily) { mutableStateOf(daily.firstOrNull { it.currency == Currency.USD } ?: daily.firstOrNull()) }
    var to by remember(daily) {
        mutableStateOf(daily.firstOrNull { it.currency != Currency.BASE && it.currency != Currency.USD } ?: daily.lastOrNull())
    }
    var givenText by remember { mutableStateOf("") }
    var receivedText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(state.today) }
    var note by remember { mutableStateOf("") }
    val transformation = remember { ThousandsVisualTransformation() }

    val given = from?.let { MoneyFormat.parse(givenText, it.currency) }
    val received = to?.let { MoneyFormat.parse(receivedText, it.currency) }
    val sameCurrency = from != null && to != null && from?.currency == to?.currency
    val sameAccount = from != null && from?.id == to?.id
    val fromCurrency = from?.currency
    val toCurrency = to?.currency
    val hasRates = fromCurrency != null && toCurrency != null && state.rates.hasRate(fromCurrency) && state.rates.hasRate(toCurrency)
    val canSubmit = !state.saving && given != null && received != null && !sameCurrency && !sameAccount && hasRates

    fun submit() {
        val f = from ?: return
        val t = to ?: return
        val g = given ?: return
        val r = received ?: return
        onSubmit(ExchangeDraft(date = date, fromAccountId = f.id, toAccountId = t.id, given = g, received = r, note = note))
        givenText = ""
        receivedText = ""
        note = ""
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountDropdown("Отдал со счёта", daily, from, state, { from = it }, Modifier.weight(1f))
                AccountDropdown("Получил на счёт", daily, to, state, { to = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = givenText,
                    onValueChange = { givenText = ThousandsVisualTransformation.sanitize(it) },
                    label = { Text("Отдал${from?.let { ", ${it.currency.code}" }.orEmpty()}") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = receivedText,
                    onValueChange = { receivedText = ThousandsVisualTransformation.sanitize(it) },
                    label = { Text("Получил${to?.let { ", ${it.currency.code}" }.orEmpty()}") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            RateHints(state, from, to, given, received, sameCurrency, sameAccount)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${DateFormat.dayMonth(date)} · ${DateFormat.weekdayShort(date)}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                OutlinedButton(onClick = { date = date.plus(-1, DateTimeUnit.DAY) }) { Text("−1") }
                OutlinedButton(onClick = { date = state.today }, enabled = date != state.today) { Text("Сегодня") }
                OutlinedButton(onClick = { date = date.plus(1, DateTimeUnit.DAY) }, enabled = date < state.today) { Text("+1") }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Где менял") },
                placeholder = { Text("обменник, ATM, банк") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.saving) "Сохраняем…" else "Записать обмен")
            }
        }
    }
}

@Composable
private fun RateHints(
    state: HomeState,
    from: Account?,
    to: Account?,
    given: ru.nomadbudget.domain.model.Money?,
    received: ru.nomadbudget.domain.model.Money?,
    sameCurrency: Boolean,
    sameAccount: Boolean,
) {
    when {
        from == null || to == null -> Unit
        sameAccount -> Text("Выбери разные счета", color = AppTheme.colors.warning, style = MaterialTheme.typography.bodySmall)
        sameCurrency -> Text("Одна валюта — это перевод, не обмен. Вкладка «Ввод».", color = AppTheme.colors.warning, style = MaterialTheme.typography.bodySmall)
        !state.rates.hasRate(from.currency) || !state.rates.hasRate(to.currency) ->
            Text("Нет курса для одной из валют, заполни таблицу курсов", color = AppTheme.colors.warning, style = MaterialTheme.typography.bodySmall)
        else -> {
            val reference = state.rates.cross(from.currency, to.currency)
            KeyValueRow("Кросс-курс по таблице", "1 ${from.currency.code} = ${MoneyFormat.formatRate(reference)} ${to.currency.code}")
            if (given != null && received != null) {
                val effective = received.toMajor() / given.toMajor()
                val spread = (reference - effective) / reference * 100
                KeyValueRow("Фактический курс", "1 ${from.currency.code} = ${MoneyFormat.formatRate(effective)} ${to.currency.code}", emphasize = true)
                Text(
                    "Спред ${MoneyFormat.formatPercent(-spread)} к таблице",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (spread > 0) AppTheme.colors.bad else AppTheme.colors.good,
                )
            }
        }
    }
}

@Composable
private fun ExchangeRow(exchange: Transaction.Exchange, state: HomeState, onDelete: (String) -> Unit) {
    val analysis = if (state.rates.hasRate(exchange.given.currency) && state.rates.hasRate(exchange.received.currency)) {
        ExchangeAnalyzer.analyze(exchange, state.rates)
    } else {
        null
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CurrencyAmount(MoneyFormat.format(exchange.given), exchange.given.currency, state, MaterialTheme.typography.bodyMedium, FontWeight.SemiBold)
                    Text("→", fontWeight = FontWeight.SemiBold)
                    CurrencyAmount(MoneyFormat.format(exchange.received), exchange.received.currency, state, MaterialTheme.typography.bodyMedium, FontWeight.SemiBold)
                }
                Text(
                    listOfNotNull(
                        "${DateFormat.dayMonth(exchange.date)} · ${state.accountName(exchange.fromAccountId)} → ${state.accountName(exchange.toAccountId)}",
                        exchange.note.ifBlank { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyFormat.formatRate(exchange.effectiveRate), fontWeight = FontWeight.SemiBold)
                if (analysis != null) {
                    Text(
                        "${MoneyFormat.formatPercent(-analysis.spreadPercent)} к таблице",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (analysis.spreadPercent > 0) AppTheme.colors.bad else AppTheme.colors.good,
                    )
                }
            }
            IconButton(onClick = { onDelete(exchange.id) }) {
                Icon(Icons.Filled.Clear, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
    }
}
