package ru.nomadbudget.presentation.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import ru.nomadbudget.domain.logic.RateQuote
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import ru.nomadbudget.domain.model.Period

object MoneyFormat {

    private const val SMALL_SHARE = 0.005

    private const val MINUS = "−"
    private const val MILLION = 1_000_000.0
    private const val MAX_QUOTE_UNIT = 1_000_000L
    private const val THOUSAND = 1_000.0
    private const val THIN_SPACE = "\u00A0"

    fun format(money: Money, showFraction: Boolean = true): String {
        val currency = money.currency
        val absMinor = abs(money.minor)
        val major = absMinor / currency.minorFactor
        val fraction = absMinor % currency.minorFactor
        val body = buildString {
            append(groupThousands(major))
            if (showFraction && currency.minorUnits > 0 && fraction != 0L) {
                append(',')
                append(fraction.toString().padStart(currency.minorUnits, '0'))
            }
        }
        val sign = if (money.minor < 0) MINUS else ""
        return when {
            currency.symbolBeforeAmount -> "$sign${currency.symbol}$body"
            else -> "$sign$body$THIN_SPACE${currency.symbol}"
        }
    }

    fun formatSigned(money: Money, showFraction: Boolean = true): String = when {
        money.minor > 0 -> "+" + format(money, showFraction)
        else -> format(money, showFraction)
    }

    fun editText(money: Money): String {
        val major = money.minor / money.currency.minorFactor
        val fraction = money.minor % money.currency.minorFactor
        return if (fraction == 0L) major.toString() else "$major,${fraction.toString().padStart(money.currency.minorUnits, '0')}"
    }

    fun formatPercentValue(value: Double): String {
        val scaled = (value * 100).roundToLong()
        val whole = scaled / 100
        val fraction = (scaled % 100).toString().padStart(2, '0').trimEnd('0')
        return if (fraction.isEmpty()) "$whole %" else "$whole,$fraction %"
    }

    fun formatQuote(rate: Double): String = when {
        rate >= 100.0 -> groupThousands(rate.roundToLong())
        rate >= 1.0 -> {
            val scaled = (rate * 100).roundToLong()
            "${scaled / 100},${(scaled % 100).toString().padStart(2, '0')}"
        }
        else -> formatRate(rate)
    }

    private fun formatRate(rate: Double): String = when {
        rate >= 100.0 -> groupThousands(rate.roundToLong())
        else -> {
            val scaled = (rate * 10_000).roundToLong()
            "${scaled / 10_000},${(scaled % 10_000).toString().padStart(4, '0')}"
        }
    }

    fun quoteUnit(value: Double): Long {
        var unit = 1L
        val absolute = abs(value)
        while (absolute > 0.0 && absolute * unit < 1.0 && unit < MAX_QUOTE_UNIT) unit *= 10
        return unit
    }

    fun unitLabel(unit: Long, currency: Currency): String = "${groupThousands(unit)}$THIN_SPACE${currency.code}"

    fun rateText(from: Currency, to: Currency, value: Double, unit: Long = 1L): String =
        "${unitLabel(unit, from)} = ${formatQuote(value)}$THIN_SPACE${to.code}"

    fun quoteText(quote: RateQuote): String = rateText(quote.unit, quote.price, quote.value)

    fun perUnitLabel(unit: Long, from: Currency, to: Currency): String = "${to.code} за ${unitLabel(unit, from)}"

    fun formatNumber(value: Double, decimals: Int): String {
        val factor = 10.0.pow(decimals)
        val scaled = (abs(value) * factor).roundToLong()
        val whole = scaled / factor.toLong()
        val fraction = (scaled % factor.toLong()).toString().padStart(decimals, '0')
        val sign = if (value < 0 && scaled != 0L) MINUS else ""
        return if (decimals == 0) "$sign${groupThousands(whole)}" else "$sign${groupThousands(whole)},$fraction"
    }

    fun formatChange(share: Double): String {
        val hundredths = (share * 10_000).roundToLong()
        if (hundredths == 0L) return "0"
        val sign = if (hundredths < 0) MINUS else "+"
        val absolute = abs(hundredths)
        return "$sign${absolute / 100},${(absolute % 100).toString().padStart(2, '0')}"
    }

    fun formatAxis(value: Double): String {
        val sign = if (value < 0) MINUS else ""
        val absolute = abs(value)
        return sign + when {
            absolute >= MILLION -> trimTenths(absolute / MILLION) + " млн"
            absolute >= THOUSAND -> trimTenths(absolute / THOUSAND) + " тыс"
            else -> absolute.roundToLong().toString()
        }
    }

    fun formatWhole(value: Double): String {
        val rounded = value.roundToLong()
        val sign = if (rounded < 0) MINUS else ""
        return "$sign${groupThousands(abs(rounded))}$THIN_SPACE${Currency.BASE.symbol}"
    }

    fun formatTenths(value: Double): String {
        val tenths = (value * 10).roundToLong()
        val sign = if (tenths < 0) MINUS else ""
        val absolute = abs(tenths)
        return if (absolute % 10 == 0L) "$sign${absolute / 10}" else "$sign${absolute / 10},${absolute % 10}"
    }

    private fun trimTenths(value: Double): String = formatTenths(value)

    fun formatShare(value: Double): String = when {
        value > 0.0 && value < SMALL_SHARE -> "<1%"
        else -> "${(value * 100).roundToLong()}%"
    }

    fun formatPercentPoints(value: Double): String = "${value.roundToLong()}%"

    fun parse(input: String, currency: Currency): Money? {
        val cleaned = input.replace(" ", "").replace(THIN_SPACE, "").replace(',', '.').trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('.')
        if (parts.size > 2) return null
        val major = parts[0].toLongOrNull() ?: return null
        val fractionDigits = parts.getOrNull(1).orEmpty()
        if (fractionDigits.any { !it.isDigit() }) return null
        val fraction = fractionDigits
            .padEnd(currency.minorUnits, '0')
            .take(currency.minorUnits)
            .toLongOrNull() ?: 0L
        val minor = major * currency.minorFactor + fraction
        return if (minor > 0) Money(minor, currency) else null
    }

    private fun groupThousands(value: Long): String {
        val digits = value.toString()
        val out = StringBuilder()
        digits.forEachIndexed { index, char ->
            val remaining = digits.length - index
            if (index > 0 && remaining % 3 == 0) out.append(THIN_SPACE)
            out.append(char)
        }
        return out.toString()
    }
}

object DateFormat {

    private val weekdaysFull = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
    private val months = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )

    fun monthName(date: LocalDate): String = months[date.month.number - 1]

    fun monthGenitive(date: LocalDate): String = monthsGenitive[date.month.number - 1]

    private val monthsGenitive = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    private val monthsDative = listOf(
        "январю", "февралю", "марту", "апрелю", "маю", "июню",
        "июлю", "августу", "сентябрю", "октябрю", "ноябрю", "декабрю",
    )

    fun monthYear(date: LocalDate): String = "${months[date.month.number - 1].lowercase()} ${date.year}"

    fun monthYearShort(date: LocalDate): String = "${twoDigits(date.month.number)}.${twoDigits(date.year % 100)}"

    fun byMonthYear(date: LocalDate): String = "к ${monthsDative[date.month.number - 1]} ${date.year}"

    fun periodRange(period: Period): String = "${dayMonth(period.start)} – ${dayMonth(period.lastDay)}"

    fun dayMonth(date: LocalDate): String = "${twoDigits(date.day)}.${twoDigits(date.month.number)}"

    fun dayMonthYear(date: LocalDate): String = "${dayMonth(date)}.${date.year}"

    fun dayMonthYearShort(date: LocalDate): String = "${dayMonth(date)}.${twoDigits(date.year % 100)}"

    fun dayMonthTime(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dayMonth(local.date)} ${timeOnly(instant)}"
    }

    fun timeOnly(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${twoDigits(local.hour)}:${twoDigits(local.minute)}"
    }

    fun weekdayFull(date: LocalDate): String = weekdaysFull[date.dayOfWeek.ordinal]

    private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
}
