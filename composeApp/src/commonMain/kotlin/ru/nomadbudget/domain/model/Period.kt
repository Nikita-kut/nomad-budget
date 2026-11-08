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

    const val DEFAULT_START_DAY: Int = 1
    const val MAX_START_DAY: Int = 28
    val startDays: IntRange = DEFAULT_START_DAY..MAX_START_DAY

    fun periodContaining(date: LocalDate, startDay: Int): Period {
        val startThisMonth = startDayIn(date.year, date.month.number, startDay)
        val start = when {
            date >= startThisMonth -> startThisMonth
            else -> startDayInPreviousMonth(date, startDay)
        }
        return Period(start, nextStart(start, startDay))
    }

    fun next(period: Period, startDay: Int): Period =
        Period(period.endExclusive, nextStart(period.endExclusive, startDay))

    fun previous(period: Period, startDay: Int): Period =
        Period(startDayInPreviousMonth(period.start, startDay), period.start)

    private fun nextStart(from: LocalDate, startDay: Int): LocalDate {
        val nextMonth = from.plus(1, DateTimeUnit.MONTH)
        return startDayIn(nextMonth.year, nextMonth.month.number, startDay)
    }

    private fun startDayInPreviousMonth(from: LocalDate, startDay: Int): LocalDate {
        val previousMonth = from.plus(-1, DateTimeUnit.MONTH)
        return startDayIn(previousMonth.year, previousMonth.month.number, startDay)
    }

    private fun startDayIn(year: Int, month: Int, startDay: Int): LocalDate {
        val firstOfMonth = LocalDate(year, month, 1)
        val daysInMonth = firstOfMonth.daysUntil(firstOfMonth.plus(1, DateTimeUnit.MONTH))
        return LocalDate(year, month, startDay.coerceIn(1, daysInMonth))
    }
}

private fun LocalDate.shortDayMonth(): String =
    "${day.toString().padStart(2, '0')}.${month.number.toString().padStart(2, '0')}"
