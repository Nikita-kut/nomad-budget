package ru.nomadbudget.presentation.format

import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyFormatTest {

    @Test
    fun format_rub_groupsThousands_andHidesZeroFraction() {
        assertEquals("263 480 ₽", MoneyFormat.format(Money.rub(26_348_000L)))
        assertEquals("1 234,56 ₽", MoneyFormat.format(Money.rub(123_456L)))
    }

    @Test
    fun format_usd_prefixSymbol() {
        assertEquals("$1 051,50", MoneyFormat.format(Money(105_150L, Currency.USD)))
        assertEquals("$200", MoneyFormat.format(Money(20_000L, Currency.USD)))
    }

    @Test
    fun format_vnd_noFraction() {
        assertEquals("5 180 000 ₫", MoneyFormat.format(Money(5_180_000L, Currency.VND)))
    }

    @Test
    fun format_negative_usesMinusSign() {
        assertEquals("−38 500 ₽", MoneyFormat.format(Money.rub(-3_850_000L)))
    }

    @Test
    fun parse_acceptsSpacesAndComma() {
        assertEquals(Money.rub(123_456L), MoneyFormat.parse("1 234,56", Currency.RUB))
        assertEquals(Money.rub(123_450L), MoneyFormat.parse("1234.5", Currency.RUB))
        assertEquals(Money(385_000L, Currency.VND), MoneyFormat.parse("385000", Currency.VND))
    }

    @Test
    fun parse_vnd_dropsFraction() {
        assertEquals(Money(385_000L, Currency.VND), MoneyFormat.parse("385000,9", Currency.VND))
    }

    @Test
    fun parse_rejectsGarbageAndZero() {
        assertNull(MoneyFormat.parse("abc", Currency.RUB))
        assertNull(MoneyFormat.parse("0", Currency.RUB))
        assertNull(MoneyFormat.parse("1.2.3", Currency.RUB))
        assertNull(MoneyFormat.parse("", Currency.RUB))
    }

    @Test
    fun formatRate_bigAndSmall() {
        assertEquals("25 900", MoneyFormat.formatRate(25_900.0))
        assertEquals("84,1975", MoneyFormat.formatRate(84.1975))
    }

    @Test
    fun formatPercent_sign() {
        assertEquals("−1,03%", MoneyFormat.formatPercent(-1.026))
        assertEquals("+0,50%", MoneyFormat.formatPercent(0.5))
    }
}
