package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate

data class RatePoint(val date: LocalDate, val value: Double)

object RatePairs {

    fun pairs(currencies: List<Currency>): List<Pair<Currency, Currency>> {
        val ordered = listOf(Currency.BASE) + currencies.filter { it != Currency.BASE }.sortedBy { it.code }
        return ordered.flatMapIndexed { i, a -> ordered.drop(i + 1).map { b -> a to b } }
    }

    fun series(from: Currency, to: Currency, history: List<ExchangeRate>): List<RatePoint> {
        val byQuote = history.groupBy { it.quote }.mapValues { (_, list) -> list.sortedBy { it.date } }
        val dates = (listOf(from, to).filter { it != Currency.BASE }.flatMap { c -> byQuote[c].orEmpty().map { it.date } }).distinct().sorted()
        return dates.mapNotNull { date ->
            val fromPer = perUnit(from, date, byQuote) ?: return@mapNotNull null
            val toPer = perUnit(to, date, byQuote) ?: return@mapNotNull null
            RatePoint(date, fromPer / toPer)
        }
    }

    private fun perUnit(currency: Currency, date: LocalDate, byQuote: Map<Currency, List<ExchangeRate>>): Double? =
        if (currency == Currency.BASE) 1.0 else byQuote[currency]?.lastOrNull { it.date <= date }?.basePerUnit
}
