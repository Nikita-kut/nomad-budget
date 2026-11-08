package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

object BalanceCalculator {

    fun totalInBase(accounts: List<Account>, balances: Map<String, Money>, rates: RateTable): Money = accounts
        .mapNotNull { rates.toBaseOrNull(balances[it.id] ?: Money.zero(it.currency)) }
        .sumIn(Currency.BASE)

    fun balance(account: Account, transactions: List<Transaction>): Money =
        transactions.fold(account.openingBalance) { acc, tx -> acc + effectOn(account, tx) }

    fun balanceBefore(account: Account, transactions: List<Transaction>, date: LocalDate): Money =
        transactions.fold(account.openingBalance) { acc, tx -> if (tx.date < date) acc + effectOn(account, tx) else acc }

    fun balances(accounts: List<Account>, transactions: List<Transaction>): Map<String, Money> {
        val byId = accounts.associateBy { it.id }
        val minor = HashMap<String, Long>(accounts.size * 2)
        accounts.forEach { minor[it.id] = it.openingBalance.minor }
        fun add(id: String, delta: Long) {
            if (id in byId) minor[id] = (minor[id] ?: 0L) + delta
        }
        transactions.forEach { tx ->
            when (tx) {
                is Transaction.Expense -> add(tx.accountId, -tx.amount.minor)
                is Transaction.Income -> add(tx.accountId, tx.amount.minor)
                is Transaction.Transfer -> {
                    add(tx.fromAccountId, -tx.amount.minor)
                    add(tx.toAccountId, tx.amount.minor)
                }
                is Transaction.Exchange -> {
                    add(tx.fromAccountId, -tx.given.minor)
                    add(tx.toAccountId, tx.received.minor)
                }
            }
        }
        return accounts.associate { it.id to Money(minor.getValue(it.id), it.currency) }
    }

    private fun effectOn(account: Account, tx: Transaction): Money = when (tx) {
        is Transaction.Expense -> if (tx.accountId == account.id) -tx.amount else zero(account)
        is Transaction.Income -> if (tx.accountId == account.id) tx.amount else zero(account)
        is Transaction.Transfer -> when (account.id) {
            tx.fromAccountId -> -tx.amount
            tx.toAccountId -> tx.amount
            else -> zero(account)
        }
        is Transaction.Exchange -> when (account.id) {
            tx.fromAccountId -> -tx.given
            tx.toAccountId -> tx.received
            else -> zero(account)
        }
    }

    private fun zero(account: Account): Money = Money.zero(account.currency)
}
