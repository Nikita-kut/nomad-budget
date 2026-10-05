package ru.nomadbudget.presentation.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import kotlin.math.abs
import kotlin.math.roundToLong
import ru.nomadbudget.domain.model.Period

object MoneyFormat {

    private const val MINUS = "−"
    private const val THIN_SPACE = " "

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

    fun formatSigned(money: Money): String = when {
        money.minor > 0 -> "+" + format(money)
        else -> format(money)
    }

    fun formatQuote(rate: Double): String = when {
        rate >= 100.0 -> groupThousands(rate.roundToLong())
        rate >= 1.0 -> {
            val scaled = (rate * 100).roundToLong()
            "${scaled / 100},${(scaled % 100).toString().padStart(2, '0')}"
        }
        else -> formatRate(rate)
    }

    fun formatRate(rate: Double): String = when {
        rate >= 100.0 -> groupThousands(rate.roundToLong())
        else -> {
            val scaled = (rate * 10_000).roundToLong()
            "${scaled / 10_000},${(scaled % 10_000).toString().padStart(4, '0')}"
        }
    }

    fun formatShare(value: Double): String = "${(value * 100).roundToLong()}%"

    fun formatPercent(value: Double): String {
        val scaled = (abs(value) * 100).roundToLong()
        val sign = if (value < 0) MINUS else "+"
        return "$sign${scaled / 100},${(scaled % 100).toString().padStart(2, '0')}%"
    }

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

    private val weekdays = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    private val weekdaysFull = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
    private val months = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )

    fun monthName(date: LocalDate): String = months[date.month.number - 1]

    fun periodRange(period: Period): String = "${dayMonth(period.start)} – ${dayMonth(period.lastDay)}"

    fun dayMonth(date: LocalDate): String =
        "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}"

    fun dayMonthTime(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dayMonth(local.date)} ${timeOnly(instant)}"
    }

    fun timeOnly(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    fun weekdayShort(date: LocalDate): String = weekdays[date.dayOfWeek.ordinal]

    fun weekdayFull(date: LocalDate): String = weekdaysFull[date.dayOfWeek.ordinal]
}
