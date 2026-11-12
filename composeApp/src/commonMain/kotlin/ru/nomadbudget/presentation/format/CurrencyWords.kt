package ru.nomadbudget.presentation.format

import ru.nomadbudget.domain.model.Currency

object CurrencyWords {

    private val prepositional = mapOf("RUB" to "рублях", "USD" to "долларах", "EUR" to "евро")

    fun inCurrency(currency: Currency): String = "в " + (prepositional[currency.code] ?: currency.code)

    fun columnIn(currency: Currency): String = "В ${currency.code}"
}
