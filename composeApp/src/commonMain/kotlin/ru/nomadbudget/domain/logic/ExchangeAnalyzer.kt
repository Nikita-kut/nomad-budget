package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.Currency

data class ExchangeAnalysis(
    val effectiveRate: Double,
    val referenceRate: Double,
    val spreadPercent: Double,
)

data class RateQuote(val unit: Currency, val price: Currency, val value: Double)

object ExchangeAnalyzer {

    fun quote(exchange: Transaction.Exchange): RateQuote {
        val given = exchange.given
        val received = exchange.received
        return when {
            given.currency == Currency.BASE -> RateQuote(received.currency, given.currency, given.toMajor() / received.toMajor())
            received.currency == Currency.BASE -> RateQuote(given.currency, received.currency, received.toMajor() / given.toMajor())
            else -> {
                val direct = received.toMajor() / given.toMajor()
                if (direct >= 1.0) RateQuote(given.currency, received.currency, direct) else RateQuote(received.currency, given.currency, 1.0 / direct)
            }
        }
    }

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
