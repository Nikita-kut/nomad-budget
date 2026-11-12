package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateTable
import kotlin.math.roundToLong

class MarketRates(history: List<ExchangeRate>, private val current: RateTable) {

    private val byQuote: Map<Currency, List<ExchangeRate>> = history.groupBy { it.quote }.mapValues { (_, list) -> list.sortedBy { it.date } }

    fun basePerUnit(currency: Currency, date: LocalDate): Double? {
        if (currency == current.base) return 1.0
        val known = byQuote[currency].orEmpty()
        return onOrBefore(known, date)?.basePerUnit ?: current.rateFor(currency)?.basePerUnit ?: known.firstOrNull()?.basePerUnit
    }

    fun knows(currency: Currency): Boolean = currency == current.base || current.hasRate(currency) || currency in byQuote

    fun toBase(money: Money, date: LocalDate): Money? = basePerUnit(money.currency, date)?.let { perUnit ->
        Money((money.toMajor() * perUnit * current.base.minorFactor).roundToLong(), current.base)
    }

    private fun onOrBefore(sorted: List<ExchangeRate>, date: LocalDate): ExchangeRate? {
        var low = 0
        var high = sorted.size - 1
        var found: ExchangeRate? = null
        while (low <= high) {
            val middle = (low + high) / 2
            if (sorted[middle].date <= date) {
                found = sorted[middle]
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return found
    }
}
