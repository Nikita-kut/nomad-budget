package ru.nomadbudget.domain.repository

import ru.nomadbudget.domain.model.Currency

interface BaseCurrencySource {
    val current: Currency
    fun set(currency: Currency)
}

class InMemoryBaseCurrency(initial: Currency = Currency.DEFAULT_BASE) : BaseCurrencySource {
    override var current: Currency = initial
        private set

    override fun set(currency: Currency) {
        current = currency
    }
}
