package ru.nomadbudget.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.plus

data class Period(val start: LocalDate, val endExclusive: LocalDate) {

    init {
        require(endExclusive > start) { "Конец периода должен быть позже начала" }
    }

    val lengthDays: Int get() = start.daysUntil(endExclusive)

    val lastDay: LocalDate get() = endExclusive.plus(-1, DateTimeUnit.DAY)

    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive

    fun dayNumber(date: LocalDate): Int {
        require(date in this) { "Дата $date вне периода $this" }
        return start.daysUntil(date) + 1
    }

    fun title(): String = "${start.shortDayMonth()} → ${endExclusive.shortDayMonth()}"
}

object SalaryCycle {

    const val DEFAULT_PAY_DAY: Int = 5

    fun periodContaining(date: LocalDate, payDay: Int = DEFAULT_PAY_DAY): Period {
        val startThisMonth = payDayIn(date.year, date.month.number, payDay)
        val start = when {
            date >= startThisMonth -> startThisMonth
            else -> payDayInPreviousMonth(date, payDay)
        }
        return Period(start, nextPayDay(start, payDay))
    }

    fun next(period: Period, payDay: Int = DEFAULT_PAY_DAY): Period =
        Period(period.endExclusive, nextPayDay(period.endExclusive, payDay))

    fun previous(period: Period, payDay: Int = DEFAULT_PAY_DAY): Period =
        Period(payDayInPreviousMonth(period.start, payDay), period.start)

    private fun nextPayDay(from: LocalDate, payDay: Int): LocalDate {
        val nextMonth = from.plus(1, DateTimeUnit.MONTH)
        return payDayIn(nextMonth.year, nextMonth.month.number, payDay)
    }

    private fun payDayInPreviousMonth(from: LocalDate, payDay: Int): LocalDate {
        val previousMonth = from.plus(-1, DateTimeUnit.MONTH)
        return payDayIn(previousMonth.year, previousMonth.month.number, payDay)
    }

    private fun payDayIn(year: Int, month: Int, payDay: Int): LocalDate {
        val firstOfMonth = LocalDate(year, month, 1)
        val daysInMonth = firstOfMonth.daysUntil(firstOfMonth.plus(1, DateTimeUnit.MONTH))
        return LocalDate(year, month, payDay.coerceAtMost(daysInMonth))
    }
}

private fun LocalDate.shortDayMonth(): String =
    "${day.toString().padStart(2, '0')}.${month.number.toString().padStart(2, '0')}"
