package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate

object DefaultRates {

    private val asOf = LocalDate(2026, 9, 19)

    val rates: List<ExchangeRate> = listOf(
        ExchangeRate(Currency.USD, basePerUnit = 84.1975, date = asOf, source = RateSource.MANUAL),
    )

    fun table(): RateTable = RateTable(rates)

    fun fill(loaded: List<ExchangeRate>): RateTable {
        val present = loaded.map { it.quote }.toSet()
        return RateTable(loaded + rates.filter { it.quote !in present })
    }
}
