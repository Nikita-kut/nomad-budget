package ru.nomadbudget.domain.logic

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.TransactionKind

object EntryDefaults {

    const val LARGE_LOOKBACK_DAYS: Int = 180
    const val LARGE_FACTOR: Long = 5L
    val LARGE_FLOOR: Money = Money(10_000_000L, Currency.RATE_PIVOT)

    fun largeAmountThreshold(transactions: List<Transaction>, today: LocalDate, kind: TransactionKind, rates: RateTable): Money {
        val floor = rates.toBaseOrNull(LARGE_FLOOR) ?: Money(LARGE_FLOOR.minor, rates.base)
        val since = today.plus(-LARGE_LOOKBACK_DAYS, DateTimeUnit.DAY)
        val largest = transactions
            .filter { it.date >= since && it.kind == kind }
            .maxOfOrNull { it.amountBase.minor } ?: 0L
        return Money(maxOf(largest * LARGE_FACTOR, floor.minor), rates.base)
    }

    fun lastUsedAccountId(transactions: List<Transaction>, activeIds: Set<String>, kind: TransactionKind, categoryId: String? = null): String? {
        val recent = transactions.filter { it.kind == kind }.sortedByDescending { it.date }
        return when (kind) {
            TransactionKind.TRANSFER, TransactionKind.EXCHANGE -> recent
                .firstNotNullOfOrNull { tx -> sourceAccount(tx)?.takeIf { it in activeIds } }
            TransactionKind.EXPENSE, TransactionKind.INCOME -> recent
                .firstOrNull { it.categoryIdOrNull == categoryId && sourceAccount(it) in activeIds }?.let(::sourceAccount)
                ?: recent.firstOrNull { sourceAccount(it) in activeIds }?.let(::sourceAccount)
        }
    }

    private fun sourceAccount(tx: Transaction): String? = when (tx) {
        is Transaction.Expense -> tx.accountId
        is Transaction.Income -> tx.accountId
        is Transaction.Transfer -> tx.fromAccountId
        is Transaction.Exchange -> tx.fromAccountId
    }
}
