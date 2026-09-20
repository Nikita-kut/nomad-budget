package ru.nomadbudget.domain.model

enum class AccountKind { CARD, ACCOUNT, CASH, SAVINGS, INVESTMENT }

data class Account(
    val id: String,
    val name: String,
    val currency: Currency,
    val kind: AccountKind,
    val isSavings: Boolean,
    val openingBalance: Money,
    val sortOrder: Int = 0,
) {
    init {
        require(openingBalance.currency == currency) { "Валюта начального остатка не совпадает со счётом" }
    }
}
