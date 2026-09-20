package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RateTableTest {

    private val date = LocalDate(2026, 9, 19)
    private val rates = RateTable(
        listOf(
            ExchangeRate(Currency.USD, basePerUnit = 84.1975, date = date, source = RateSource.API),
            ExchangeRate(Currency.VND, basePerUnit = 0.00328422, date = date, source = RateSource.API),
        ),
    )

    @Test
    fun toBase_usd_convertsToKopecks() {
        val base = rates.toBase(Money(20_000L, Currency.USD))
        assertEquals(Money(1_683_950L, Currency.RUB), base)
    }

    @Test
    fun toBase_vnd_roundsToKopecks() {
        val base = rates.toBase(Money(385_000L, Currency.VND))
        assertEquals(Money(126_442L, Currency.RUB), base)
    }

    @Test
    fun toBase_rub_isIdentity() {
        val money = Money(940_000L, Currency.RUB)
        assertEquals(money, rates.toBase(money))
    }

    @Test
    fun fromBase_vnd_dropsFraction() {
        val vnd = rates.fromBase(Money(100_000L, Currency.RUB), Currency.VND)
        assertEquals(Money(304_486L, Currency.VND), vnd)
    }

    @Test
    fun cross_usdToVnd_matchesCentralBank() {
        assertEquals(25_637.0, rates.cross(Currency.USD, Currency.VND), absoluteTolerance = 1.0)
    }

    @Test
    fun rateFor_base_isNull() {
        assertNull(rates.rateFor(Currency.RUB))
    }

    @Test
    fun toBase_missingRate_throws() {
        val onlyUsd = RateTable(listOf(ExchangeRate(Currency.USD, 84.0, date, RateSource.API)))
        assertFailsWith<IllegalArgumentException> { onlyUsd.toBase(Money(1L, Currency.VND)) }
    }
}
