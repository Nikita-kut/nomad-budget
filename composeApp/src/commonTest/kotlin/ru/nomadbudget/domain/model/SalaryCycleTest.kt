package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SalaryCycleTest {

    @Test
    fun periodContaining_midMonth_startsOnFifthOfSameMonth() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 20))
        assertEquals(LocalDate(2026, 9, 5), period.start)
        assertEquals(LocalDate(2026, 10, 5), period.endExclusive)
    }

    @Test
    fun periodContaining_beforePayDay_startsInPreviousMonth() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 10, 3))
        assertEquals(LocalDate(2026, 9, 5), period.start)
    }

    @Test
    fun periodContaining_onPayDay_startsThatDay() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 10, 5))
        assertEquals(LocalDate(2026, 10, 5), period.start)
        assertEquals(LocalDate(2026, 11, 5), period.endExclusive)
    }

    @Test
    fun periodContaining_december_rollsIntoNextYear() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 12, 20))
        assertEquals(LocalDate(2027, 1, 5), period.endExclusive)
    }

    @Test
    fun periodContaining_january_beforePayDay_startsInPreviousYear() {
        val period = SalaryCycle.periodContaining(LocalDate(2027, 1, 2))
        assertEquals(LocalDate(2026, 12, 5), period.start)
    }

    @Test
    fun periodContaining_payDay31_clampsToShortMonth() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 2, 10), payDay = 31)
        assertEquals(LocalDate(2026, 1, 31), period.start)
        assertEquals(LocalDate(2026, 2, 28), period.endExclusive)
    }

    @Test
    fun september2026_lengthAndDayNumber_matchSpreadsheet() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 5))
        assertEquals(30, period.lengthDays)
        assertEquals(16, period.dayNumber(LocalDate(2026, 9, 20)))
        assertEquals("05.09 → 05.10", period.title())
    }

    @Test
    fun contains_endExclusive() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 5))
        assertTrue(LocalDate(2026, 10, 4) in period)
        assertFalse(LocalDate(2026, 10, 5) in period)
    }

    @Test
    fun next_and_previous_areAdjacent() {
        val current = SalaryCycle.periodContaining(LocalDate(2026, 9, 20))
        assertEquals(current.endExclusive, SalaryCycle.next(current).start)
        assertEquals(current.start, SalaryCycle.previous(current).endExclusive)
    }
}
