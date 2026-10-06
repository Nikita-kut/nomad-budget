package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

data class PlanShare(
    val category: Category,
    val planned: Money,
    val share: Double,
)

data class PlanSummary(
    val incomePlanned: Money,
    val expensePlanned: Money,
    val itemized: Money,
    val freeAmounts: Money,
    val plannedCategories: Int,
    val expenseCategories: Int,
    val shares: List<PlanShare>,
    val savingsTarget: Money = Money.zero(Currency.BASE),
) {
    val free: Money get() = incomePlanned - expensePlanned - savingsTarget

    val allocatedShare: Double?
        get() = if (incomePlanned.minor > 0L) (expensePlanned + savingsTarget).minor.toDouble() / incomePlanned.minor else null

    val savingsShare: Double?
        get() = if (incomePlanned.minor > 0L) savingsTarget.minor.toDouble() / incomePlanned.minor else null

    val isEmpty: Boolean get() = incomePlanned.isZero && expensePlanned.isZero && savingsTarget.isZero
}

data class CategoryHistory(
    val average: Money,
    val previous: Money,
    val months: Int,
)

object PlanCalculator {

    const val HISTORY_MONTHS: Int = 3

    fun summary(budgets: List<CategoryBudget>, savingsTarget: Money = Money.zero(Currency.BASE)): PlanSummary {
        val income = budgets.filter { it.category.kind == CategoryKind.INCOME }
        val expense = budgets.filter { it.category.kind == CategoryKind.EXPENSE }
        val incomePlanned = income.map { it.planned }.sumIn(Currency.BASE)
        val expensePlanned = expense.map { it.planned }.sumIn(Currency.BASE)
        val base = if (incomePlanned.minor > 0L) incomePlanned else expensePlanned
        val shares = expense
            .filter { it.hasPlan }
            .sortedByDescending { it.planned.minor }
            .map { PlanShare(it.category, it.planned, if (base.minor > 0L) it.planned.minor.toDouble() / base.minor else 0.0) }
        return PlanSummary(
            incomePlanned = incomePlanned,
            expensePlanned = expensePlanned,
            itemized = expense.map { it.itemsPlanned }.sumIn(Currency.BASE),
            freeAmounts = expense.map { it.freePlanned }.sumIn(Currency.BASE),
            plannedCategories = expense.count { it.hasPlan },
            expenseCategories = expense.size,
            shares = shares,
            savingsTarget = savingsTarget,
        )
    }

    fun dailyAllowance(budgets: List<CategoryBudget>, daysLeft: Int): Money? {
        if (daysLeft <= 0) return null
        val expense = budgets.filter { it.category.kind == CategoryKind.EXPENSE && it.hasPlan }
        if (expense.isEmpty()) return null
        val remaining = expense.map { it.remainingWhole }.sumIn(Currency.BASE)
        return Money(remaining.minor / daysLeft, Currency.BASE)
    }

    fun history(transactions: List<Transaction>, period: Period, months: Int = HISTORY_MONTHS): Map<String, CategoryHistory> {
        val previous = generateSequence(SalaryCycle.previous(period)) { SalaryCycle.previous(it) }.take(months).toList()
        val active = previous.filter { p -> transactions.any { it.date in p } }
        if (active.isEmpty()) return emptyMap()
        val byPeriod = active.map { p ->
            transactions.filter { it.date in p }.mapNotNull { tx ->
                when (tx) {
                    is Transaction.Expense -> tx.categoryId to tx.amountBase
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
