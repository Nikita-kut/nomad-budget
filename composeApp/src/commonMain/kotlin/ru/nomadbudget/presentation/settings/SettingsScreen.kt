package ru.nomadbudget.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.logic.CapitalSettings
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.IsoCurrencies
import ru.nomadbudget.platformName
import ru.nomadbudget.presentation.components.AppIcons
import ru.nomadbudget.presentation.components.CapitalSettingsFields
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.CurrencyRow
import ru.nomadbudget.presentation.components.CurrencySearchField
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.IncomeDaysEditor
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.StartDayPicker
import ru.nomadbudget.presentation.components.Ui
import ru.nomadbudget.presentation.components.incomeDaysText
import ru.nomadbudget.presentation.components.startDayText
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.operations
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.more.SubScreenHeader
import ru.nomadbudget.presentation.theme.AppTheme
import ru.nomadbudget.presentation.theme.LocalThemeSettings
import ru.nomadbudget.presentation.theme.ThemeMode

@Composable
fun SettingsScreen(
    state: HomeState,
    onBack: (() -> Unit)?,
    onSetPeriodStartDay: (Int) -> Unit,
    onSetIncomeDays: (List<Int>) -> Unit,
    onSaveCapital: (CapitalSettings) -> Unit,
    onAddCurrency: (String) -> Unit,
    onChangePassword: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val settings = state.settings
    var pendingStartDay by remember { mutableStateOf<Int?>(null) }
    var incomeDays by remember(settings.incomeDays) { mutableStateOf(settings.incomeDays.toSet()) }
    var capitalDraft by remember(settings) { mutableStateOf<CapitalSettings?>(settings.capital) }
    var changingPassword by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var deleteStep by remember { mutableStateOf(0) }
    val theme = LocalThemeSettings.current

    CenteredLazyColumn {
        if (onBack != null) item { SubScreenHeader("Настройки", onBack) }

        item { SectionTitle("Месяц бюджета", hint = "начинается ${startDayText(settings.periodStartDay)}", info = Hints.PERIOD_START) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("День, с которого считается месяц бюджета", fontWeight = FontWeight.Medium)
                    StartDayPicker(selected = settings.periodStartDay, onSelect = { if (it != settings.periodStartDay) pendingStartDay = it })
                    Text(
                        "Текущий месяц: ${startDayText(settings.periodStartDay)}. При смене дня план прошлых месяцев останется на старых датах.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionTitle("Дни дохода", hint = incomeDaysText(settings.incomeDays), info = Hints.INCOME_DAYS) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Когда обычно приходит доход", fontWeight = FontWeight.Medium)
                    IncomeDaysEditor(days = incomeDays, onChange = { incomeDays = it })
                    Text("Подсказка для планирования, месяц не делит.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (incomeDays != settings.incomeDays.toSet()) {
                        Button(onClick = { onSetIncomeDays(incomeDays.toList()) }, enabled = !state.saving) { Text("Сохранить дни дохода") }
                    }
                }
            }
        }

        item { SectionTitle("Подушка и капитал", hint = "${settings.cushionMonths} мес. · ${formatReturn(settings.annualReturn)}", info = Hints.CAPITAL_GOALS) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapitalSettingsFields(settings = settings.capital, onChange = { capitalDraft = it })
                    val draft = capitalDraft
                    if (draft != null && draft != settings.capital) {
                        Button(onClick = { onSaveCapital(draft) }, enabled = !state.saving) { Text("Сохранить") }
                    }
                }
            }
        }

        item { SectionTitle("Тема", hint = themeLabel(theme.mode)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(Ui.CardPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(selected = theme.mode == mode, onClick = { theme.set(mode) }, label = { Text(themeLabel(mode)) })
                    }
                }
            }
        }

        item { SectionTitle("Валюты", hint = "итоги считаются в рублях") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    state.currencies.sortedWith(compareBy({ it != Currency.BASE }, { it.code })).forEachIndexed { index, currency ->
                        if (index > 0) HorizontalDivider()
                        CurrencyRow(
                            code = currency.code,
                            name = IsoCurrencies.nameOf(currency.code) ?: currency.symbol,
                            trailing = when {
                                currency == Currency.BASE -> ({ Text("базовая", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) })
                                !state.rates.hasRate(currency) -> ({ Text("курса пока нет", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.warning) })
                                else -> null
                            },
                        )
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Добавить валюту", fontWeight = FontWeight.Medium)
                    CurrencySearchField(exclude = state.currencies.map { it.code }.toSet(), onPick = { onAddCurrency(it.code) })
                    Text("Курс к рублю появится в течение суток, до этого суммы в этой валюте в итоги не попадут.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { SectionTitle("Аккаунт", hint = platformName()) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ActionRow("Сменить пароль", "новый пароль не короче $MIN_PASSWORD символов") { changingPassword = true }
                    HorizontalDivider()
                    ActionRow("Выйти из аккаунта", "сохранённые копии данных на этом устройстве удалятся") { confirmSignOut = true }
                    HorizontalDivider()
                    ActionRow("Удалить аккаунт", "все данные в облаке будут стёрты без возможности восстановления", danger = true) { deleteStep = 1 }
                }
            }
        }
    }

    pendingStartDay?.let { day ->
        AlertDialog(
            onDismissRequest = { pendingStartDay = null },
            title = { Text("Начинать месяц $day числа?") },
            text = { Text("Новые месяцы будут считаться с $day числа. План прошлых месяцев останется на старых датах, итоги прошлых месяцев пересчитаются по новым границам.") },
            confirmButton = {
                Button(onClick = {
                    onSetPeriodStartDay(day)
                    pendingStartDay = null
                }) { Text("Сменить") }
            },
            dismissButton = { TextButton(onClick = { pendingStartDay = null }) { Text("Отмена") } },
        )
    }

    if (changingPassword) {
        PasswordDialog(onDismiss = { changingPassword = false }) {
            onChangePassword(it)
            changingPassword = false
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
                        if (state.pendingCount > 0) append("\n\nЕщё не отправлено и пропадёт: ${operations(state.pendingCount)}. Сначала нажми «Обновить» при сети.")
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

    when (deleteStep) {
        1 -> AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            title = { Text("Удалить аккаунт?") },
            text = { Text("Удалятся все счета, операции, планы, кредиты и настройки. Восстановить их будет нельзя. Перед удалением можно выгрузить операции в CSV на экране «Ещё».") },
            confirmButton = { TextButton(onClick = { deleteStep = 2 }) { Text("Продолжить", color = AppTheme.colors.bad) } },
            dismissButton = { TextButton(onClick = { deleteStep = 0 }) { Text("Отмена") } },
        )
        2 -> DeleteConfirmDialog(busy = state.saving, onDismiss = { deleteStep = 0 }, onConfirm = onDeleteAccount)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = if (danger) AppTheme.colors.bad else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val valid = password.length >= MIN_PASSWORD && password == repeat
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый пароль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Новый пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = { Text("Ещё раз") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = repeat.isNotEmpty() && repeat != password,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Не короче $MIN_PASSWORD символов. После смены текущая сессия остаётся активной.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(password) }, enabled = valid) { Text("Сменить пароль") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun DeleteConfirmDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Последнее подтверждение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Чтобы удалить аккаунт, введи слово $DELETE_WORD.")
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = typed.trim() == DELETE_WORD && !busy) { Text(if (busy) "Удаление…" else "Удалить навсегда") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Как в системе"
    ThemeMode.LIGHT -> "Светлая"
    ThemeMode.DARK -> "Тёмная"
}

private fun formatReturn(value: Double): String = if (value == 0.0) "без доходности" else "${MoneyFormat.formatPercentValue(value)} годовых"

private const val MIN_PASSWORD = 8
private const val DELETE_WORD = "УДАЛИТЬ"
