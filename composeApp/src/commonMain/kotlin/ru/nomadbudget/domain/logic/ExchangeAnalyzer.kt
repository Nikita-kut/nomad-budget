package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Transaction

data class ExchangeAnalysis(
    val effectiveRate: Double,
    val referenceRate: Double,
    val spreadPercent: Double,
)

object ExchangeAnalyzer {

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
