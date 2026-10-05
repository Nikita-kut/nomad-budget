package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
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
) {
    val free: Money get() = incomePlanned - expensePlanned

    val allocatedShare: Double?
        get() = if (incomePlanned.minor > 0L) expensePlanned.minor.toDouble() / incomePlanned.minor else null

    val isEmpty: Boolean get() = incomePlanned.isZero && expensePlanned.isZero
}

object PlanCalculator {

    fun summary(budgets: List<CategoryBudget>): PlanSummary {
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
        )
    }
}
