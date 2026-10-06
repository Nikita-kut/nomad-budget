package ru.nomadbudget.presentation.format

import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.TestCurrencies.JPY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyFormatTest {

    @Test
    fun format_rub_groupsThousands_andHidesZeroFraction() {
        assertEquals("263\u00A0480\u00A0₽", MoneyFormat.format(Money.rub(26_348_000L)))
        assertEquals("1\u00A0234,56\u00A0₽", MoneyFormat.format(Money.rub(123_456L)))
    }

    @Test
    fun format_usd_prefixSymbol() {
        assertEquals("$1\u00A0051,50", MoneyFormat.format(Money(105_150L, Currency.USD)))
        assertEquals("$200", MoneyFormat.format(Money(20_000L, Currency.USD)))
    }

    @Test
    fun format_zeroDecimalCurrency_noFraction() {
        assertEquals("5\u00A0180\u00A0000\u00A0¥", MoneyFormat.format(Money(5_180_000L, JPY)))
    }

    @Test
    fun format_negative_usesMinusSign() {
        assertEquals("−38\u00A0500\u00A0₽", MoneyFormat.format(Money.rub(-3_850_000L)))
    }

    @Test
    fun parse_acceptsSpacesAndComma() {
        assertEquals(Money.rub(123_456L), MoneyFormat.parse("1\u00A0234,56", Currency.RUB))
        assertEquals(Money.rub(123_450L), MoneyFormat.parse("1234.5", Currency.RUB))
        assertEquals(Money(385_000L, JPY), MoneyFormat.parse("385000", JPY))
    }

    @Test
    fun parse_zeroDecimalCurrency_dropsFraction() {
        assertEquals(Money(385_000L, JPY), MoneyFormat.parse("385000,9", JPY))
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
        assertEquals("25\u00A0900", MoneyFormat.formatRate(25_900.0))
        assertEquals("84,1975", MoneyFormat.formatRate(84.1975))
    }

    @Test
    fun formatPercent_sign() {
        assertEquals("−1,03%", MoneyFormat.formatPercent(-1.026))
        assertEquals("+0,50%", MoneyFormat.formatPercent(0.5))
    }

    @Test
    fun formatShare_roundsToWholePercent() {
        assertEquals("45%", MoneyFormat.formatShare(0.45))
        assertEquals("7%", MoneyFormat.formatShare(0.0666))
        assertEquals("120%", MoneyFormat.formatShare(1.2))
    }

    @Test
    fun periodRange_showsLastDayInclusive_andMonthName() {
        val period = ru.nomadbudget.domain.model.SalaryCycle.periodContaining(kotlinx.datetime.LocalDate(2026, 10, 22))
        assertEquals("05.10 – 04.11", DateFormat.periodRange(period))
        assertEquals("Октябрь", DateFormat.monthName(period.start))
    }

    @Test
    fun formatQuote_twoDecimalsForMidRange() {
        assertEquals("92,80", MoneyFormat.formatQuote(92.8))
        assertEquals("149", MoneyFormat.formatQuote(149.2))
        assertEquals("0,0108", MoneyFormat.formatQuote(0.0108))
    }
}
