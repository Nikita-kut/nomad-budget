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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.sumIn
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.InfoHint
import ru.nomadbudget.presentation.components.KeyValueRow
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

private const val DEFAULT_DEBT_CATEGORY = "Долг"

@Composable
fun DebtsScreen(
    state: HomeState,
    onBack: (() -> Unit)?,
    onSave: (Debt) -> Unit,
    onClose: (String) -> Unit,
    onPlanIntoMonth: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<Debt?>(null) }
    var creating by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<Debt?>(null) }
    val expenseCategories = state.activeCategories.filter { it.kind == CategoryKind.EXPENSE }
    var planCategory by remember(expenseCategories) {
        mutableStateOf(expenseCategories.firstOrNull { it.name.equals(DEFAULT_DEBT_CATEGORY, ignoreCase = true) } ?: expenseCategories.firstOrNull())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            item { SubScreenHeader("Кредиты", onBack) }
        }
        if (state.openDebts.isNotEmpty()) {
            item { DebtsSummary(state) }
            item { SectionTitle("Открытые", hint = "нажми, чтобы править", info = Hints.DEBTS) }
            items(state.openDebts, key = Debt::id) { debt ->
                DebtCard(debt, state, onClick = { editing = debt }, onClose = { closing = debt })
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("В план текущего месяца", fontWeight = FontWeight.Medium)
                            InfoHint("План по кредитам", Hints.DEBTS_PLAN)
                        }
                        state.openDebts.forEach { debt ->
                            KeyValueRow(
                                debt.name,
                                buildString {
                                    append(MoneyFormat.format(debt.plannedPayment, false))
                                    if (!debt.extraPayment.isZero) append(" (платёж + досрочно ${MoneyFormat.format(debt.extraPayment, false)})")
                                },
                            )
                        }
                        val byCurrency = state.openDebts.groupBy { it.currency }
                        byCurrency.forEach { (currency, debts) ->
                            KeyValueRow("Итого в план, ${currency.code}", MoneyFormat.format(debts.map { it.plannedPayment }.sumIn(currency), false), emphasize = true)
                        }
                        Dropdown("Категория плана", expenseCategories, planCategory, Category::name, { planCategory = it })
                        Button(
                            onClick = { planCategory?.let { onPlanIntoMonth(it.id) } },
                            enabled = planCategory != null && !state.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Добавить в план ${state.period.title()}") }
                    }
                }
            }
        } else {
            item { EmptyHint("Кредитов пока нет. Добавь первый, и он появится в плане месяца одной кнопкой.") }
        }
        item {
            OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("Добавить кредит") }
        }
        val closed = state.debts.filter { it.isClosed }
        if (closed.isNotEmpty()) {
            item { SectionTitle("Закрытые") }
            items(closed, key = Debt::id) { debt ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(debt.name, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (creating) {
        DebtDialog(initial = null, currencies = state.currencies, onDismiss = { creating = false }) {
            onSave(it)
            creating = false
        }
    }
    editing?.let { debt ->
        DebtDialog(initial = debt, currencies = state.currencies, onDismiss = { editing = null }) {
            onSave(it)
            editing = null
        }
    }
    closing?.let { debt ->
        ConfirmDialog(
            title = "Закрыть «${debt.name}»?",
            text = "Кредит уйдёт в закрытые, платежи по нему останутся в истории.",
            confirmText = "Закрыть",
            onDismiss = { closing = null },
            onConfirm = {
                onClose(debt.id)
                closing = null
            },
        )
    }
}

@Composable
private fun DebtsSummary(state: HomeState) {
    val byCurrency = state.openDebts.groupBy { it.currency }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            byCurrency.forEach { (currency, debts) ->
                KeyValueRow("Должен, ${currency.code}", MoneyFormat.format(debts.map { it.principalRemaining }.sumIn(currency), false), emphasize = true)
                KeyValueRow("Платежей в месяц, ${currency.code}", MoneyFormat.format(debts.map { it.monthlyPayment }.sumIn(currency), false))
            }
        }
    }
}

@Composable
private fun DebtCard(debt: Debt, state: HomeState, onClick: () -> Unit, onClose: () -> Unit) {
    val paid = state.paidThisPeriod(debt)
    val paidEnough = paid.wholeUnits >= debt.plannedPayment.wholeUnits
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(debt.name, fontWeight = FontWeight.SemiBold)
                Text(MoneyFormat.format(debt.principalRemaining, false), fontWeight = FontWeight.SemiBold)
            }
            val details = listOfNotNull(
                "платёж ${MoneyFormat.format(debt.monthlyPayment, false)}",
                debt.extraPayment.takeUnless { it.isZero }?.let { "досрочно ${MoneyFormat.format(it, false)}" },
                debt.ratePercent?.let { "${MoneyFormat.formatRate(it)}%" },
                debt.payDay?.let { "до $it числа" },
                debt.ratePercent?.let { "проценты ≈ ${MoneyFormat.format(DebtCalculator.monthlyInterest(debt), false)} в месяц" },
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (paid.isZero) "в этом месяце не платил" else "в этом месяце ${MoneyFormat.format(paid, false)} из ${MoneyFormat.format(debt.plannedPayment, false)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (paidEnough) AppTheme.colors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClose) { Text("Закрыть") }
            }
        }
    }
}

@Composable
private fun DebtDialog(initial: Debt?, currencies: List<Currency>, onDismiss: () -> Unit, onConfirm: (Debt) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var currency by remember { mutableStateOf(initial?.currency ?: Currency.BASE) }
    var remaining by remember { mutableStateOf(initial?.principalRemaining?.let(::majorText).orEmpty()) }
    var payment by remember { mutableStateOf(initial?.monthlyPayment?.let(::majorText).orEmpty()) }
    var extra by remember { mutableStateOf(initial?.extraPayment?.takeUnless { it.isZero }?.let(::majorText).orEmpty()) }
    var rate by remember { mutableStateOf(initial?.ratePercent?.toString().orEmpty()) }
    var payDay by remember { mutableStateOf(initial?.payDay?.toString().orEmpty()) }
    val transformation = remember { ThousandsVisualTransformation() }

    val remainingMoney = MoneyFormat.parse(remaining, currency) ?: if (remaining.trim() == "0") Money.zero(currency) else null
    val paymentMoney = MoneyFormat.parse(payment, currency)
    val extraMoney = if (extra.isBlank() || extra.trim() == "0") Money.zero(currency) else MoneyFormat.parse(extra, currency)
    val rateValue = rate.replace(',', '.').trim().toDoubleOrNull()
    val payDayValue = payDay.trim().toIntOrNull()
    val valid = name.isNotBlank() && remainingMoney != null && paymentMoney != null && extraMoney != null &&
        (rate.isBlank() || rateValue != null) && (payDay.isBlank() || payDayValue in 1..31)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый кредит" else "Кредит: ${initial.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (initial == null) {
                    Dropdown("Валюта", currencies, currency, Currency::code, { currency = it })
                }
                OutlinedTextField(
                    value = remaining, onValueChange = { remaining = ThousandsVisualTransformation.sanitize(it) },
                    label = { Text("Остаток долга, ${currency.code}") }, singleLine = true, visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = payment, onValueChange = { payment = ThousandsVisualTransformation.sanitize(it) },
                    label = { Text("Платёж в месяц, ${currency.code}") }, singleLine = true, visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = extra, onValueChange = { extra = ThousandsVisualTransformation.sanitize(it) },
                    label = { Text("Досрочно в месяц сверху, ${currency.code}") }, singleLine = true, visualTransformation = transformation,
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rate, onValueChange = { rate = it }, label = { Text("Ставка, %") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = payDay, onValueChange = { payDay = it.filter(Char::isDigit).take(2) }, label = { Text("День оплаты") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Со ставкой остаток уменьшается на платёж минус проценты за месяц, без ставки — на весь платёж. Остаток можно править руками.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        Debt(
                            id = initial?.id.orEmpty(),
                            name = name.trim(),
                            currency = currency,
                            principalRemaining = remainingMoney ?: Money.zero(currency),
                            monthlyPayment = paymentMoney ?: Money.zero(currency),
                            ratePercent = rateValue,
                            payDay = payDayValue,
                            extraPayment = extraMoney ?: Money.zero(currency),
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun majorText(money: Money): String {
    val major = money.minor / money.currency.minorFactor
    val fraction = money.minor % money.currency.minorFactor
    return if (fraction == 0L) major.toString() else "$major,${fraction.toString().padStart(money.currency.minorUnits, '0')}"
}
