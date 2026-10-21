package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonthVerdictTest {

    private fun summary(spentTotal: Long, fromSavings: Long, plannedTotal: Long, plannedFromSavings: Long, saved: Long, unplanned: Long = 0L) = MonthSummary(
        incomePlanned = Money.rub(40_000_000L), incomeFact = Money.rub(40_000_000L),
        expensePlanned = Money.rub(plannedTotal), expenseFact = Money.rub(spentTotal),
        savedToSavings = Money.rub(saved), takenFromSavings = Money.rub(0L),
        expenseFromSavings = Money.rub(fromSavings), plannedFromSavings = Money.rub(plannedFromSavings),
        unplannedFromSavings = Money.rub(unplanned),
    )

    @Test
    fun noPlan_noVerdict() {
        assertNull(MonthVerdict.evaluate(summary(0, 0, 0, 0, 0), null, periodEnded = false))
    }

    @Test
    fun withinPlanAndSavingsDone_isGood() {
        val verdict = MonthVerdict.evaluate(summary(35_000_000L, 5_000_000L, 35_000_000L, 5_000_000L, 10_000_000L), Money.rub(10_000_000L), false)
        assertEquals(VerdictLevel.GOOD, verdict?.level)
    }

    @Test
    fun savingsShortWhileMonthRuns_isWarning_andBadWhenEnded() {
        val s = summary(20_000_000L, 0, 30_000_000L, 0, 4_000_000L)
        assertEquals(VerdictLevel.WARNING, MonthVerdict.evaluate(s, Money.rub(10_000_000L), false)?.level)
        assertEquals(VerdictLevel.BAD, MonthVerdict.evaluate(s, Money.rub(10_000_000L), true)?.level)
    }

    @Test
    fun overspendFromIncome_isBad() {
        val verdict = MonthVerdict.evaluate(summary(32_000_000L, 0, 30_000_000L, 0, 0), null, false)
        assertEquals(VerdictLevel.BAD, verdict?.level)
    }

    @Test
    fun unplannedFromSavings_isWarning() {
        val verdict = MonthVerdict.evaluate(summary(38_000_000L, 8_000_000L, 30_000_000L, 0, 0, unplanned = 8_000_000L), null, false)
        assertEquals(VerdictLevel.WARNING, verdict?.level)
    }
}
