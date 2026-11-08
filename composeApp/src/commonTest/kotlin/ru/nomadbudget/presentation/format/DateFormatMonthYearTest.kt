package ru.nomadbudget.presentation.format

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateFormatMonthYearTest {

    @Test
    fun monthYear() {
        assertEquals("ноябрь 2027", DateFormat.monthYear(LocalDate(2027, 11, 5)))
        assertEquals("май 2026", DateFormat.monthYear(LocalDate(2026, 5, 1)))
    }

    @Test
    fun byMonthYear() {
        assertEquals("к ноябрю 2027", DateFormat.byMonthYear(LocalDate(2027, 11, 5)))
        assertEquals("к марту 2026", DateFormat.byMonthYear(LocalDate(2026, 3, 31)))
        assertEquals("к маю 2026", DateFormat.byMonthYear(LocalDate(2026, 5, 1)))
    }
}
