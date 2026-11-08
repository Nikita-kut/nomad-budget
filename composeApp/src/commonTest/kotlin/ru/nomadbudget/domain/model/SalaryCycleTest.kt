package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SalaryCycleTest {

    @Test
    fun startDay1_isCalendarMonth() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 2, 14), startDay = 1)
        assertEquals(LocalDate(2026, 2, 1), period.start)
        assertEquals(LocalDate(2026, 3, 1), period.endExclusive)
        assertEquals(28, period.lengthDays)
    }

    @Test
    fun startDay1_onFirstDay_startsThatDay() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 3, 1), startDay = 1)
        assertEquals(LocalDate(2026, 3, 1), period.start)
        assertEquals(LocalDate(2026, 4, 1), period.endExclusive)
    }

    @Test
    fun startDay1_lastDayOfMonth_belongsToThatMonth() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 1, 31), startDay = 1)
        assertEquals(LocalDate(2026, 1, 1), period.start)
        assertEquals(LocalDate(2026, 2, 1), period.endExclusive)
    }

    @Test
    fun startDay5_midMonth_startsOnFifthOfSameMonth() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 20), startDay = 5)
        assertEquals(LocalDate(2026, 9, 5), period.start)
        assertEquals(LocalDate(2026, 10, 5), period.endExclusive)
    }

    @Test
    fun startDay5_beforeStart_startsInPreviousMonth() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 10, 3), startDay = 5)
        assertEquals(LocalDate(2026, 9, 5), period.start)
    }

    @Test
    fun startDay5_onStartDay_startsThatDay() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 10, 5), startDay = 5)
        assertEquals(LocalDate(2026, 10, 5), period.start)
        assertEquals(LocalDate(2026, 11, 5), period.endExclusive)
    }

    @Test
    fun startDay5_december_rollsIntoNextYear() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 12, 20), startDay = 5)
        assertEquals(LocalDate(2027, 1, 5), period.endExclusive)
    }

    @Test
    fun startDay5_january_beforeStart_startsInPreviousYear() {
        val period = SalaryCycle.periodContaining(LocalDate(2027, 1, 2), startDay = 5)
        assertEquals(LocalDate(2026, 12, 5), period.start)
    }

    @Test
    fun startDay28_february_keepsDay28_andIsNeverClamped() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 2, 28), startDay = 28)
        assertEquals(LocalDate(2026, 2, 28), period.start)
        assertEquals(LocalDate(2026, 3, 28), period.endExclusive)
        assertEquals(28, period.lengthDays)
    }

    @Test
    fun startDay28_dayBefore_belongsToPreviousPeriod() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 3, 27), startDay = 28)
        assertEquals(LocalDate(2026, 2, 28), period.start)
        assertEquals(LocalDate(2026, 3, 28), period.endExclusive)
    }

    @Test
    fun startDay28_leapFebruary() {
        val period = SalaryCycle.periodContaining(LocalDate(2028, 2, 29), startDay = 28)
        assertEquals(LocalDate(2028, 2, 28), period.start)
        assertEquals(LocalDate(2028, 3, 28), period.endExclusive)
        assertEquals(29, period.lengthDays)
    }

    @Test
    fun startDay31_clampsToShortMonth() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 2, 10), startDay = 31)
        assertEquals(LocalDate(2026, 1, 31), period.start)
        assertEquals(LocalDate(2026, 2, 28), period.endExclusive)
    }

    @Test
    fun startDays_areOneToTwentyEight() {
        assertEquals(1, SalaryCycle.startDays.first)
        assertEquals(28, SalaryCycle.startDays.last)
        assertEquals(1, SalaryCycle.DEFAULT_START_DAY)
    }

    @Test
    fun september2026_lengthAndDayNumber() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 5), startDay = 5)
        assertEquals(30, period.lengthDays)
        assertEquals(16, period.dayNumber(LocalDate(2026, 9, 20)))
    }

    @Test
    fun contains_endExclusive() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 5), startDay = 5)
        assertTrue(LocalDate(2026, 10, 4) in period)
        assertFalse(LocalDate(2026, 10, 5) in period)
    }

    @Test
    fun next_and_previous_areAdjacent_forEveryStartDay() {
        for (startDay in SalaryCycle.startDays) {
            val current = SalaryCycle.periodContaining(LocalDate(2026, 9, 20), startDay)
            assertEquals(current.endExclusive, SalaryCycle.next(current, startDay).start)
            assertEquals(current.start, SalaryCycle.previous(current, startDay).endExclusive)
            assertEquals(startDay, current.start.day)
        }
    }

    @Test
    fun wholeYear_isCoveredWithoutGaps_forStartDays1_5_28() {
        for (startDay in listOf(1, 5, 28)) {
            var period = SalaryCycle.periodContaining(LocalDate(2026, 1, 1), startDay)
            var date = LocalDate(2026, 1, 1)
            while (date < LocalDate(2027, 1, 1)) {
                if (date !in period) period = SalaryCycle.next(period, startDay)
                assertTrue(date in period, "$date not in $period for start day $startDay")
                date = LocalDate.fromEpochDays(date.toEpochDays() + 1)
            }
        }
    }
}
