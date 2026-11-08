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
    fun shortForms_padDayMonthAndYear() {
        assertEquals("03.07", DateFormat.monthYearShort(LocalDate(2007, 3, 15)))
        assertEquals("11.27", DateFormat.monthYearShort(LocalDate(2027, 11, 5)))
        assertEquals("05.03.2026", DateFormat.dayMonthYear(LocalDate(2026, 3, 5)))
        assertEquals("05.03.26", DateFormat.dayMonthYearShort(LocalDate(2026, 3, 5)))
    }

    @Test
    fun byMonthYear() {
        assertEquals("к ноябрю 2027", DateFormat.byMonthYear(LocalDate(2027, 11, 5)))
        assertEquals("к марту 2026", DateFormat.byMonthYear(LocalDate(2026, 3, 31)))
        assertEquals("к маю 2026", DateFormat.byMonthYear(LocalDate(2026, 5, 1)))
    }
}
