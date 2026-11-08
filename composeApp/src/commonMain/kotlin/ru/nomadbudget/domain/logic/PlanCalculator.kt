package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

data class CategoryHistory(
    val average: Money,
    val previous: Money,
    val months: Int,
)

object PlanCalculator {

    const val HISTORY_MONTHS: Int = 3
    const val USUAL_DIFF_THRESHOLD: Double = 0.2

    fun differsFromUsual(planned: Money, history: CategoryHistory): Boolean {
        if (history.average.minor <= 0L) return false
        return kotlin.math.abs(history.average.minor - planned.minor).toDouble() / history.average.minor > USUAL_DIFF_THRESHOLD
    }

    fun history(
        transactions: List<Transaction>,
        period: Period,
        startDay: Int,
        accounts: List<Account> = emptyList(),
        months: Int = HISTORY_MONTHS,
    ): Map<String, CategoryHistory> {
        val accountsById = accounts.associateBy { it.id }
        val previous = generateSequence(SalaryCycle.previous(period, startDay)) { SalaryCycle.previous(it, startDay) }.take(months).toList()
        val active = previous.filter { p -> transactions.any { it.date in p } }
        if (active.isEmpty()) return emptyMap()
        val byPeriod = active.map { p ->
            transactions.filter { it.date in p }.mapNotNull { tx ->
                when (tx) {
                    is Transaction.Expense -> (tx.categoryId to tx.amountBase).takeIf { BudgetCalculator.isEnvelopeSpend(tx, accountsById) }
                    is Transaction.Income -> tx.categoryId to tx.amountBase
                    is Transaction.Transfer, is Transaction.Exchange -> null
                }
            }.groupBy({ it.first }, { it.second }).mapValues { (_, list) -> list.sumIn(Currency.BASE) }
        }
        val lastActive = if (transactions.any { it.date in previous.first() }) byPeriod.first() else emptyMap()
        val categories = byPeriod.flatMap { it.keys }.toSet()
        return categories.associateWith { id ->
            val total = byPeriod.mapNotNull { it[id] }.sumIn(Currency.BASE)
            CategoryHistory(
                average = Money(total.minor / active.size, Currency.BASE),
                previous = lastActive[id] ?: Money.zero(Currency.BASE),
                months = active.size,
            )
        }
    }
}
