package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

enum class BudgetStatus { OK, WARNING, OVER }

data class BudgetLine(val categoryId: String, val planned: Money)

data class CategoryBudget(
    val category: Category,
    val planned: Money,
    val fact: Money,
    val transactionCount: Int,
) {
    val remaining: Money get() = planned - fact

    val progress: Double
        get() = when {
            planned.minor > 0L -> fact.minor.toDouble() / planned.minor
            fact.minor > 0L -> 1.0
            else -> 0.0
        }

    val status: BudgetStatus
        get() = when {
            fact > planned -> BudgetStatus.OVER
            progress >= BudgetCalculator.WARNING_THRESHOLD -> BudgetStatus.WARNING
            else -> BudgetStatus.OK
        }
}

data class MonthSummary(
    val incomePlanned: Money,
    val incomeFact: Money,
    val expensePlanned: Money,
    val expenseFact: Money,
    val savedToSavings: Money,
) {
    val remaining: Money get() = incomeFact - expenseFact - savedToSavings
}

object BudgetCalculator {

    const val WARNING_THRESHOLD: Double = 0.85

    fun categoryBudgets(
        categories: List<Category>,
        lines: List<BudgetLine>,
        transactions: List<Transaction>,
        period: Period,
    ): List<CategoryBudget> {
        val plannedByCategory = lines.associate { it.categoryId to it.planned }
        val inPeriod = transactions.filter { it.date in period }
        return categories
            .sortedBy { it.sortOrder }
            .map { category ->
                val related = inPeriod.filter { it.belongsTo(category.id) }
                CategoryBudget(
                    category = category,
                    planned = plannedByCategory[category.id] ?: Money.zero(Currency.BASE),
                    fact = related.map { it.amountBase }.sumIn(Currency.BASE),
                    transactionCount = related.size,
                )
            }
    }

    fun monthSummary(
        budgets: List<CategoryBudget>,
        transactions: List<Transaction>,
        accounts: List<Account>,
        period: Period,
    ): MonthSummary {
        val income = budgets.filter { it.category.kind == CategoryKind.INCOME }
        val expense = budgets.filter { it.category.kind == CategoryKind.EXPENSE }
        val savingsIds = accounts.filter { it.isSavings }.map { it.id }.toSet()
        val saved = transactions
            .filterIsInstance<Transaction.Transfer>()
            .filter { it.date in period && it.toAccountId in savingsIds && it.fromAccountId !in savingsIds }
            .map { it.amountBase }
            .sumIn(Currency.BASE)
        return MonthSummary(
            incomePlanned = income.map { it.planned }.sumIn(Currency.BASE),
            incomeFact = income.map { it.fact }.sumIn(Currency.BASE),
            expensePlanned = expense.map { it.planned }.sumIn(Currency.BASE),
            expenseFact = expense.map { it.fact }.sumIn(Currency.BASE),
            savedToSavings = saved,
        )
    }

    private fun Transaction.belongsTo(categoryId: String): Boolean = when (this) {
        is Transaction.Expense -> this.categoryId == categoryId
        is Transaction.Income -> this.categoryId == categoryId
        is Transaction.Transfer, is Transaction.Exchange -> false
    }
}
