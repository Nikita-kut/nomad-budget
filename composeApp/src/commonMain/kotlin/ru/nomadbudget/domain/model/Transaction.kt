package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate

sealed interface Transaction {
    val id: String
    val date: LocalDate
    val note: String
    val amountBase: Money

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
    ) : Transaction

    data class Transfer(
        override val id: String,
        override val date: LocalDate,
        val fromAccountId: String,
        val toAccountId: String,
        val amount: Money,
        override val amountBase: Money,
        override val note: String = "",
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
    ) : Transaction {
        init {
            require(given.currency != received.currency) { "Обмен требует разные валюты" }
        }

        val effectiveRate: Double get() = received.toMajor() / given.toMajor()
    }
}
