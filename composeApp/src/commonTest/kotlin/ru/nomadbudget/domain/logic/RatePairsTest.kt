package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.RateSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RatePairsTest {

    private val history = listOf(
        ExchangeRate(Currency.USD, 90.0, LocalDate(2026, 10, 1), RateSource.API),
        ExchangeRate(Currency.USD, 92.0, LocalDate(2026, 10, 3), RateSource.API),
        ExchangeRate(Currency.USD, 95.0, LocalDate(2026, 10, 7), RateSource.API),
    )

    @Test
    fun basePerUnitOn_takesLatestRateNotAfterDate() {
        assertEquals(92.0, RatePairs.basePerUnitOn(history, Currency.USD, LocalDate(2026, 10, 5)))
        assertEquals(95.0, RatePairs.basePerUnitOn(history, Currency.USD, LocalDate(2026, 10, 7)))
        assertNull(RatePairs.basePerUnitOn(history, Currency.USD, LocalDate(2026, 9, 30)))
        assertEquals(1.0, RatePairs.basePerUnitOn(emptyList(), Currency.BASE, LocalDate(2026, 9, 30)))
    }

    @Test
    fun median_andInverted() {
        val points = listOf(RatePoint(LocalDate(2026, 10, 1), 90.0), RatePoint(LocalDate(2026, 10, 2), 100.0), RatePoint(LocalDate(2026, 10, 3), 92.0))
        assertEquals(92.0, RatePairs.median(points))
        assertEquals(0.01, RatePairs.inverted(points)[1].value, absoluteTolerance = 1e-12)
    }

    @Test
    fun changeFrom_previousPoint() {
        val today = RatePoint(LocalDate(2026, 10, 2), 92.0)
        assertEquals(0.02222, today.changeFrom(RatePoint(LocalDate(2026, 10, 1), 90.0))!!, absoluteTolerance = 1e-4)
        assertNull(today.changeFrom(null))
    }
}
