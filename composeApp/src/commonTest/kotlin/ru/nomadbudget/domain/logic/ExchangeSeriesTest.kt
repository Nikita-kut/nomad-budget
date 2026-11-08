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

class ExchangeSeriesTest {

    private val d1 = LocalDate(2026, 10, 1)
    private val d2 = LocalDate(2026, 10, 5)
    private val rates = RateTable(listOf(ExchangeRate(Currency.USD, 95.0, d2, RateSource.API)))
    private val history = listOf(ExchangeRate(Currency.USD, 90.0, d1, RateSource.API), ExchangeRate(Currency.USD, 95.0, d2, RateSource.API))

    private fun exchange(id: String, date: LocalDate, given: Money, received: Money) =
        Transaction.Exchange(id, date, "a", "b", given, received, Money.rub(0L))

    @Test
    fun compare_matchesAnalyzeForSameAmounts() {
        val given = Money(10_000L, Currency.USD)
        val received = Money.rub(920_000L)
        val viaTransaction = ExchangeAnalyzer.analyze(exchange("x", d2, given, received), rates)
        val direct = ExchangeAnalyzer.compare(given, received, rates)
        assertEquals(viaTransaction, direct)
        assertEquals(92.0, direct.effectiveRate, absoluteTolerance = 1e-9)
    }

    @Test
    fun orientedQuote_followsOrientationUnit() {
        val orientation = RateQuote(Currency.USD, Currency.RUB, 95.0)
        val direct = ExchangeAnalyzer.orientedQuote(Money(10_000L, Currency.USD), Money.rub(920_000L), orientation)
        assertEquals(Currency.USD, direct.unit)
        assertEquals(92.0, direct.value, absoluteTolerance = 1e-9)
        val reversed = ExchangeAnalyzer.orientedQuote(Money.rub(920_000L), Money(10_000L, Currency.USD), orientation)
        assertEquals(Currency.USD, reversed.unit)
        assertEquals(92.0, reversed.value, absoluteTolerance = 1e-9)
    }

    @Test
    fun series_orientsByLastExchange_andPairsTableRateOfThatDay() {
        val exchanges = listOf(
            exchange("1", d1, Money.rub(910_000L), Money(10_000L, Currency.USD)),
            exchange("2", d2, Money(10_000L, Currency.USD), Money.rub(930_000L)),
        )
        val series = ExchangeAnalyzer.series(exchanges, history, rates).single()
        assertEquals(Currency.USD, series.unit)
        assertEquals(Currency.RUB, series.price)
        assertEquals(listOf(d1, d2), series.points.map { it.date })
        assertEquals(91.0, series.points[0].mine, absoluteTolerance = 1e-9)
        assertEquals(90.0, series.points[0].table)
        assertEquals(93.0, series.points[1].mine, absoluteTolerance = 1e-9)
        assertEquals(95.0, series.points[1].table)
    }
}
