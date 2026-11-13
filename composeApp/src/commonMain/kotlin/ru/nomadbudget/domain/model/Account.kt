package ru.nomadbudget.domain.model

enum class AccountKind { CARD, ACCOUNT, CASH, SAVINGS, INVESTMENT }

enum class AccountRole { LIFE, FUND, CUSHION }

data class Account(
    val id: String,
    val name: String,
    val currency: Currency,
    val kind: AccountKind,
    val isSavings: Boolean,
    val openingBalance: Money,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val isFund: Boolean = false,
    val fundMonthly: Money? = null,
    val fundGoal: Money? = null,
    val fundDay: Int? = null,
    val archivedOn: kotlinx.datetime.LocalDate? = null,
) {
    init {
        require(openingBalance.currency == currency) { "Валюта начального остатка не совпадает со счётом" }
        require(!(isFund && isSavings)) { "Счёт не может быть и копилкой, и подушкой" }
    }

    fun activeOn(date: kotlinx.datetime.LocalDate): Boolean = !isArchived || (archivedOn != null && archivedOn >= date)

    val role: AccountRole
        get() = when {
            isSavings -> AccountRole.CUSHION
            isFund -> AccountRole.FUND
            else -> AccountRole.LIFE
        }
}
