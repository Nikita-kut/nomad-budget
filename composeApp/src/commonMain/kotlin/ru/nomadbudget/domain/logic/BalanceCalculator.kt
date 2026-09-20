package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction

object BalanceCalculator {

    fun balance(account: Account, transactions: List<Transaction>): Money =
        transactions.fold(account.openingBalance) { acc, tx -> acc + effectOn(account, tx) }

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
