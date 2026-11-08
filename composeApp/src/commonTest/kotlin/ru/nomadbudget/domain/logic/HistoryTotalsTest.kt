package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.TEST_START_DAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryTotalsTest {

    private fun point(month: Int, income: Long, expense: Long, saved: Long) = MonthPoint(
        SalaryCycle.periodContaining(LocalDate(2026, month, 10), TEST_START_DAY), Money.rub(income), Money.rub(expense), Money.rub(saved),
    )

    @Test
    fun totals_sumAndAverage() {
        val totals = HistoryCalculator.totals(listOf(point(8, 10_000_000L, 6_000_000L, 2_000_000L), point(9, 20_000_000L, 8_000_000L, 4_000_000L)))
        assertEquals(2, totals.months)
        assertEquals(Money.rub(30_000_000L), totals.income)
        assertEquals(Money.rub(15_000_000L), totals.averageIncome)
        assertEquals(Money.rub(7_000_000L), totals.averageExpense)
        assertEquals(Money.rub(6_000_000L), totals.netSaved)
        assertEquals(0.2, totals.savingsShare!!, absoluteTolerance = 1e-9)
    }

    @Test
    fun totals_empty_hasZeroAveragesAndNoShare() {
        val totals = HistoryCalculator.totals(emptyList())
        assertEquals(0, totals.months)
        assertEquals(Money.rub(0L), totals.averageIncome)
        assertNull(totals.savingsShare)
    }
}
