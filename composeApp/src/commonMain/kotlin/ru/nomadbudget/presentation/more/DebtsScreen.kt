package ru.nomadbudget.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.domain.logic.DebtObligation
import ru.nomadbudget.domain.logic.ObligationStatus
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.DebtCloseReason
import ru.nomadbudget.domain.model.DebtHistory
import ru.nomadbudget.domain.model.DebtTotals
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.components.AddTile
import ru.nomadbudget.presentation.components.AppIcons
import ru.nomadbudget.presentation.components.ArchiveCard
import ru.nomadbudget.presentation.components.ArchivedItem
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.ConfirmDialog
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.PillTone
import ru.nomadbudget.presentation.components.PrimaryButton
import ru.nomadbudget.presentation.components.SecondaryButton
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.StatTile
import ru.nomadbudget.presentation.components.StatusPill
import ru.nomadbudget.presentation.components.TileRow
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.format.plural
import ru.nomadbudget.presentation.home.EarlyRepaymentDraft
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

@Composable
fun DebtsScreen(
    state: HomeState,
    onBack: (() -> Unit)?,
    onSave: (Debt) -> Unit,
    onClose: (String) -> Unit,
    onReopen: (String) -> Unit = {},
    onClosePaidOff: (String) -> Unit = {},
    onRepayEarly: (EarlyRepaymentDraft) -> Unit = {},
    onPay: (String) -> Unit,
    twoColumns: Boolean = false,
) {
    var editing by remember { mutableStateOf<Debt?>(null) }
    var creating by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<Debt?>(null) }
    var closingPaidOff by remember { mutableStateOf<Debt?>(null) }
    var repaying by remember { mutableStateOf<Debt?>(null) }
    val month = state.month
    val obligations = month.debts.sortedWith(compareBy<DebtObligation> { it.status == ObligationStatus.PAID }.thenBy(nullsLast()) { it.due })

    CenteredLazyColumn {
        if (onBack != null) {
            item { SubScreenHeader("Кредиты", onBack) }
        }
        if (obligations.isNotEmpty()) {
            item { DebtsSummary(state, twoColumns) }
            item { SectionTitle("Платежи месяца", hint = DateFormat.periodRange(state.period), info = Hints.DEBTS) }
            item { PaymentsTimeline(obligations, state.period, state.today) }
            item { SectionTitle("Открытые", hint = "сначала ближайшие") }
            val perRow = if (twoColumns) 2 else 1
            items(obligations.filterNot { it.debt.isClosed }.chunked(perRow), key = { row -> row.joinToString { it.debt.id } }) { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    row.forEach { obligation ->
                        Box(modifier = Modifier.weight(1f)) {
                            DebtCard(
                                obligation = obligation,
                                today = state.today,
                                onEdit = { editing = obligation.debt },
                                onClose = { closing = obligation.debt },
                                onClosePaidOff = { closingPaidOff = obligation.debt },
                                onRepayEarly = { repaying = obligation.debt },
                                onPay = { onPay(obligation.debt.id) },
                            )
                        }
                    }
                    if (row.size < perRow) Box(modifier = Modifier.weight(1f)) {}
                }
            }
        } else {
            item { EmptyHint("Кредитов пока нет. Добавь первый: его платёж сам попадёт в обязательные платежи месяца.") }
        }
        item {
            AddTile("Добавить кредит", onClick = { creating = true })
        }
        val closed = state.debts.filter { it.isClosed }
        if (closed.isNotEmpty()) {
            item { SectionTitle("Закрытые", hint = "в план и итоги не входят") }
            val payments = state.transactions.filterIsInstance<Transaction.Expense>()
            item {
                ArchiveCard(
                    closed.map { debt -> ArchivedItem(debt.id, debt.name, archiveSubtitle(debt, DebtHistory.totals(debt, payments))) },
                    restoreText = "Открыть снова",
                    onRestore = onReopen,
                )
            }
        }
    }

    if (creating) {
        DebtDialog(initial = null, currencies = state.userCurrencies, onDismiss = { creating = false }) {
            onSave(it)
            creating = false
        }
    }
    editing?.let { debt ->
        DebtDialog(initial = debt, currencies = state.userCurrencies, onDismiss = { editing = null }) {
            onSave(it)
            editing = null
        }
    }
    repaying?.let { debt ->
        EarlyRepaymentDialog(
            debt = debt,
            accounts = state.activeAccounts,
            balances = state.balances,
            today = state.today,
            saving = state.saving,
            onDismiss = { repaying = null },
            onConfirm = {
                onRepayEarly(it)
                repaying = null
            },
        )
    }
    closingPaidOff?.let { debt ->
        val tail = debt.principalRemaining
        ConfirmDialog(
            title = "Закрыть «${debt.name}» как погашенный?",
            text = if (tail.minor > 0L) {
                "Осталось ${MoneyFormat.format(tail)}: это расхождение с банком в процентах. Остаток спишется — это не доход и не расход, бюджет не изменится. Кредит уйдёт в архив; «Открыть снова» вернёт и остаток."
            } else {
                "Кредит уйдёт в архив, платежи по нему останутся в истории. «Открыть снова» вернёт его."
            },
            confirmText = if (tail.minor > 0L) "Закрыть и списать" else "Закрыть",
            onDismiss = { closingPaidOff = null },
            onConfirm = {
                onClosePaidOff(debt.id)
                closingPaidOff = null
            },
        )
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
private fun DebtsSummary(state: HomeState, wide: Boolean) {
    val month = state.month
    val owed = state.capital.debt
    val forecasts = state.openDebts.map { debt -> DebtCalculator.forecast(debt, debt.plannedPayment)?.months }
    val lastClose = if (forecasts.any { it == null }) null else forecasts.filterNotNull().maxOrNull()
    val paid = month.debtsPaid
    TileRow(
        wide,
        listOf(
            { m -> StatTile("Остаток долга", MoneyFormat.format(owed, false), "тело по ${plural(state.openDebts.size, "кредиту", "кредитам", "кредитам")}", modifier = m, accent = true) },
            { m ->
                StatTile(
                    "Платежи в этом месяце",
                    MoneyFormat.format(month.debtsPlanned, false),
                    "оплачено $paid из ${month.debts.size}",
                    modifier = m,
                    progress = if (month.debts.isEmpty()) null else paid.toFloat() / month.debts.size,
                    progressColor = AppTheme.colors.good,
                )
            },
            { m ->
                StatTile(
                    "Последний закроется",
                    lastClose?.let { DateFormat.monthYear(state.today.plus(it, DateTimeUnit.MONTH)) } ?: "—",
                    lastClose?.let { "через $it мес. при текущих платежах" } ?: if (forecasts.isEmpty()) "кредитов нет" else "по одному из кредитов платёж не покрывает проценты",
                    modifier = m,
                )
            },
        ),
    )
}

@Composable
private fun PaymentsTimeline(obligations: List<DebtObligation>, period: Period, today: LocalDate) {
    val colors = AppTheme.colors
    val length = period.lengthDays
    fun toneOf(status: ObligationStatus): Color = when (status) {
        ObligationStatus.PAID -> colors.good
        ObligationStatus.OVERDUE -> colors.bad
        ObligationStatus.SOON -> colors.warning
        ObligationStatus.UPCOMING -> colors.muted
    }
    val byDate = obligations
        .mapNotNull { o -> o.due?.let { it to o } }
        .groupBy({ it.first }, { it.second })
        .entries
        .sortedBy { it.key }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                val width = maxWidth
                fun x(date: LocalDate): Dp {
                    val index = (period.dayNumber(date) - 1).coerceIn(0, length - 1)
                    return width * (index.toFloat() / (length - 1).coerceAtLeast(1))
                }
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).offset(y = 26.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                if (today in period) {
                    val tx = x(today)
                    Box(modifier = Modifier.width(width).height(4.dp).offset(y = 26.dp)) {
                        Box(modifier = Modifier.width(tx).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
                    }
                    Box(modifier = Modifier.offset(x = (tx - 1.dp).coerceAtLeast(0.dp), y = 16.dp).width(2.dp).height(24.dp).background(MaterialTheme.colorScheme.onSurface))
                    Text("сегодня", style = MaterialTheme.typography.labelSmall, modifier = Modifier.offset(x = (tx - 22.dp).coerceIn(0.dp, width - 48.dp), y = 40.dp))
                }
                byDate.forEach { entry ->
                    val date = entry.key
                    val list = entry.value
                    val dx = x(date)
                    val worst = list.map { it.status }.filter { it != ObligationStatus.PAID }.minByOrNull { it.ordinal } ?: ObligationStatus.PAID
                    val tone = toneOf(worst)
                    Box(modifier = Modifier.offset(x = (dx - 7.dp).coerceIn(0.dp, width - 14.dp), y = 21.dp).size(14.dp).clip(CircleShape).background(tone))
                    Text(
                        "${date.day}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tone,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.offset(x = (dx - 12.dp).coerceIn(0.dp, width - 24.dp), y = 0.dp).width(24.dp),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(DateFormat.dayMonth(period.start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormat.dayMonth(period.lastDay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    "оплачен" to ObligationStatus.PAID,
                    "скоро" to ObligationStatus.SOON,
                    "просрочен" to ObligationStatus.OVERDUE,
                    "впереди" to ObligationStatus.UPCOMING,
                ).forEach { (label, status) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(toneOf(status)))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider()
            obligations.sortedWith(compareBy(nullsLast()) { it.due }).forEach { o ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(toneOf(o.status)))
                    Text(o.due?.let { DateFormat.dayMonth(it) } ?: "без даты", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                    Text(o.debt.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(MoneyFormat.format(o.planned, false), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun DebtCard(
    obligation: DebtObligation,
    today: LocalDate,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onClosePaidOff: () -> Unit,
    onRepayEarly: () -> Unit,
    onPay: () -> Unit,
) {
    val debt = obligation.debt
    var menu by remember { mutableStateOf(false) }
    val forecast = DebtCalculator.forecast(debt, debt.plannedPayment)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(debt.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusPill(statusText(obligation), statusTone(obligation.status))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVertical, contentDescription = "Действия с кредитом") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Изменить") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Закрыть кредит") }, onClick = { menu = false; onClose() })
                    }
                }
            }
            Column(modifier = Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Осталось", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(MoneyFormat.format(debt.principalRemaining, false), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        "платёж ${MoneyFormat.format(debt.monthlyPayment, false)}",
                        debt.extraPayment.takeUnless { it.isZero }?.let { "досрочно +${MoneyFormat.format(it, false)}" },
                        debt.ratePercent?.let { MoneyFormat.formatPercentValue(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    when {
                        forecast == null -> "платёж не покрывает проценты: долг не уменьшается"
                        forecast.totalInterest.isZero -> "закроется ${DateFormat.byMonthYear(today.plus(forecast.months, DateTimeUnit.MONTH))}"
                        else -> "закроется ${DateFormat.byMonthYear(today.plus(forecast.months, DateTimeUnit.MONTH))} · переплата ≈ ${MoneyFormat.format(forecast.totalInterest, false)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (forecast == null) AppTheme.colors.bad else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (debt.lessThanOnePayment) {
                    Text(
                        if (debt.principalRemaining.minor > 0L) "Осталось меньше одного платежа — похоже, кредит погашен." else "Остаток ноль — кредит погашен.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(onClick = onClosePaidOff, modifier = Modifier.fillMaxWidth()) {
                        Text(if (debt.principalRemaining.minor > 0L) "Закрыть, списать ${MoneyFormat.format(debt.principalRemaining, false)}" else "Закрыть кредит")
                    }
                }
                val early = obligation.earlyPaid.takeUnless { it.isZero }?.let { "досрочно ${MoneyFormat.format(it, false)}" }
                when {
                    obligation.paidOff -> Text(
                        listOfNotNull("погашен досрочно", early).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.good,
                    )
                    obligation.status == ObligationStatus.PAID -> Text(
                        listOfNotNull("платёж ${MoneyFormat.format(obligation.regularPaid, false)} внесён", early).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.good,
                    )
                    else -> {
                        early?.let { Text("в этом месяце $it", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.good) }
                        PrimaryButton(onClick = onPay, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text("Внести платёж ${MoneyFormat.format(obligation.planned, false)}")
                        }
                    }
                }
                if (!debt.lessThanOnePayment && !obligation.paidOff) {
                    SecondaryButton(onClick = onRepayEarly, modifier = Modifier.fillMaxWidth()) { Text("Досрочное погашение") }
                }
            }
        }
    }
}

private val DAYS_OF_MONTH = 1..31

private fun statusText(o: DebtObligation): String = when (o.status) {
    ObligationStatus.PAID -> if (o.paidOff) "погашен" else "оплачен"
    ObligationStatus.OVERDUE -> "просрочен"
    ObligationStatus.SOON, ObligationStatus.UPCOMING -> o.due?.let { "к ${it.day} ${DateFormat.monthGenitive(it)}" } ?: "к оплате"
}

private fun statusTone(status: ObligationStatus): PillTone = when (status) {
    ObligationStatus.PAID -> PillTone.GOOD
    ObligationStatus.OVERDUE -> PillTone.BAD
    ObligationStatus.SOON -> PillTone.WARNING
    ObligationStatus.UPCOMING -> PillTone.NEUTRAL
}

@Composable
private fun DebtDialog(initial: Debt?, currencies: List<Currency>, onDismiss: () -> Unit, onConfirm: (Debt) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var currency by remember { mutableStateOf(initial?.currency ?: Currency.BASE) }
    var remaining by remember { mutableStateOf(initial?.principalRemaining?.let(MoneyFormat::editText).orEmpty()) }
    var payment by remember { mutableStateOf(initial?.monthlyPayment?.let(MoneyFormat::editText).orEmpty()) }
    var extra by remember { mutableStateOf(initial?.extraPayment?.takeUnless { it.isZero }?.let(MoneyFormat::editText).orEmpty()) }
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
                    value = remaining, onValueChange = { remaining = ThousandsVisualTransformation.sanitize(it, currency.minorUnits) },
                    label = { Text("Остаток долга, ${currency.code}") }, singleLine = true, visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = payment, onValueChange = { payment = ThousandsVisualTransformation.sanitize(it, currency.minorUnits) },
                    label = { Text("Платёж в месяц, ${currency.code}") }, singleLine = true, visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = extra, onValueChange = { extra = ThousandsVisualTransformation.sanitize(it, currency.minorUnits) },
                    label = { Text("Досрочное погашение в месяц, ${currency.code}") }, singleLine = true, visualTransformation = transformation,
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
                    "Платёж и досрочка попадают в обязательные платежи месяца сами. Со ставкой остаток уменьшается на платёж минус проценты за месяц, без ставки — на весь платёж.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            PrimaryButton(
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

private fun archiveSubtitle(debt: Debt, totals: DebtTotals): String = listOfNotNull(
    listOfNotNull(
        (debt.openedOn ?: totals.firstPayment)?.let(DateFormat::monthYear),
        debt.closedOn?.let(DateFormat::monthYear),
    ).joinToString(" — ").takeIf { it.isNotEmpty() },
    when (debt.closeReason) {
        DebtCloseReason.EARLY -> "погашен досрочно"
        DebtCloseReason.PAID_OFF -> "выплачен"
        DebtCloseReason.MANUAL, null -> "закрыт"
    },
    totals.paid.takeUnless { it.isZero }?.let { "внесено ${MoneyFormat.format(it, false)}" },
    totals.principal?.let { "тело ${MoneyFormat.format(it, false)}" },
    totals.interest?.let { "проценты ≈ ${MoneyFormat.format(it, false)}" },
    debt.writtenOff.takeUnless { it.isZero }?.let { "списано ${MoneyFormat.format(it, false)}" },
).joinToString(" · ")

