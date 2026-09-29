package ru.nomadbudget.presentation.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.sumIn
import ru.nomadbudget.presentation.components.CurrencyChip
import ru.nomadbudget.presentation.components.KeyValueRow
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState

@Composable
fun AccountsScreen(state: HomeState) {
    val daily = state.accounts.filterNot { it.isSavings }
    val savings = state.accounts.filter { it.isSavings }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionTitle("Ежедневные", hint = "в валюте счёта · в ₽") }
        item { AccountsCard(daily, state, totalLabel = "Итого ежедневные") }
        item { SectionTitle("Накопления") }
        item { AccountsCard(savings, state, totalLabel = "Итого накопления") }
        item { SectionTitle("Курсы", hint = rateHint(state)) }
        item { RatesCard(state) }
    }
}

private fun rateHint(state: HomeState): String {
    val usd = state.rates.rateFor(Currency.USD) ?: return "нет данных"
    return when (usd.source) {
        RateSource.MANUAL -> "по умолчанию, таблица пуста"
        RateSource.API -> "снимок за ${DateFormat.dayMonth(usd.date)}"
    }
}

@Composable
private fun AccountsCard(accounts: List<Account>, state: HomeState, totalLabel: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        accounts.forEachIndexed { index, account ->
            if (index > 0) HorizontalDivider()
            AccountRow(account, state)
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
private fun AccountRow(account: Account, state: HomeState) {
    val balance = state.balances.getValue(account.id)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(account.name, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(kindLabel(account.kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CurrencyChip(account.currency)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(MoneyFormat.format(balance), fontWeight = FontWeight.SemiBold)
            if (account.currency != Currency.BASE) {
                val inBase = state.rates.toBaseOrNull(balance)
                Text(
                    if (inBase != null) "≈ ${MoneyFormat.format(inBase, false)}" else "нет курса",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
