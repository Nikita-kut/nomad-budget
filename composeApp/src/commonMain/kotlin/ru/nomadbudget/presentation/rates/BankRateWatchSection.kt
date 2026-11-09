package ru.nomadbudget.presentation.rates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import ru.nomadbudget.domain.logic.BankRateAnalyzer
import ru.nomadbudget.domain.model.BankRateCategory
import ru.nomadbudget.domain.model.BankRateWatch
import ru.nomadbudget.domain.model.BankRateWatchDraft
import ru.nomadbudget.domain.model.BankWatchData
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.presentation.charts.LegendEntry
import ru.nomadbudget.presentation.charts.MultiLineChart
import ru.nomadbudget.presentation.components.AddTile
import ru.nomadbudget.presentation.components.AppIcons
import ru.nomadbudget.presentation.components.ChartPanel
import ru.nomadbudget.presentation.components.ChartPeriod
import ru.nomadbudget.presentation.components.ConfirmDialog
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
import kotlin.time.Clock

private val WATCH_PERIODS = listOf(ChartPeriod("1 день", 24), ChartPeriod("7 дней", 24 * 7), ChartPeriod("30 дней", null))
private val HOURS = BankRateWatch.HOURS.toList()
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
        SectionTitle("Слежение за курсом банка", hint = if (watch == null) null else "проверка каждые 15 минут", info = Hints.BANK_WATCH)
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
    val exchange = data.latest[BankRateCategory.EXCHANGE]
    val card = data.latest[BankRateCategory.CARD]
    val current = data.latest[watch.category] ?: exchange
    val now = Clock.System.now()
    val range = BankRateAnalyzer.range(data.history, now)
    val unit = MoneyFormat.quoteUnit(current?.buy ?: watch.target ?: 1.0)
    val cbr = current?.cbr
    val spread = current?.let { BankRateAnalyzer.spread(it.buy, cbr) }
    val checkedAt = watch.lastCheckedAt ?: current?.takenAt
    TileRow(
        wide,
        listOf(
            { m ->
                StatTile(
                    "Курс банка сейчас",
                    current?.let { MoneyFormat.rateText(watch.from, watch.to, it.buy * unit, unit) } ?: "—",
                    listOfNotNull(
                        exchange?.let { "обмен ${MoneyFormat.formatQuote(it.buy * unit)}" },
                        card?.let { "карта ${MoneyFormat.formatQuote(it.buy * unit)}" },
                        if (!watch.enabled) "на паузе" else checkedAt?.let { "проверено ${DateFormat.dayMonthTime(it)}" },
                    ).joinToString(" · ").ifEmpty { "первая проверка в течение 15 минут" },
                    modifier = m,
                    accent = true,
                )
            },
            { m ->
                StatTile(
                    "Курс ЦБ",
                    cbr?.let { MoneyFormat.rateText(watch.from, watch.to, it * unit, unit) } ?: "—",
                    spread?.let { "разница с ЦБ ${MoneyFormat.formatChange(it)} %" } ?: "курс ЦБ ещё не получен",
                    modifier = m,
                    subColor = spread?.let { if (it < 0) colors.bad else colors.good },
                )
            },
            { m ->
                val target = watch.target
                StatTile(
                    "Цель",
                    target?.let { MoneyFormat.rateText(watch.from, watch.to, it * unit, unit) } ?: "не задана",
                    range?.let { "за ${plural(BankRateAnalyzer.RANGE_DAYS, "день", "дня", "дней")}: мин ${MoneyFormat.formatQuote(it.min * unit)} · макс ${MoneyFormat.formatQuote(it.max * unit)}" }
                        ?: "истории за неделю пока нет",
                    modifier = m,
                    progress = current?.let { BankRateAnalyzer.progress(it.buy, target, range) },
                    progressColor = colors.save,
                )
            },
        ),
    )
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
        hint = "сколько ${MoneyFormat.perUnitLabel(unit, watch.from, watch.to)}, точка — час",
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
                        IconButton(onClick = { callbacks.onRemoveSubscriber(subscriber.id) }) {
                            Icon(AppIcons.Trash, contentDescription = "Отключить подписчика", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
    categoryLabel(watch.category),
    "скачок от ${MoneyFormat.formatPercentValue(watch.jumpPercent)}",
    watch.digestHour?.let { "сводка в ${hourText(it)}" } ?: "без сводки",
    if (watch.sendsAllDay) "присылать круглосуточно" else "присылать с ${hourText(watch.sendFrom)} до ${hourText(watch.sendTo)}",
    watch.timezone,
).joinToString(" · ")

private fun categoryLabel(category: BankRateCategory): String = when (category) {
    BankRateCategory.EXCHANGE -> "обмен в приложении"
    BankRateCategory.CARD -> "оплата картой"
}

private fun hourText(hour: Int): String = "${hour.toString().padStart(2, '0')}:00"

@Composable
fun BankRateWatchDialog(
    initial: BankRateWatch?,
    currencies: List<Currency>,
    suggestedTarget: Double?,
    onDismiss: () -> Unit,
    onConfirm: (BankRateWatchDraft) -> Unit,
) {
    val foreign = currencies.firstOrNull { it != Currency.BASE }
    var from by remember { mutableStateOf(initial?.from ?: Currency.BASE) }
    var to by remember { mutableStateOf(initial?.to ?: foreign ?: Currency.BASE) }
    var category by remember { mutableStateOf(initial?.category ?: BankRateCategory.EXCHANGE) }
    val unit = MoneyFormat.quoteUnit(initial?.target ?: suggestedTarget ?: 1.0)
    var target by remember { mutableStateOf(initial?.target?.let { MoneyFormat.formatQuote(it * unit) }.orEmpty()) }
    var jump by remember { mutableStateOf(MoneyFormat.formatTenths(initial?.jumpPercent ?: BankRateWatch.DEFAULT_JUMP_PERCENT)) }
    var sendFrom by remember { mutableStateOf(initial?.sendFrom ?: BankRateWatch.DEFAULT_QUIET_TO) }
    var sendTo by remember { mutableStateOf(initial?.sendTo ?: BankRateWatch.DEFAULT_QUIET_FROM) }
    var digest by remember { mutableStateOf(if (initial == null) BankRateWatch.DEFAULT_DIGEST_HOUR else initial.digestHour ?: DIGEST_OFF) }
    var timezone by remember { mutableStateOf(initial?.timezone ?: BankRateWatch.DEFAULT_TIMEZONE) }

    val targetValue = parseDecimal(target)?.let { it / unit }
    val jumpValue = parseDecimal(jump)
    val digestHours = HOURS.filter { BankRateWatch.inSendWindow(it, quietFrom = sendTo, quietTo = sendFrom) }
    val digestValid = digest == DIGEST_OFF || digest in digestHours
    val valid = from != to && (target.isBlank() || (targetValue != null && targetValue > 0.0)) && jumpValue != null && jumpValue > 0.0 && digestValid && timezone.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Следить за курсом банка" else "Слежение за курсом") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown("Из валюты", currencies, from, Currency::code, { from = it }, modifier = Modifier.weight(1f))
                    Dropdown("В валюту", currencies, to, Currency::code, { to = it }, modifier = Modifier.weight(1f))
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BankRateCategory.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = item == category,
                            onClick = { category = item },
                            shape = SegmentedButtonDefaults.itemShape(index, BankRateCategory.entries.size),
                            icon = {},
                        ) { Text(categoryLabel(item)) }
                    }
                }
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Цель, ${MoneyFormat.perUnitLabel(unit, from, to)}") },
                    placeholder = { Text(suggestedTarget?.let { "максимум за неделю ${MoneyFormat.formatQuote(it * unit)}" } ?: "без цели") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = jump,
                    onValueChange = { jump = it },
                    label = { Text("Скачок с прошлой проверки, %") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown("Присылать с", HOURS, sendFrom, ::hourText, { sendFrom = it }, modifier = Modifier.weight(1f))
                    Dropdown("до", HOURS, sendTo, ::hourText, { sendTo = it }, modifier = Modifier.weight(1f))
                }
                Dropdown("Сводка каждый день", listOf(DIGEST_OFF) + digestHours, digest, { if (it == DIGEST_OFF) "выключена" else hourText(it) }, { digest = it })
                OutlinedTextField(
                    value = timezone,
                    onValueChange = { timezone = it.trim() },
                    label = { Text("Часовой пояс") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Уведомления приходят только с ${hourText(sendFrom)} до ${hourText(sendTo)}. Что случилось в остальное время, придёт одним сообщением в ${hourText(sendFrom)}. Одинаковые часы — присылать круглосуточно. Без цели сигнал о цели не отправляется.",
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
                            category = category,
                            target = targetValue,
                            jumpPercent = jumpValue ?: BankRateWatch.DEFAULT_JUMP_PERCENT,
                            digestHour = digest.takeIf { it != DIGEST_OFF },
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
