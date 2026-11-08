package ru.nomadbudget.presentation.format

import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.TestCurrencies.JPY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ru.nomadbudget.domain.model.TEST_START_DAY

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
    fun formatQuote_bigAndSmall() {
        assertEquals("25\u00A0900", MoneyFormat.formatQuote(25_900.0))
        assertEquals("0,8420", MoneyFormat.formatQuote(0.84197))
    }

    @Test
    fun editText_dropsZeroFraction_keepsNonZero() {
        assertEquals("1500", MoneyFormat.editText(Money.rub(150_000L)))
        assertEquals("1500,50", MoneyFormat.editText(Money.rub(150_050L)))
        assertEquals("7,05", MoneyFormat.editText(Money.rub(705L)))
    }

    @Test
    fun formatTenths_trimsZeroTenth() {
        assertEquals("6", MoneyFormat.formatTenths(6.04))
        assertEquals("6,5", MoneyFormat.formatTenths(6.49))
        assertEquals("−1,2", MoneyFormat.formatTenths(-1.2))
    }

    @Test
    fun formatPercentPoints_roundsWhole() {
        assertEquals("12%", MoneyFormat.formatPercentPoints(12.4))
        assertEquals("-3%", MoneyFormat.formatPercentPoints(-2.6))
    }

    @Test
    fun formatShare_roundsToWholePercent() {
        assertEquals("45%", MoneyFormat.formatShare(0.45))
        assertEquals("7%", MoneyFormat.formatShare(0.0666))
        assertEquals("120%", MoneyFormat.formatShare(1.2))
    }

    @Test
    fun periodRange_showsLastDayInclusive_andMonthName() {
        val period = ru.nomadbudget.domain.model.SalaryCycle.periodContaining(kotlinx.datetime.LocalDate(2026, 10, 22), TEST_START_DAY)
        assertEquals("05.10 – 04.11", DateFormat.periodRange(period))
        assertEquals("Октябрь", DateFormat.monthName(period.start))
    }

    @Test
    fun formatQuote_twoDecimalsForMidRange() {
        assertEquals("92,80", MoneyFormat.formatQuote(92.8))
        assertEquals("149", MoneyFormat.formatQuote(149.2))
        assertEquals("0,0108", MoneyFormat.formatQuote(0.0108))
    }

    @Test
    fun unitLabel_usesCode_notSymbol() {
        assertEquals("1 USD", MoneyFormat.unitLabel(1L, Currency.USD))
        assertEquals("1 000 RUB", MoneyFormat.unitLabel(1_000L, Currency.RUB))
    }

    @Test
    fun rateText_codesOnBothSides_withScaledUnit() {
        assertEquals("1 USD = 92,46 RUB", MoneyFormat.rateText(Currency.USD, Currency.RUB, 92.46))
        assertEquals("1 000 RUB = 10,82 USD", MoneyFormat.rateText(Currency.RUB, Currency.USD, 10.82, unit = 1_000L))
    }

    @Test
    fun quoteText_followsQuoteOrientation() {
        val quote = ru.nomadbudget.domain.logic.ExchangeAnalyzer.baseQuote(Currency.USD, 92.8)
        assertEquals("1 USD = 92,80 RUB", MoneyFormat.quoteText(quote))
    }

    @Test
    fun perUnitLabel_namesPriceThenUnit() {
        assertEquals("RUB за 1 USD", MoneyFormat.perUnitLabel(1L, Currency.USD, Currency.RUB))
        assertEquals("USD за 1 000 RUB", MoneyFormat.perUnitLabel(1_000L, Currency.RUB, Currency.USD))
    }
}
