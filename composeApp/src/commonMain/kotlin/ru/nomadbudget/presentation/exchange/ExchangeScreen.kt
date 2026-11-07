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
import ru.nomadbudget.presentation.components.DateField
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
import ru.nomadbudget.presentation.components.AccountsLine
import ru.nomadbudget.presentation.components.ArrowGlyph
import ru.nomadbudget.presentation.more.SubScreenHeader
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.charts.LineChart
import ru.nomadbudget.presentation.components.currencyColor
import ru.nomadbudget.presentation.components.ChartPanel
import ru.nomadbudget.presentation.components.ChartPeriod
import ru.nomadbudget.domain.logic.RatePairs
import ru.nomadbudget.presentation.charts.MultiLineChart
import ru.nomadbudget.presentation.charts.LegendEntry
import ru.nomadbudget.presentation.components.Ui

@Composable
fun ExchangeScreen(state: HomeState, onSubmit: (ExchangeDraft) -> Unit, onDelete: (String) -> Unit, onBack: (() -> Unit)? = null) {
    CenteredLazyColumn(bottom = 96.dp) {
        if (onBack != null) {
            item { SubScreenHeader("Обмен валюты", onBack) }
        }
        item { ExchangeForm(state, onSubmit) }
        val charts = exchangeCharts(state)
        items(charts.size) { index ->
            val chart = charts[index]
            if (chart.points.size >= MIN_EXCHANGES) ExchangeRateChart(chart, state) else SingleExchangeCard(chart)
        }
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
                    onValueChange = { givenText = ThousandsVisualTransformation.sanitize(it, from?.currency?.minorUnits ?: 2) },
                    label = { Text("Отдал${from?.let { ", ${it.currency.code}" }.orEmpty()}") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = receivedText,
                    onValueChange = { receivedText = ThousandsVisualTransformation.sanitize(it, to?.currency?.minorUnits ?: 2) },
                    label = { Text("Получил${to?.let { ", ${it.currency.code}" }.orEmpty()}") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            RateHints(state, from, to, given, received, sameCurrency, sameAccount)
            DateField(date = date, today = state.today, onChange = { date = it })
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
            val referenceQuote = ExchangeAnalyzer.pairQuote(from.currency, to.currency, reference)
            KeyValueRow("Курс по таблице", ExchangeAnalyzer.quoteText(referenceQuote, MoneyFormat::formatQuote))
            if (given != null && received != null) {
                val effective = received.toMajor() / given.toMajor()
                val spread = (reference - effective) / reference * 100
                val effectiveQuote = if (referenceQuote.unit == from.currency) ru.nomadbudget.domain.logic.RateQuote(from.currency, to.currency, effective) else ru.nomadbudget.domain.logic.RateQuote(to.currency, from.currency, 1.0 / effective)
                KeyValueRow("Твой курс", ExchangeAnalyzer.quoteText(effectiveQuote, MoneyFormat::formatQuote), emphasize = true)
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
                    ArrowGlyph(MaterialTheme.colorScheme.onSurface)
                    CurrencyAmount(MoneyFormat.format(exchange.received), exchange.received.currency, state, MaterialTheme.typography.bodyMedium, FontWeight.SemiBold)
                }
                AccountsLine(
                    state,
                    fromId = exchange.fromAccountId,
                    toId = exchange.toAccountId,
                    prefix = DateFormat.dayMonth(exchange.date),
                    note = exchange.note,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val quote = ExchangeAnalyzer.quote(exchange)
                Text(ExchangeAnalyzer.quoteText(quote, MoneyFormat::formatQuote), fontWeight = FontWeight.SemiBold)
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

private data class ExchangePoint(val date: kotlinx.datetime.LocalDate, val mine: Double, val table: Double?)

private data class ExchangeChartData(val unit: Currency, val price: Currency, val points: List<ExchangePoint>)

private fun exchangeCharts(state: HomeState): List<ExchangeChartData> =
    state.exchanges
        .groupBy { setOf(it.given.currency, it.received.currency) }
        .values
        .sortedByDescending { it.size }
        .map { list ->
            val sorted = list.sortedBy { it.date }
            val orientation = ExchangeAnalyzer.quote(sorted.last())
            val market = RatePairs.series(orientation.unit, orientation.price, state.rateHistory)
            val current = state.rates.cross(orientation.unit, orientation.price)
            ExchangeChartData(
                unit = orientation.unit,
                price = orientation.price,
                points = sorted.map { tx ->
                    val q = ExchangeAnalyzer.quote(tx)
                    ExchangePoint(
                        date = tx.date,
                        mine = if (q.unit == orientation.unit) q.value else 1.0 / q.value,
                        table = market.lastOrNull { it.date <= tx.date }?.value ?: current,
                    )
                },
            )
        }

@Composable
private fun ExchangeRateChart(data: ExchangeChartData, state: HomeState) {
    val mineColor = currencyColor(if (data.unit == Currency.BASE) data.price else data.unit, state)
    val tableColor = MaterialTheme.colorScheme.outline
    val withTable = data.points.all { it.table != null }
    ChartPanel(
        title = "1 ${data.unit.symbol} → ${data.price.symbol}: твой курс в обменах",
        hint = "сколько ${data.price.code} ты получал за 1 ${data.unit.code}" + if (withTable) "; выше линии таблицы — обменял выгодно" else "",
        legend = listOfNotNull(LegendEntry("твой курс", mineColor), LegendEntry("по таблице в тот день", tableColor).takeIf { withTable }),
        totalPoints = data.points.size,
        periods = EXCHANGE_PERIODS,
        stateKey = data.unit.code + data.price.code,
    ) { from, visible ->
        val slice = data.points.drop(from)
        val series = listOfNotNull(slice.map { it.mine }, slice.map { it.table ?: it.mine }.takeIf { withTable })
        MultiLineChart(
            series = series,
            colors = listOf(mineColor, tableColor).take(series.size),
            labels = slice.map { DateFormat.dayMonth(it.date) },
            formatY = MoneyFormat::formatQuote,
            visibleCount = visible,
        )
    }
}

private const val MIN_EXCHANGES = 2

@Composable
private fun SingleExchangeCard(data: ExchangeChartData) {
    val point = data.points.single()
    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("1 ${data.unit.symbol} → ${data.price.symbol}: один обмен", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${DateFormat.dayMonth(point.date)}: твой курс ${MoneyFormat.formatQuote(point.mine)}" +
                    (point.table?.let { " · по таблице ${MoneyFormat.formatQuote(it)}" } ?: "") +
                    ". График появится после второго обмена.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val EXCHANGE_PERIODS = listOf(ChartPeriod("10 обменов", 10), ChartPeriod("все", null))
