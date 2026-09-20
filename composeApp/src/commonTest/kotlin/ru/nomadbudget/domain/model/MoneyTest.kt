package ru.nomadbudget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun plus_sameCurrency_addsMinorUnits() {
        val result = Money(1_050L, Currency.RUB) + Money(950L, Currency.RUB)
        assertEquals(Money(2_000L, Currency.RUB), result)
    }

    @Test
    fun plus_differentCurrency_throws() {
        assertFailsWith<IllegalArgumentException> {
            Money(100L, Currency.RUB) + Money(100L, Currency.USD)
        }
    }

    @Test
    fun ofMajor_rub_convertsToKopecks() {
        assertEquals(Money(26_348_000L, Currency.RUB), Money.ofMajor(263_480.0, Currency.RUB))
    }

    @Test
    fun ofMajor_vnd_hasNoFraction() {
        assertEquals(Money(385_000L, Currency.VND), Money.ofMajor(385_000.0, Currency.VND))
        assertEquals(385_000.0, Money(385_000L, Currency.VND).toMajor())
    }

    @Test
    fun ofMajor_usd_roundsToCents() {
        assertEquals(Money(351L, Currency.USD), Money.ofMajor(3.51, Currency.USD))
        assertEquals(Money(350L, Currency.USD), Money.ofMajor(3.504, Currency.USD))
    }

    @Test
    fun ofMajor_binaryFloatArtifact_isAbsorbedByRounding() {
        assertEquals(Money(30L, Currency.USD), Money.ofMajor(0.1 + 0.2, Currency.USD))
    }

    @Test
    fun sumIn_emptyList_isZero() {
        assertEquals(Money.zero(Currency.VND), emptyList<Money>().sumIn(Currency.VND))
    }
}
