package ru.nomadbudget.presentation.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.platformName
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.sumIn
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ru.nomadbudget.presentation.theme.AppTheme

@Composable
fun MoreScreen(
    state: HomeState,
    onCategories: () -> Unit,
    onBalanceCheck: () -> Unit,
    onRates: () -> Unit,
    onCharts: () -> Unit,
    onExchange: () -> Unit,
    onDebts: () -> Unit,
    showSecondary: Boolean,
    onSignOut: () -> Unit,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSecondary) {
            item { SectionTitle("Деньги") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    MenuRow("Обмен валюты", state.exchanges.firstOrNull()?.let { "последний ${DateFormat.dayMonth(it.date)}" } ?: "обменов ещё не было", onExchange)
                    HorizontalDivider()
                    MenuRow(
                        "Кредиты",
                        if (state.openDebts.isEmpty()) "открытых нет" else "открыто ${state.openDebts.size} · должен ${MoneyFormat.format(state.openDebts.mapNotNull { state.rates.toBaseOrNull(it.principalRemaining) }.sumIn(Currency.BASE), false)}",
                        onDebts,
                    )
                }
            }
            item { SectionTitle("Аналитика") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    MenuRow("Графики", "доходы, расходы, накопления по месяцам", onCharts)
                    HorizontalDivider()
                    MenuRow(
                        "Курсы валют",
                        state.rates.all.firstOrNull()?.let { "обновлены ${DateFormat.dayMonth(it.date)}, раз в сутки" } ?: "текущие и история по дням",
                        onRates,
                    )
                }
            }
        }
        item { SectionTitle("Настройки") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                MenuRow("Категории и подкатегории", "${state.activeCategories.size} категорий, ${state.subcategories.size} подкатегорий", onCategories)
                HorizontalDivider()
                MenuRow(
                    "Сверка остатков",
                    state.balanceChecks.firstOrNull()?.let { "последняя ${DateFormat.dayMonth(it.date)}" } ?: "ещё не сверялись",
                    onBalanceCheck,
                )
            }
        }
        item { SectionTitle("Аккаунт") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(platformName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { confirmSignOut = true }) { Text("Выйти из аккаунта") }
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Выйти из аккаунта?") },
            text = {
                Text(
                    buildString {
                        append("С этого устройства удалятся сохранённые копии данных и заметки из «Входящих». В облаке всё останется.")
                        if (state.pendingCount > 0) append("\n\n${state.pendingCount} операций ещё не отправлены и пропадут. Сначала нажми «Обновить» при сети.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Выйти", color = AppTheme.colors.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
