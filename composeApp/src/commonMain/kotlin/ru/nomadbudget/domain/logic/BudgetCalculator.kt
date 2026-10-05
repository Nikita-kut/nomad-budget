package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

enum class BudgetStatus { OK, WARNING, OVER }

data class BudgetLine(
    val categoryId: String,
    val planned: Money,
    val subcategoryId: String? = null,
)

data class SubcategoryBudget(
    val subcategory: Subcategory?,
    val planned: Money?,
    val fact: Money,
) {
    val remaining: Money? get() = planned?.minus(fact)
}

data class CategoryBudget(
    val category: Category,
    val planned: Money,
    val fact: Money,
    val transactionCount: Int,
    val items: List<SubcategoryBudget> = emptyList(),
    val plannedByItems: Boolean = false,
    val freePlanned: Money = Money.zero(Currency.BASE),
    val itemsPlanned: Money = Money.zero(Currency.BASE),
) {
    val remaining: Money get() = planned - fact

    val hasPlan: Boolean get() = planned.minor > 0L

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
    val takenFromSavings: Money,
) {
    val netSaved: Money get() = savedToSavings - takenFromSavings

    val remaining: Money get() = incomeFact - expenseFact - netSaved
}

object BudgetCalculator {

    const val WARNING_THRESHOLD: Double = 0.85

    fun categoryBudgets(
        categories: List<Category>,
        lines: List<BudgetLine>,
        transactions: List<Transaction>,
        period: Period,
        subcategories: List<Subcategory> = emptyList(),
    ): List<CategoryBudget> {
        val subcategoriesById = subcategories.associateBy { it.id }
        val inPeriod = transactions.filter { it.date in period }
        return categories
            .sortedBy { it.sortOrder }
            .map { category ->
                val related = inPeriod.filter { it.belongsTo(category.id) }
                val categoryLines = lines.filter { it.categoryId == category.id }
                val itemLines = categoryLines.filter { it.subcategoryId != null }
                val items = buildItems(related, itemLines, subcategoriesById)
                val plannedByItems = itemLines.isNotEmpty()
                val itemsPlanned = itemLines.map { it.planned }.sumIn(Currency.BASE)
                val freePlanned = categoryLines.firstOrNull { it.subcategoryId == null }?.planned ?: Money.zero(Currency.BASE)
                val planned = freePlanned + itemsPlanned
                CategoryBudget(
                    category = category,
                    planned = planned,
                    fact = related.map { it.amountBase }.sumIn(Currency.BASE),
                    transactionCount = related.size,
                    items = items,
                    plannedByItems = plannedByItems,
                    freePlanned = freePlanned,
                    itemsPlanned = itemsPlanned,
                )
            }
    }

    private fun buildItems(
        related: List<Transaction>,
        itemLines: List<BudgetLine>,
        subcategoriesById: Map<String, Subcategory>,
    ): List<SubcategoryBudget> {
        val factBySubcategory = related
            .filterIsInstance<Transaction.Expense>()
            .groupBy { it.subcategoryId }
            .mapValues { (_, list) -> list.map { it.amountBase }.sumIn(Currency.BASE) }
        val plannedBySubcategory = itemLines.associate { it.subcategoryId to it.planned }
        val ids = (plannedBySubcategory.keys + factBySubcategory.keys).filterNotNull().distinct()
        if (ids.isEmpty() && factBySubcategory[null] == null) return emptyList()
        val items = ids.map { id ->
            SubcategoryBudget(
                subcategory = subcategoriesById[id],
                planned = plannedBySubcategory[id],
                fact = factBySubcategory[id] ?: Money.zero(Currency.BASE),
            )
        }.sortedWith(compareByDescending<SubcategoryBudget> { it.planned?.minor ?: -1L }.thenBy { it.subcategory?.name.orEmpty() })
        val withoutSubcategory = factBySubcategory[null]?.let { SubcategoryBudget(subcategory = null, planned = null, fact = it) }
        return items + listOfNotNull(withoutSubcategory)
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
        val transfers = transactions.filterIsInstance<Transaction.Transfer>().filter { it.date in period }
        val saved = transfers
            .filter { it.toAccountId in savingsIds && it.fromAccountId !in savingsIds }
            .map { it.amountBase }
            .sumIn(Currency.BASE)
        val taken = transfers
            .filter { it.fromAccountId in savingsIds && it.toAccountId !in savingsIds }
            .map { it.amountBase }
            .sumIn(Currency.BASE)
        return MonthSummary(
            incomePlanned = income.map { it.planned }.sumIn(Currency.BASE),
            incomeFact = income.map { it.fact }.sumIn(Currency.BASE),
            expensePlanned = expense.map { it.planned }.sumIn(Currency.BASE),
            expenseFact = expense.map { it.fact }.sumIn(Currency.BASE),
            savedToSavings = saved,
            takenFromSavings = taken,
        )
    }

    private fun Transaction.belongsTo(categoryId: String): Boolean = when (this) {
        is Transaction.Expense -> this.categoryId == categoryId
        is Transaction.Income -> this.categoryId == categoryId
        is Transaction.Transfer, is Transaction.Exchange -> false
    }
}
