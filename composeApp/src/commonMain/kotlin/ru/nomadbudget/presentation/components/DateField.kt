package ru.nomadbudget.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import ru.nomadbudget.presentation.format.DateFormat
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    today: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    allowFuture: Boolean = false,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${DateFormat.dayMonth(date)} · ${DateFormat.weekdayFull(date)}", fontWeight = FontWeight.Medium)
            Text(
                when (date) {
                    today -> "сегодня"
                    today.plus(-1, DateTimeUnit.DAY) -> "вчера"
                    else -> date.toString()
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { onChange(date.plus(-1, DateTimeUnit.DAY)) }) { Text("−1") }
        OutlinedButton(onClick = { onChange(today) }, enabled = date != today) { Text("Сегодня") }
        OutlinedButton(onClick = { onChange(date.plus(1, DateTimeUnit.DAY)) }, enabled = allowFuture || date < today) { Text("+1") }
        IconButton(onClick = { pickerOpen = true }) { Icon(Icons.Filled.DateRange, contentDescription = "Выбрать дату") }
    }
    if (pickerOpen) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toEpochMillisUtc())
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = millis.toLocalDateUtc()
                            onChange(if (!allowFuture && picked > today) today else picked)
                        }
                        pickerOpen = false
                    },
                ) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

private fun LocalDate.toEpochMillisUtc(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toLocalDateUtc(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
