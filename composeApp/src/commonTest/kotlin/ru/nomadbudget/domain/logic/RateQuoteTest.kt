package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.TestCurrencies
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class RateQuoteTest {

    private fun exchange(given: Money, received: Money) = Transaction.Exchange(
        id = "x", date = LocalDate(2026, 10, 7), fromAccountId = "a", toAccountId = "b",
        given = given, received = received, amountBase = Money.rub(0L),
    )

    @Test
    fun rublesToDollars_quotedAsRublesPerDollar() {
        val quote = ExchangeAnalyzer.quote(exchange(Money.rub(9_280_000L), Money(100_000L, Currency.USD)))
        assertEquals(Currency.USD, quote.unit)
        assertEquals(Currency.RUB, quote.price)
        assertEquals(92.8, quote.value, absoluteTolerance = 1e-9)
    }

    @Test
    fun dollarsToRubles_sameDirection() {
        val quote = ExchangeAnalyzer.quote(exchange(Money(10_000L, Currency.USD), Money.rub(915_000L)))
        assertEquals(Currency.USD, quote.unit)
        assertEquals(91.5, quote.value, absoluteTolerance = 1e-9)
    }

    @Test
    fun crossPair_unitIsTheStrongerCurrency() {
        val quote = ExchangeAnalyzer.quote(exchange(Money(30_000L, TestCurrencies.JPY), Money(20_000L, Currency.USD)))
        assertEquals(Currency.USD, quote.unit)
        assertEquals(TestCurrencies.JPY, quote.price)
        assertEquals(150.0, quote.value, absoluteTolerance = 1e-9)
    }
}
