package ru.nomadbudget.presentation.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.EarlyRepaymentMode
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.presentation.components.AppModal
import ru.nomadbudget.presentation.components.DateField
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.EarlyRepaymentDraft
import ru.nomadbudget.presentation.theme.AppTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarlyRepaymentDialog(
    debt: Debt,
    accounts: List<Account>,
    balances: Map<String, Money>,
    today: LocalDate,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (EarlyRepaymentDraft) -> Unit,
) {
    val currency = debt.currency
    val candidates = accounts.filter { !it.isArchived && it.currency == currency }
    var amountText by remember { mutableStateOf("") }
    val amount = MoneyFormat.parse(amountText, currency)
    var account by remember {
        mutableStateOf(
            candidates.filter { it.role == AccountRole.CUSHION }.maxByOrNull { balances[it.id]?.minor ?: 0L } ?: candidates.firstOrNull(),
        )
    }
    var date by remember { mutableStateOf(today) }
    var modeChosen by remember { mutableStateOf<EarlyRepaymentMode?>(null) }
    val coversAll = amount != null && debt.principalRemaining.minor - amount.minor < debt.monthlyPayment.minor
    val mode = modeChosen ?: if (coversAll) EarlyRepaymentMode.PAY_OFF else EarlyRepaymentMode.REDUCE_PAYMENT
    val suggested = amount?.let { DebtCalculator.suggestedPayment(debt, it) }
    var paymentText by remember { mutableStateOf<String?>(null) }
    val paymentShown = paymentText ?: suggested?.let(MoneyFormat::editText).orEmpty()
    val newPayment = MoneyFormat.parse(paymentShown, currency)
    var effectiveFrom by remember { mutableStateOf<LocalDate?>(null) }
    val from = effectiveFrom ?: nextDueDate(debt, date)
    val left = amount?.let { debt.principalRemaining - it }
    val transformation = remember { ThousandsVisualTransformation() }
    val valid = amount != null && amount.minor > 0L && account != null &&
        (mode != EarlyRepaymentMode.REDUCE_PAYMENT || newPayment != null && newPayment.minor > 0L)

    AppModal(
        title = "Досрочно: ${debt.name}",
        onDismiss = onDismiss,
        confirmText = "Записать",
        confirmEnabled = valid && !saving,
        onConfirm = {
            val chosen = account ?: return@AppModal
            val money = amount ?: return@AppModal
            onConfirm(
                EarlyRepaymentDraft(
                    debtId = debt.id,
                    accountId = chosen.id,
                    amount = money,
                    date = date,
                    mode = mode,
                    newPayment = newPayment.takeIf { mode == EarlyRepaymentMode.REDUCE_PAYMENT },
                    effectiveFrom = from.takeIf { mode == EarlyRepaymentMode.REDUCE_PAYMENT },
                ),
            )
        },
    ) {
        Text(
            "Осталось ${MoneyFormat.format(debt.principalRemaining, false)} · платёж ${MoneyFormat.format(debt.monthlyPayment, false)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = ThousandsVisualTransformation.sanitize(it, currency.minorUnits) },
            label = { Text("Сумма досрочки, ${currency.code}") },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (candidates.isEmpty()) {
            Text("Нет счёта в ${currency.code}: досрочку можно внести только со счёта в валюте кредита.", color = AppTheme.colors.bad, style = MaterialTheme.typography.bodySmall)
        } else {
            Dropdown(
                label = "Откуда",
                items = candidates,
                selected = account,
                itemLabel = { "${it.name} · ${MoneyFormat.format(balances[it.id] ?: Money.zero(it.currency), false)}" },
                onSelect = { account = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("Когда", style = MaterialTheme.typography.labelMedium)
        DateField(date = date, today = today, onChange = { date = it })
        Text("Что сделал банк", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MODES.forEach { (value, label) ->
                FilterChip(selected = mode == value, onClick = { modeChosen = value }, label = { Text(label) })
            }
        }
        when (mode) {
            EarlyRepaymentMode.REDUCE_PAYMENT -> {
                OutlinedTextField(
                    value = paymentShown,
                    onValueChange = { paymentText = ThousandsVisualTransformation.sanitize(it, currency.minorUnits) },
                    label = { Text("Новый платёж, ${currency.code}") },
                    supportingText = {
                        Text(
                            suggested?.let { "по расчёту ≈ ${MoneyFormat.format(it, false)}; впиши точную цифру из нового графика банка" }
                                ?: "впиши цифру из нового графика банка",
                        )
                    },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("С какого платежа", style = MaterialTheme.typography.labelMedium)
                DateField(date = from, today = today, onChange = { effectiveFrom = it }, allowFuture = true)
                Text(
                    "В этом месяце план останется прежним, новый платёж — с ${DateFormat.dayMonth(from)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EarlyRepaymentMode.REDUCE_TERM -> {
                val before = DebtCalculator.forecast(debt, debt.monthlyPayment)
                val after = left?.takeIf { it.minor > 0L }?.let { DebtCalculator.forecast(debt.copy(principalRemaining = it), debt.monthlyPayment) }
                Text(
                    if (before != null && after != null) {
                        "Платёж прежний, закроется ${DateFormat.byMonthYear(today.plus(after.months, DateTimeUnit.MONTH))} вместо ${DateFormat.byMonthYear(today.plus(before.months, DateTimeUnit.MONTH))}, переплата меньше на ≈ ${MoneyFormat.format(before.totalInterest - after.totalInterest, false)}."
                    } else {
                        "Платёж прежний, срок сократится."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EarlyRepaymentMode.PAY_OFF -> Text(
                when {
                    left == null -> "Кредит закроется и уйдёт в архив."
                    left.minor > debt.monthlyPayment.minor -> "После досрочки останется ${MoneyFormat.format(left, false)} — больше одного платежа. Точно погашен полностью? Остаток спишется."
                    left.minor > 0L -> "Остаток ${MoneyFormat.format(left, false)} — расхождение с банком в процентах — спишется. Кредит уйдёт в архив."
                    else -> "Кредит закроется и уйдёт в архив."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (left != null && left.minor > debt.monthlyPayment.minor) AppTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val MODES = listOf(
    EarlyRepaymentMode.REDUCE_PAYMENT to "Уменьшить платёж",
    EarlyRepaymentMode.REDUCE_TERM to "Уменьшить срок",
    EarlyRepaymentMode.PAY_OFF to "Погасить полностью",
)

private fun nextDueDate(debt: Debt, after: LocalDate): LocalDate {
    val day = debt.payDay ?: return after.plus(1, DateTimeUnit.MONTH)
    var month = LocalDate(after.year, after.month, 1)
    repeat(3) {
        val next = month.plus(1, DateTimeUnit.MONTH)
        val length = (next.toEpochDays() - month.toEpochDays()).toInt()
        val candidate = LocalDate(month.year, month.month, day.coerceIn(1, length))
        if (candidate > after) return candidate
        month = next
    }
    return after.plus(1, DateTimeUnit.MONTH)
}
