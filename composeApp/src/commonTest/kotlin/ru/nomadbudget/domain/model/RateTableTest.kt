package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.TestCurrencies.JPY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RateTableTest {

    private val date = LocalDate(2026, 9, 19)
    private val rates = RateTable(
        listOf(
            ExchangeRate(Currency.USD, basePerUnit = 84.1975, date = date, source = RateSource.API),
            ExchangeRate(JPY, basePerUnit = 0.5, date = date, source = RateSource.API),
        ),
    )

    @Test
    fun toBase_usd_convertsToKopecks() {
        val base = rates.toBase(Money(20_000L, Currency.USD))
        assertEquals(Money(1_683_950L, Currency.RUB), base)
    }

    @Test
    fun toBase_zeroDecimalCurrency_roundsToKopecks() {
        val base = rates.toBase(Money(385_001L, JPY))
        assertEquals(Money(19_250_050L, Currency.RUB), base)
    }

    @Test
    fun toBase_rub_isIdentity() {
        val money = Money(940_000L, Currency.RUB)
        assertEquals(money, rates.toBase(money))
    }

    @Test
    fun fromBase_zeroDecimalCurrency_dropsFraction() {
        val jpy = rates.fromBase(Money(100_033L, Currency.RUB), JPY)
        assertEquals(Money(2_001L, JPY), jpy)
    }

    @Test
    fun cross_usdToJpy() {
        assertEquals(168.395, rates.cross(Currency.USD, JPY), absoluteTolerance = 0.001)
    }

    @Test
    fun rateFor_base_isNull() {
        assertNull(rates.rateFor(Currency.RUB))
    }

    @Test
    fun hasRate_baseAlwaysTrue_unknownFalse() {
        assertTrue(rates.hasRate(Currency.RUB))
        assertTrue(rates.hasRate(JPY))
        assertFalse(rates.hasRate(Currency("EUR", 2, "€")))
    }

    @Test
    fun toBaseOrNull_missingRate_returnsNull() {
        assertNull(rates.toBaseOrNull(Money(1L, Currency("EUR", 2, "€"))))
    }

    @Test
    fun toBase_missingRate_throws() {
        val onlyUsd = RateTable(listOf(ExchangeRate(Currency.USD, 84.0, date, RateSource.API)))
        assertFailsWith<IllegalArgumentException> { onlyUsd.toBase(Money(1L, JPY)) }
    }
}
