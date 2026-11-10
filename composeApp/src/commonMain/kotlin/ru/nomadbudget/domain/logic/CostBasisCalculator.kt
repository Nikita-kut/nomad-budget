package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Transaction
import kotlin.math.roundToLong

data class CostBasis(
    val effectiveBase: Map<String, Money>,
    val averageCost: Map<String, RateQuote>,
) {
    fun applyTo(transactions: List<Transaction>): List<Transaction> {
        if (effectiveBase.isEmpty()) return transactions
        return transactions.map { tx ->
            val base = effectiveBase[tx.id]
            if (base == null || base == tx.amountBase) {
                tx
            } else {
                when (tx) {
                    is Transaction.Expense -> tx.copy(amountBase = base)
                    is Transaction.Income -> tx.copy(amountBase = base)
                    is Transaction.Transfer -> tx.copy(amountBase = base)
                    is Transaction.Exchange -> tx.copy(amountBase = base)
                }
            }
        }
    }

    companion object {
        val EMPTY: CostBasis = CostBasis(emptyMap(), emptyMap())
    }
}

object CostBasisCalculator {

    fun calculate(
        accounts: List<Account>,
        transactions: List<Transaction>,
        rateHistory: List<ExchangeRate>,
        rates: RateTable,
    ): CostBasis {
        if (accounts.none { it.currency != Currency.BASE }) return CostBasis.EMPTY
        val market = MarketRates(rateHistory, rates)
        val foreign = accounts.filter { it.currency != Currency.BASE && market.knows(it.currency) }
        if (foreign.isEmpty()) return CostBasis.EMPTY
        val foreignIds = foreign.map { it.id }.toSet()
        val ordered = transactions.sortedWith(compareBy<Transaction> { it.date }.thenBy { sameDayRank(it, foreignIds) }.thenBy { it.id })
        val pools = HashMap<String, Pool>(foreign.size * 2)
        foreign.forEach { account ->
            val pool = Pool(account.currency)
            val openedOn = ordered.firstOrNull { it.touches(account.id) }?.date
            val opening = account.openingBalance
            if (opening.minor > 0L) {
                val value = openedOn?.let { market.toBase(opening, it) } ?: rates.toBaseOrNull(opening) ?: Money.rub(0L)
                pool.add(opening.minor, value.minor)
            } else {
                pool.units = opening.minor
            }
            pools[account.id] = pool
        }
        val effective = HashMap<String, Money>()
        ordered.forEach { tx ->
            when (tx) {
                is Transaction.Income -> pools.matching(tx.accountId, tx.amount)?.let { pool ->
                    pool.add(tx.amount.minor, valueOf(tx.amount, tx.date, market, tx).minor)
                }
                is Transaction.Expense -> pools.matching(tx.accountId, tx.amount)?.let { pool ->
                    effective[tx.id] = Money.rub(pool.remove(tx.amount, tx.date, market, tx))
                }
                is Transaction.Transfer -> {
                    val from = pools.matching(tx.fromAccountId, tx.amount)
                    val to = pools.matching(tx.toAccountId, tx.amount)
                    if (from != null || to != null) {
                        val cost = from?.remove(tx.amount, tx.date, market, tx) ?: valueOf(tx.amount, tx.date, market, tx).minor
                        to?.add(tx.amount.minor, cost)
                        effective[tx.id] = Money.rub(cost)
                    }
                }
                is Transaction.Exchange -> {
                    val from = pools.matching(tx.fromAccountId, tx.given)
                    val to = pools.matching(tx.toAccountId, tx.received)
                    if (from != null || to != null) {
                        val removed = from?.remove(tx.given, tx.date, market, tx)
                        val cost = when {
                            tx.given.currency == Currency.BASE -> tx.given.minor
                            tx.received.currency == Currency.BASE -> tx.received.minor
                            removed != null -> removed
                            else -> valueOf(tx.given, tx.date, market, tx).minor
                        }
                        to?.add(tx.received.minor, cost)
                        effective[tx.id] = Money.rub(cost)
                    }
                }
            }
        }
        val average = foreign.mapNotNull { account ->
            val pool = pools.getValue(account.id)
            pool.basePerUnit()?.let { account.id to ExchangeAnalyzer.baseQuote(account.currency, it) }
        }.toMap()
        return CostBasis(effective, average)
    }

    private fun sameDayRank(tx: Transaction, foreignIds: Set<String>): Int = when (tx) {
        is Transaction.Income -> RANK_INCOME
        is Transaction.Exchange -> if (tx.fromAccountId in foreignIds) RANK_EXCHANGE_FOREIGN else RANK_EXCHANGE_BASE
        is Transaction.Transfer -> RANK_TRANSFER
        is Transaction.Expense -> RANK_EXPENSE
    }

    private fun Map<String, Pool>.matching(accountId: String, amount: Money): Pool? = this[accountId]?.takeIf { it.currency == amount.currency }

    private fun valueOf(part: Money, date: LocalDate, market: MarketRates, tx: Transaction): Money =
        market.toBase(part, date) ?: storedShare(part, tx)

    private fun storedShare(part: Money, tx: Transaction): Money {
        val own = tx.ownAmount
        if (own.currency != part.currency || own.minor <= 0L) return Money.rub(0L)
        return Money.rub((tx.amountBase.minor.toDouble() * part.minor / own.minor).roundToLong())
    }

    private class Pool(val currency: Currency) {
        var units: Long = 0L
        var cost: Long = 0L

        fun add(amount: Long, value: Long) {
            val after = units + amount
            cost = when {
                units >= 0L -> cost + value
                after > 0L -> (value.toDouble() * after / amount).roundToLong()
                else -> 0L
            }
            units = after
        }

        fun remove(amount: Money, date: LocalDate, market: MarketRates, tx: Transaction): Long {
            val covered = amount.minor.coerceAtMost(units).coerceAtLeast(0L)
            val fromPool = when {
                covered == 0L -> 0L
                covered == units -> cost
                else -> (cost.toDouble() * covered / units).roundToLong()
            }
            val uncovered = amount.minor - covered
            val atMarket = if (uncovered > 0L) valueOf(Money(uncovered, currency), date, market, tx).minor else 0L
            cost -= fromPool
            units -= amount.minor
            return fromPool + atMarket
        }

        fun basePerUnit(): Double? {
            if (units <= 0L || cost <= 0L) return null
            return (cost.toDouble() / Currency.BASE.minorFactor) / (units.toDouble() / currency.minorFactor)
        }
    }

    private const val RANK_INCOME = 0
    private const val RANK_EXCHANGE_BASE = 1
    private const val RANK_EXCHANGE_FOREIGN = 2
    private const val RANK_TRANSFER = 3
    private const val RANK_EXPENSE = 4
}
