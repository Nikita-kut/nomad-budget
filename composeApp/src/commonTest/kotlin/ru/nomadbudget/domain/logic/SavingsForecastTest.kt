package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.SalaryCycle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavingsForecastTest {

    private fun point(month: Int, saved: Long) = MonthPoint(
        SalaryCycle.periodContaining(LocalDate(2026, month, 10)), Money.rub(0L), Money.rub(0L), Money.rub(saved),
    )

    @Test
    fun forecast_usesLastThreeClosedMonths_andTarget() {
        val points = listOf(point(6, 9_000_000L), point(7, 1_000_000L), point(8, 2_000_000L), point(9, 3_000_000L), point(10, 500_000L))
        val f = HistoryCalculator.savingsForecast(points, currentPeriodOpen = true, savingsBalance = Money.rub(100_000_000L), monthlyTarget = Money.rub(4_000_000L))
        assertEquals(3, f?.monthsUsed)
        assertEquals(Money.rub(2_000_000L), f?.actualPerMonth)
        assertEquals(Money.rub(124_000_000L), f?.byActualInYear)
        assertEquals(Money.rub(148_000_000L), f?.byTargetInYear)
    }

    @Test
    fun forecast_negativePace_reducesBalance() {
        val f = HistoryCalculator.savingsForecast(listOf(point(9, -1_000_000L)), currentPeriodOpen = false, savingsBalance = Money.rub(50_000_000L), monthlyTarget = null)
        assertEquals(Money.rub(38_000_000L), f?.byActualInYear)
        assertNull(f?.byTargetInYear)
    }

    @Test
    fun forecast_noDataNoTarget_isNull() {
        assertNull(HistoryCalculator.savingsForecast(emptyList(), true, Money.rub(0L), null))
    }
}
