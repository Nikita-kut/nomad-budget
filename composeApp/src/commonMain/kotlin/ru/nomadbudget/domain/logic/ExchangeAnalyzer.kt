package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.Currency

data class ExchangeAnalysis(
    val effectiveRate: Double,
    val referenceRate: Double,
    val spreadPercent: Double,
)

data class RateQuote(val unit: Currency, val price: Currency, val value: Double) {
    val inverted: Boolean get() = unit == Currency.BASE
}

object ExchangeAnalyzer {

    fun quote(exchange: Transaction.Exchange): RateQuote =
        pairQuote(exchange.given.currency, exchange.received.currency, exchange.received.toMajor() / exchange.given.toMajor())

    fun pairQuote(from: Currency, to: Currency, toPerFrom: Double): RateQuote =
        if (toPerFrom >= 1.0) RateQuote(from, to, toPerFrom) else RateQuote(to, from, 1.0 / toPerFrom)

    fun baseQuote(currency: Currency, basePerUnit: Double): RateQuote =
        if (basePerUnit >= 1.0) RateQuote(currency, Currency.BASE, basePerUnit) else RateQuote(Currency.BASE, currency, 1.0 / basePerUnit)

    fun quoteText(quote: RateQuote, format: (Double) -> String): String = "1 ${quote.unit.symbol} = ${format(quote.value)} ${quote.price.symbol}"

    fun analyze(exchange: Transaction.Exchange, rates: RateTable): ExchangeAnalysis {
        val reference = rates.cross(exchange.given.currency, exchange.received.currency)
        val effective = exchange.effectiveRate
        return ExchangeAnalysis(
            effectiveRate = effective,
            referenceRate = reference,
            spreadPercent = (reference - effective) / reference * PERCENT,
        )
    }

    private const val PERCENT = 100.0
}
