package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Transaction

data class ExchangeAnalysis(
    val effectiveRate: Double,
    val referenceRate: Double,
    val spreadPercent: Double,
)

data class RateQuote(val unit: Currency, val price: Currency, val value: Double)

data class ExchangePoint(val date: LocalDate, val mine: Double, val table: Double?)

data class ExchangeSeries(val unit: Currency, val price: Currency, val points: List<ExchangePoint>)

object ExchangeAnalyzer {

    fun quote(exchange: Transaction.Exchange): RateQuote = quote(exchange.given, exchange.received)

    fun quote(given: Money, received: Money): RateQuote =
        pairQuote(given.currency, received.currency, received.toMajor() / given.toMajor())

    fun pairQuote(from: Currency, to: Currency, toPerFrom: Double): RateQuote =
        if (toPerFrom >= 1.0) RateQuote(from, to, toPerFrom) else RateQuote(to, from, 1.0 / toPerFrom)

    fun baseQuote(currency: Currency, basePerUnit: Double, base: Currency): RateQuote =
        if (basePerUnit >= 1.0) RateQuote(currency, base, basePerUnit) else RateQuote(base, currency, 1.0 / basePerUnit)

    fun orientedQuote(given: Money, received: Money, orientation: RateQuote): RateQuote {
        val effective = received.toMajor() / given.toMajor()
        return if (orientation.unit == given.currency) RateQuote(given.currency, received.currency, effective) else RateQuote(received.currency, given.currency, 1.0 / effective)
    }

    fun analyze(exchange: Transaction.Exchange, rates: RateTable): ExchangeAnalysis = compare(exchange.given, exchange.received, rates)

    fun compare(given: Money, received: Money, rates: RateTable): ExchangeAnalysis {
        val reference = rates.cross(given.currency, received.currency)
        val effective = received.toMajor() / given.toMajor()
        return ExchangeAnalysis(
            effectiveRate = effective,
            referenceRate = reference,
            spreadPercent = (reference - effective) / reference * PERCENT,
        )
    }

    fun series(exchanges: List<Transaction.Exchange>, rateHistory: List<ExchangeRate>, rates: RateTable): List<ExchangeSeries> = exchanges
        .groupBy { setOf(it.given.currency, it.received.currency) }
        .values
        .sortedByDescending { it.size }
        .map { list ->
            val sorted = list.sortedBy { it.date }
            val orientation = quote(sorted.last())
            val market = RatePairs.series(orientation.unit, orientation.price, rateHistory, rates.base)
            val current = rates.cross(orientation.unit, orientation.price)
            ExchangeSeries(
                unit = orientation.unit,
                price = orientation.price,
                points = sorted.map { tx ->
                    ExchangePoint(
                        date = tx.date,
                        mine = orientedQuote(tx.given, tx.received, orientation).value,
                        table = market.lastOrNull { it.date <= tx.date }?.value ?: current,
                    )
                },
            )
        }

    private const val PERCENT = 100.0
}
