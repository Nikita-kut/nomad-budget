package ru.nomadbudget.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import ru.nomadbudget.domain.logic.CapitalSettings
import ru.nomadbudget.domain.model.IsoCurrencies
import ru.nomadbudget.domain.model.IsoCurrency
import ru.nomadbudget.domain.model.SalaryCycle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChips(days: IntRange, selected: Set<Int>, modifier: Modifier = Modifier, onToggle: (Int) -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        days.forEach { day ->
            FilterChip(selected = day in selected, onClick = { onToggle(day) }, label = { Text(day.toString()) })
        }
    }
}

@Composable
fun StartDayPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    DayChips(SalaryCycle.startDays, setOf(selected), modifier, onSelect)
}

@Composable
fun IncomeDaysEditor(days: Set<Int>, onChange: (Set<Int>) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = days.isEmpty(), onClick = { onChange(emptySet()) }, label = { Text("нерегулярно") })
        DayChips(1..MAX_INCOME_DAY, days) { day -> onChange(if (day in days) days - day else days + day) }
    }
}

fun startDayText(day: Int): String = if (day == SalaryCycle.DEFAULT_START_DAY) "1 числа, как календарный месяц" else "$day числа"

fun incomeDaysText(days: Collection<Int>): String = if (days.isEmpty()) "нерегулярно" else days.sorted().joinToString(", ")

fun incomeDaysHint(days: Collection<Int>): String? {
    if (days.isEmpty()) return null
    val sorted = days.sorted()
    val listed = if (sorted.size == 1) sorted.first().toString() else sorted.dropLast(1).joinToString(", ") + " и " + sorted.last()
    return "Доход обычно приходит $listed числа"
}

@Composable
fun CapitalSettingsFields(settings: CapitalSettings, onChange: (CapitalSettings?) -> Unit, modifier: Modifier = Modifier) {
    var months by remember(settings) { mutableStateOf(settings.cushionMonths.toString()) }
    var rate by remember(settings) { mutableStateOf(if (settings.annualReturn == 0.0) "" else settings.annualReturn.toString().removeSuffix(".0")) }
    fun publish() {
        val monthsValue = months.toIntOrNull()
        val rateValue = if (rate.isBlank()) 0.0 else rate.replace(',', '.').toDoubleOrNull()
        val valid = monthsValue != null && monthsValue in CapitalSettings.CUSHION_MONTHS_RANGE && rateValue != null && rateValue in 0.0..CapitalSettings.MAX_ANNUAL_RETURN
        onChange(if (valid) CapitalSettings(monthsValue, rateValue) else null)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = months,
            onValueChange = {
                months = it.filter(Char::isDigit).take(2)
                publish()
            },
            label = { Text("Размер подушки, месяцев расходов") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = rate,
            onValueChange = {
                rate = it
                publish()
            },
            label = { Text("Доходность подушки, % годовых") },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Доходность — сколько в среднем приносят накопительные счета и вклады. Используется только в сценарии «как сейчас + доходность».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CurrencySearchField(exclude: Set<String>, onPick: (IsoCurrency) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, exclude) { IsoCurrencies.search(query, exclude) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(QUERY_LIMIT) },
            label = { Text("Код или название валюты") },
            placeholder = { Text("USD, евро, иена…") },
            singleLine = true,
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        results.forEachIndexed { index, iso ->
            if (index > 0) HorizontalDivider()
            CurrencyRow(
                code = iso.code,
                name = iso.name,
                modifier = Modifier.clickable {
                    onPick(iso)
                    query = ""
                },
            )
        }
        if (query.isNotBlank() && results.isEmpty()) {
            Text("Такого кода нет в списке ISO 4217", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CurrencyRow(code: String, name: String?, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(code, fontWeight = FontWeight.Medium, modifier = Modifier.width(CODE_WIDTH))
        Text(name ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

private const val MAX_INCOME_DAY = 31
private const val QUERY_LIMIT = 32
private val CODE_WIDTH = 48.dp
