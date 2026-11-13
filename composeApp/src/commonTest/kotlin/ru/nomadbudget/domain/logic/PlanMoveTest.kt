package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanMoveTest {

    @Test
    fun movesToPeriodContainingToday_whenEnoughDaysLeft() {
        val target = PlanMove.target(LocalDate(2026, 11, 13), newStartDay = 10)
        assertEquals(LocalDate(2026, 11, 10), target.start)
    }

    @Test
    fun movesToNextPeriod_whenNewPeriodEndsWithinAWeek() {
        val target = PlanMove.target(LocalDate(2026, 11, 13), newStartDay = 14)
        assertEquals(LocalDate(2026, 11, 14), target.start)
        val edge = PlanMove.target(LocalDate(2026, 11, 13), newStartDay = 20)
        assertEquals(LocalDate(2026, 11, 20), edge.start)
    }
}
