package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate

enum class TransactionKind { EXPENSE, INCOME, TRANSFER, EXCHANGE }

sealed interface Transaction {
    val id: String
    val date: LocalDate
    val note: String
    val amountBase: Money
    val pending: Boolean

    val kind: TransactionKind
        get() = when (this) {
            is Expense -> TransactionKind.EXPENSE
            is Income -> TransactionKind.INCOME
            is Transfer -> TransactionKind.TRANSFER
            is Exchange -> TransactionKind.EXCHANGE
        }

    val ownAmount: Money
        get() = when (this) {
            is Expense -> amount
            is Income -> amount
            is Transfer -> amount
            is Exchange -> given
        }

    val accountIds: Set<String>
        get() = when (this) {
            is Expense -> setOf(accountId)
            is Income -> setOf(accountId)
            is Transfer -> setOf(fromAccountId, toAccountId)
            is Exchange -> setOf(fromAccountId, toAccountId)
        }

    val categoryIdOrNull: String?
        get() = when (this) {
            is Expense -> categoryId
            is Income -> categoryId
            is Transfer, is Exchange -> null
        }

    fun touches(accountId: String): Boolean = accountId in accountIds

    fun touchesAny(ids: Set<String>): Boolean = accountIds.any { it in ids }

    data class Expense(
        override val id: String,
        override val date: LocalDate,
        val accountId: String,
        val amount: Money,
        val categoryId: String,
        val subcategoryId: String?,
        override val amountBase: Money,
        val rateSource: RateSource,
        override val note: String = "",
        val debtId: String? = null,
        override val pending: Boolean = false,
        val debtPrincipal: Money? = null,
        val debtEarly: Boolean = false,
    ) : Transaction

    data class Income(
        override val id: String,
        override val date: LocalDate,
        val accountId: String,
        val amount: Money,
        val categoryId: String,
        override val amountBase: Money,
        val rateSource: RateSource,
        override val note: String = "",
        override val pending: Boolean = false,
    ) : Transaction

    data class Transfer(
        override val id: String,
        override val date: LocalDate,
        val fromAccountId: String,
        val toAccountId: String,
        val amount: Money,
        override val amountBase: Money,
        override val note: String = "",
        override val pending: Boolean = false,
    ) : Transaction

    data class Exchange(
        override val id: String,
        override val date: LocalDate,
        val fromAccountId: String,
        val toAccountId: String,
        val given: Money,
        val received: Money,
        override val amountBase: Money,
        override val note: String = "",
        override val pending: Boolean = false,
    ) : Transaction {
        init {
            require(given.currency != received.currency) { "Обмен требует разные валюты" }
        }

        val effectiveRate: Double get() = received.toMajor() / given.toMajor()
    }
}
