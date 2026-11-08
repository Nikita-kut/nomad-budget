package ru.nomadbudget.presentation.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.operations
import ru.nomadbudget.presentation.format.plural
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.AppIcons

@Composable
fun MoreScreen(
    state: HomeState,
    onCategories: () -> Unit,
    onBalanceCheck: () -> Unit,
    onRates: () -> Unit,
    onCharts: () -> Unit,
    onCapital: () -> Unit,
    onExchange: () -> Unit,
    onDebts: () -> Unit,
    showSecondary: Boolean,
    onExport: () -> Unit,
    onSettings: () -> Unit,
) {
    CenteredLazyColumn {
        if (showSecondary) {
            item { SectionTitle("Деньги") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    MenuRow("Обмен валюты", state.exchanges.firstOrNull()?.let { "последний ${DateFormat.dayMonth(it.date)}" } ?: "обменов ещё не было", onExchange)
                    HorizontalDivider()
                    MenuRow(
                        "Кредиты",
                        if (state.openDebts.isEmpty()) "открытых нет" else "открыто ${state.openDebts.size} · остаток ${MoneyFormat.format(state.capital.debt, false)}",
                        onDebts,
                    )
                }
            }
            item { SectionTitle("Аналитика") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    MenuRow("Капитал", "счета минус долги, прогноз, цели", onCapital)
                    HorizontalDivider()
                    MenuRow("Графики", "доходы, расходы, подушка по месяцам", onCharts)
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
                MenuRow("Категории и подкатегории", "${plural(state.activeCategories.size, "категория", "категории", "категорий")}, ${plural(state.subcategories.size, "подкатегория", "подкатегории", "подкатегорий")}", onCategories)
                HorizontalDivider()
                MenuRow(
                    "Сверка остатков",
                    state.balanceChecks.firstOrNull()?.let { "последняя ${DateFormat.dayMonth(it.date)}" } ?: "сверок ещё не было",
                    onBalanceCheck,
                )
                HorizontalDivider()
                MenuRow("Выгрузить операции в CSV", "${operations(state.transactions.size)}, открывается в Excel и Google Таблицах", onExport)
                HorizontalDivider()
                MenuRow("Настройки", "начало месяца, валюты, тема, пароль, выход", onSettings)
            }
        }
        item { SectionTitle("Горячие клавиши", hint = "в вебе") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("N — новая запись · J — журнал · P — план", style = MaterialTheme.typography.bodyMedium)
                    Text("M — месяц · A — счета · Esc — назад", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
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
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(AppIcons.ArrowLeft, contentDescription = "Назад") }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
