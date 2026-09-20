package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExchangeAnalyzerTest {

    private val date = LocalDate(2026, 9, 7)
    private val rates = RateTable(
        listOf(
            ExchangeRate(Currency.USD, 84.1975, date, RateSource.API),
            ExchangeRate(Currency.VND, 0.00328422, date, RateSource.API),
        ),
    )

    @Test
    fun atmWithdrawal_givesWorseRateThanCentralBank() {
        val exchange = Transaction.Exchange(
            "x1", date, "usd_card", "vnd_cash",
            given = Money(20_000L, Currency.USD), received = Money(5_180_000L, Currency.VND),
            amountBase = Money(1_683_950L, Currency.RUB),
        )
        val analysis = ExchangeAnalyzer.analyze(exchange, rates)
        assertEquals(25_900.0, analysis.effectiveRate, absoluteTolerance = 0.01)
        assertEquals(25_637.0, analysis.referenceRate, absoluteTolerance = 1.0)
        assertTrue(analysis.spreadPercent < 0.0)
        assertEquals(-1.03, analysis.spreadPercent, absoluteTolerance = 0.05)
    }

    @Test
    fun receivingLessThanReference_isPositiveSpread() {
        val exchange = Transaction.Exchange(
            "x2", date, "usd_cash", "vnd_cash",
            given = Money(10_000L, Currency.USD), received = Money(2_500_000L, Currency.VND),
            amountBase = Money(841_975L, Currency.RUB),
        )
        val analysis = ExchangeAnalyzer.analyze(exchange, rates)
        assertTrue(analysis.spreadPercent > 0.0)
    }
}
