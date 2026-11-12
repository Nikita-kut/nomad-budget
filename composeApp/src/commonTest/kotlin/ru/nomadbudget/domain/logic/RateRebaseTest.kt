package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.RateTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RateRebaseTest {

    private val xts = Currency("XTS", 0, "¤")
    private val day1 = LocalDate(2026, 10, 1)
    private val day2 = LocalDate(2026, 10, 2)

    private fun rate(quote: Currency, rub: Double, date: LocalDate = day1) = ExchangeRate(quote, rub, date, RateSource.API)

    @Test
    fun sameBaseKeepsTable() {
        val pivot = RateTable(listOf(rate(Currency.USD, 90.0)), Currency.RUB)
        assertSame(pivot, RateRebase.table(pivot, Currency.RUB))
    }

    @Test
    fun tableRebasedToUsd() {
        val pivot = RateTable(listOf(rate(Currency.USD, 90.0), rate(xts, 0.009)), Currency.RUB)
        val usd = RateRebase.table(pivot, Currency.USD)
        assertEquals(Currency.USD, usd.base)
        assertEquals(0.0001, usd.basePerUnit(xts), 1e-12)
        assertEquals(1.0 / 90.0, usd.basePerUnit(Currency.RUB), 1e-12)
        assertEquals(Money(100L, Currency.USD), usd.toBase(Money(9_000L, Currency.RUB)))
    }

    @Test
    fun historyUsesBaseRateOfTheSameDay() {
        val pivot = listOf(rate(Currency.USD, 90.0, day1), rate(Currency.USD, 100.0, day2), rate(xts, 0.009, day1), rate(xts, 0.01, day2))
        val usd = RateRebase.history(pivot, Currency.RUB, Currency.USD)
        assertEquals(listOf(0.0001, 0.0001), usd.filter { it.quote == xts }.map { it.basePerUnit }.map { kotlin.math.round(it * 1e8) / 1e8 })
        assertEquals(listOf(1.0 / 90.0, 1.0 / 100.0), usd.filter { it.quote == Currency.RUB }.map { it.basePerUnit })
        assertTrue(usd.none { it.quote == Currency.USD })
    }
}
