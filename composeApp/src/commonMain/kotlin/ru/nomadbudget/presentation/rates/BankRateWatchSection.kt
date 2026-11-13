package ru.nomadbudget.presentation.rates

import ru.nomadbudget.presentation.format.LocalBaseCurrency
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.time.Clock
import ru.nomadbudget.domain.logic.BankRateAnalyzer
import ru.nomadbudget.domain.logic.RateAssessment
import ru.nomadbudget.domain.logic.RateVerdict
import ru.nomadbudget.domain.model.BankRateCategory
import ru.nomadbudget.domain.model.BankRateWatch
import ru.nomadbudget.domain.model.BankRateWatchDraft
import ru.nomadbudget.domain.model.BankWatchData
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.presentation.charts.LegendEntry
import ru.nomadbudget.presentation.charts.MultiLineChart
import ru.nomadbudget.presentation.components.AddTile
import ru.nomadbudget.presentation.components.ChartPanel
import ru.nomadbudget.presentation.components.ChartPeriod
import ru.nomadbudget.presentation.components.ConfirmDialog
import ru.nomadbudget.presentation.components.DeleteIconButton
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.PrimaryButton
import ru.nomadbudget.presentation.components.SecondaryButton
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.StatTile
import ru.nomadbudget.presentation.components.TileRow
import ru.nomadbudget.presentation.components.Ui
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.plural
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

private val WATCH_PERIODS = listOf(ChartPeriod("1 день", 24), ChartPeriod("7 дней", 24 * 7), ChartPeriod("30 дней", null))
private val HOURS = BankRateWatch.HOURS.toList()
private val TOP_SHARES = listOf(10, 15, 25)
private const val DIGEST_OFF = -1

class BankWatchCallbacks(
    val onCreate: (BankRateWatchDraft) -> Unit,
    val onUpdate: (BankRateWatchDraft) -> Unit,
    val onSetEnabled: (Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onRemoveSubscriber: (String) -> Unit,
)

fun LazyListScope.bankRateWatchItems(state: HomeState, wide: Boolean, callbacks: BankWatchCallbacks, onEdit: () -> Unit) {
    val data = state.bankWatch
    val watch = data.watch
    item(key = "bank-watch-title") {
        SectionTitle("Слежение за курсом банка", hint = if (watch == null) null else watch.lastCheckedAt?.let { "проверено ${DateFormat.dayMonthTime(it)}" } ?: "проверка каждые 15 минут", info = Hints.BANK_WATCH)
    }
    if (watch == null) {
        item(key = "bank-watch-add") { AddTile("Следить за курсом банка", onClick = onEdit) }
        return
    }
    item(key = "bank-watch-tiles") { WatchTiles(data, watch, wide) }
    item(key = "bank-watch-chart") { WatchChart(data, watch) }
    item(key = "bank-watch-telegram") { TelegramCard(data, watch, callbacks, onEdit) }
}

@Composable
private fun WatchTiles(data: BankWatchData, watch: BankRateWatch, wide: Boolean) {
    val colors = AppTheme.colors
    val current = data.latest[BankRateCategory.EXCHANGE]
    val card = data.latest[BankRateCategory.CARD]
    val now = Clock.System.now()
    val range = BankRateAnalyzer.range(data.week, now)
    val unit = MoneyFormat.quoteUnit(current?.buy ?: watch.target ?: 1.0)
    val cbr = current?.cbr
    val assessment = current?.let { BankRateAnalyzer.assess(it, data.week, now, watch.goodPercentile, watch.tradeOpen, watch.tradeClose) }
    val compared = assessment?.takeIf { it.verdict in COMPARED_VERDICTS }
    TileRow(
        wide,
        listOf(
            { m ->
                StatTile(
                    "Курс банка сейчас",
                    current?.let { MoneyFormat.rateText(watch.from, watch.to, it.buy * unit, unit) } ?: "—",
                    listOfNotNull(
                        if (!watch.enabled) "на паузе" else null,
                        assessment?.let(::verdictText),
                        compared?.let(::positionText),
                    ).joinToString(" · ").ifEmpty { "первая проверка в течение 15 минут" },
                    modifier = m,
                    accent = true,
                    progress = compared?.below?.toFloat(),
                )
            },
            { m ->
                val spread = assessment?.spread
                StatTile(
                    "Курс ЦБ",
                    cbr?.let { MoneyFormat.rateText(watch.from, watch.to, it * unit, unit) } ?: "—",
                    listOfNotNull(
                        spread?.let { "разница с ЦБ ${MoneyFormat.formatChange(it)} %" } ?: "курс ЦБ ещё не получен",
                        assessment?.medianSpread?.let { "медиана недели ${MoneyFormat.formatChange(it)} %" },
                    ).joinToString(" · "),
                    modifier = m,
                    subColor = spread?.let { if (it < 0) colors.bad else colors.good },
                )
            },
            { m ->
                val target = watch.target
                StatTile(
                    "Цель",
                    target?.let { MoneyFormat.rateText(watch.from, watch.to, it * unit, unit) } ?: "не задана",
                    listOfNotNull(
                        range?.let { "неделя в часы биржи: мин ${MoneyFormat.formatQuote(it.min * unit)} · макс ${MoneyFormat.formatQuote(it.max * unit)}" }
                            ?: "истории за неделю пока нет",
                        current?.let { cardText(it.buy, card?.buy) },
                    ).joinToString(" · "),
                    modifier = m,
                    progress = current?.let { BankRateAnalyzer.progress(it.buy, target, range) },
                    progressColor = colors.save,
                )
            },
        ),
    )
}

private val COMPARED_VERDICTS = setOf(RateVerdict.GOOD, RateVerdict.NORMAL, RateVerdict.BAD)

private fun verdictText(assessment: RateAssessment): String = when (assessment.verdict) {
    RateVerdict.GOOD -> "выгодно"
    RateVerdict.NORMAL -> "обычно"
    RateVerdict.BAD -> "не меняй"
    RateVerdict.WARMUP -> "мало данных для сравнения"
    RateVerdict.CLOSED -> "биржа закрыта, ночной курс"
}

private fun positionText(assessment: RateAssessment): String =
    if (assessment.verdict == RateVerdict.BAD) "хуже ${MoneyFormat.formatPercentValue(wholePercent(assessment.above))} замеров недели"
    else "лучше ${MoneyFormat.formatPercentValue(wholePercent(assessment.below))} замеров недели"

private fun wholePercent(share: Double): Double = (share * 100).roundToInt().toDouble()

private fun cardText(exchange: Double, card: Double?): String? {
    if (card == null || exchange <= 0.0) return null
    val tenths = ((card / exchange - 1.0) * 1000).roundToInt()
    return when {
        tenths == 0 -> "картой так же"
        tenths > 0 -> "картой выгоднее на ${MoneyFormat.formatPercentValue(tenths / 10.0)}"
        else -> "картой хуже на ${MoneyFormat.formatPercentValue(-tenths / 10.0)}"
    }
}

@Composable
private fun WatchChart(data: BankWatchData, watch: BankRateWatch) {
    val points = remember(data.history) { BankRateAnalyzer.points(data.history) }
    if (points.size < 2) {
        Text("Для графика нужны хотя бы две проверки курса", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val unit = MoneyFormat.quoteUnit(points.last().bank)
    val bank = remember(points, unit) { points.map { it.bank * unit } }
    val cbr = remember(points, unit) { BankRateAnalyzer.lastKnownCbr(points)?.map { it * unit } }
    val labels = remember(points) { points.map { DateFormat.dayMonthTime(it.takenAt) } }
    val bankColor = MaterialTheme.colorScheme.primary
    val cbrColor = AppTheme.colors.muted
    val names = listOfNotNull("банк", cbr?.let { "ЦБ" })
    ChartPanel(
        title = "Банк и ЦБ · ${watch.from.code} → ${watch.to.code}",
        hint = "сколько ${MoneyFormat.perUnitLabel(unit, watch.from, watch.to)}, точка — час; вечером и в выходные у банка ночной курс, в сигналах не учитывается",
        legend = names.zip(listOf(bankColor, cbrColor)).map { (name, color) -> LegendEntry(name, color) },
        totalPoints = points.size,
        periods = WATCH_PERIODS,
        modifier = Modifier.fillMaxWidth(),
        stateKey = "bank-watch-${watch.id}",
    ) { start, visible ->
        MultiLineChart(
            series = listOfNotNull(bank.drop(start), cbr?.drop(start)),
            colors = listOf(bankColor, cbrColor),
            labels = labels.drop(start),
            formatY = MoneyFormat::formatQuote,
            visibleCount = visible,
            names = names,
        )
    }
}

@Composable
private fun TelegramCard(data: BankWatchData, watch: BankRateWatch, callbacks: BankWatchCallbacks, onEdit: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var deleting by remember { mutableStateOf(false) }
    val bot = data.botUsername
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Уведомления в Telegram", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (watch.enabled) "включено" else "на паузе", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = watch.enabled, onCheckedChange = callbacks.onSetEnabled)
            }
            Text(settingsSummary(watch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (bot == null) {
                Text("Бот ещё не подключён", fontWeight = FontWeight.Medium)
                Text(
                    "Нужно создать бота у @BotFather, положить его токен в секреты GitHub под именем TELEGRAM_BOT_TOKEN и один раз запустить проверку курса. После этого здесь появится кнопка подписки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val link = "https://t.me/$bot?start=${watch.linkCode}"
                PrimaryButton(onClick = { uriHandler.openUri(link) }, modifier = Modifier.fillMaxWidth()) { Text("Подписаться в Telegram") }
                Text("Отправь ссылку, кому ещё нужны уведомления: $link", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            if (data.subscribers.isEmpty()) {
                Text("Подписчиков пока нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(plural(data.subscribers.size, "подписчик", "подписчика", "подписчиков"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                data.subscribers.forEachIndexed { index, subscriber ->
                    if (index > 0) HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(subscriber.title, modifier = Modifier.weight(1f))
                        DeleteIconButton(
                            description = "Отключить подписчика",
                            title = "Отключить «${subscriber.title}»?",
                            text = "Бот перестанет присылать этому человеку сообщения. Подписаться снова можно по ссылке из карточки.",
                            confirmText = "Отключить",
                            onConfirm = { callbacks.onRemoveSubscriber(subscriber.id) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(onClick = onEdit) { Text("Настроить") }
                TextButton(onClick = { deleting = true }) { Text("Удалить") }
            }
        }
    }
    if (deleting) {
        ConfirmDialog(
            title = "Удалить слежение?",
            text = "Подписчики перестанут получать уведомления, история курса останется.",
            confirmText = "Удалить",
            onDismiss = { deleting = false },
            onConfirm = {
                callbacks.onDelete()
                deleting = false
            },
        )
    }
}

private fun settingsSummary(watch: BankRateWatch): String = listOfNotNull(
    "верхние ${MoneyFormat.formatPercentValue((100 - watch.goodPercentile).toDouble())} недели",
    "повтор при улучшении на ${MoneyFormat.formatPercentValue(watch.improvePercent)}",
    "биржа ${timeText(watch.tradeOpen)}–${timeText(watch.tradeClose)} МСК",
    watch.digestAt?.let { "сводка в ${timeText(it)} МСК" } ?: "без сводки",
    if (watch.sendsAllDay) "присылать круглосуточно" else "присылать с ${hourText(watch.sendFrom)} до ${hourText(watch.sendTo)}",
    watch.timezone,
).joinToString(" · ")

private fun hourText(hour: Int): String = DateFormat.hourOfDay(hour)

private fun timeText(minute: Int): String = DateFormat.minuteOfDay(minute)

@Composable
fun BankRateWatchDialog(
    initial: BankRateWatch?,
    currencies: List<Currency>,
    suggestedTarget: Double?,
    onDismiss: () -> Unit,
    onConfirm: (BankRateWatchDraft) -> Unit,
) {
    val base = LocalBaseCurrency.current
    val foreign = currencies.firstOrNull { it != base }
    var from by remember { mutableStateOf(initial?.from ?: base) }
    var to by remember { mutableStateOf(initial?.to ?: foreign ?: base) }
    val unit = MoneyFormat.quoteUnit(initial?.target ?: suggestedTarget ?: 1.0)
    var target by remember { mutableStateOf(initial?.target?.let { MoneyFormat.formatQuote(it * unit) }.orEmpty()) }
    var topShare by remember { mutableStateOf(100 - (initial?.goodPercentile ?: BankRateWatch.DEFAULT_GOOD_PERCENTILE)) }
    var improve by remember { mutableStateOf(MoneyFormat.formatTenths(initial?.improvePercent ?: BankRateWatch.DEFAULT_IMPROVE_PERCENT)) }
    var tradeOpen by remember { mutableStateOf(initial?.tradeOpen ?: BankRateWatch.DEFAULT_TRADE_OPEN) }
    var tradeClose by remember { mutableStateOf(initial?.tradeClose ?: BankRateWatch.DEFAULT_TRADE_CLOSE) }
    var sendFrom by remember { mutableStateOf(initial?.sendFrom ?: BankRateWatch.DEFAULT_QUIET_TO) }
    var sendTo by remember { mutableStateOf(initial?.sendTo ?: BankRateWatch.DEFAULT_QUIET_FROM) }
    var digest by remember { mutableStateOf(if (initial == null) BankRateWatch.DEFAULT_DIGEST_AT else initial.digestAt ?: DIGEST_OFF) }
    var timezone by remember { mutableStateOf(initial?.timezone ?: BankRateWatch.DEFAULT_TIMEZONE) }

    val targetValue = parseDecimal(target)?.let { it / unit }
    val improveValue = parseDecimal(improve)
    val topShares = (TOP_SHARES + topShare).distinct().sorted()
    val digestTimes = BankRateWatch.TIME_STEPS.filter { BankRateWatch.inSendWindow(it, quietFrom = sendTo, quietTo = sendFrom) }
    val digestValid = digest == DIGEST_OFF || digest in digestTimes
    val valid = from != to && (target.isBlank() || (targetValue != null && targetValue > 0.0)) && improveValue != null && improveValue > 0.0 &&
        BankRateWatch.validTradeMinutes(tradeOpen, tradeClose) && digestValid && timezone.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Следить за курсом банка" else "Слежение за курсом") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown("Из валюты", currencies, from, Currency::code, { from = it }, modifier = Modifier.weight(1f))
                    Dropdown("В валюту", currencies, to, Currency::code, { to = it }, modifier = Modifier.weight(1f))
                }
                Dropdown("Строгость: верхние X % недели", topShares, topShare, { MoneyFormat.formatPercentValue(it.toDouble()) }, { topShare = it })
                OutlinedTextField(
                    value = improve,
                    onValueChange = { improve = it },
                    label = { Text("Повтор при улучшении на, %") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown("Часы биржи с, МСК", BankRateWatch.TIME_STEPS, tradeOpen, ::timeText, { tradeOpen = it }, modifier = Modifier.weight(1f))
                    Dropdown("по", BankRateWatch.CLOSE_STEPS, tradeClose, ::timeText, { tradeClose = it }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Всегда сообщать, если курс ≥, ${MoneyFormat.perUnitLabel(unit, from, to)}") },
                    placeholder = { Text(suggestedTarget?.let { "максимум за неделю ${MoneyFormat.formatQuote(it * unit)}" } ?: "необязательно") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown("Присылать с", HOURS, sendFrom, ::hourText, { sendFrom = it }, modifier = Modifier.weight(1f))
                    Dropdown("до", HOURS, sendTo, ::hourText, { sendTo = it }, modifier = Modifier.weight(1f))
                }
                Dropdown("Сводка каждый день", listOf(DIGEST_OFF) + digestTimes, digest, { if (it == DIGEST_OFF) "выключена" else timeText(it) }, { digest = it })
                OutlinedTextField(
                    value = timezone,
                    onValueChange = { timezone = it.trim() },
                    label = { Text("Часовой пояс") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Выгодно — курс обмена в верхних ${MoneyFormat.formatPercentValue(topShare.toDouble())} замеров недели и разница с ЦБ уже обычной; зеркально приходит «сейчас не меняй». Считаются только часы биржи в будни: вечером и в выходные у банка ночной курс. На одно окно — одно сообщение, повтор только при улучшении. Пока курс не ниже заданного, сообщение приходит при каждом его изменении. Сообщения приходят с ${hourText(sendFrom)} до ${hourText(sendTo)}; остальное попадёт одной строкой в ежедневную сводку. Одинаковые часы — присылать круглосуточно.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                onClick = {
                    onConfirm(
                        BankRateWatchDraft(
                            from = from,
                            to = to,
                            category = BankRateCategory.EXCHANGE,
                            target = targetValue,
                            goodPercentile = 100 - topShare,
                            improvePercent = improveValue ?: BankRateWatch.DEFAULT_IMPROVE_PERCENT,
                            tradeOpen = tradeOpen,
                            tradeClose = tradeClose,
                            digestAt = digest.takeIf { it != DIGEST_OFF },
                            quietFrom = sendTo,
                            quietTo = sendFrom,
                            timezone = timezone,
                            provider = initial?.provider ?: BankRateWatch.DEFAULT_PROVIDER,
                        ),
                    )
                },
                enabled = valid,
            ) { Text(if (initial == null) "Следить" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun parseDecimal(text: String): Double? = text.replace(" ", "").replace(" ", "").replace(',', '.').trim().toDoubleOrNull()
