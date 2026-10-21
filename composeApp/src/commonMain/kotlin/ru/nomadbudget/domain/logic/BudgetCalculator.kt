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

enum class BudgetStatus { OK, WARNING, DONE, OVER_SLIGHTLY, OVER }

enum class Pace { ON_TRACK, AHEAD }

data class BudgetLine(
    val categoryId: String,
    val planned: Money,
    val subcategoryId: String? = null,
    val fromSavings: Boolean = false,
)

data class SubcategoryBudget(
    val subcategory: Subcategory?,
    val planned: Money?,
    val fact: Money,
    val fromSavings: Boolean = false,
) {
    val remaining: Money? get() = planned?.minus(fact)

    val remainingWhole: Money? get() = planned?.let { it.withoutFraction() - fact.withoutFraction() }
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
    val freeFromSavings: Boolean = false,
    val fromSavingsPlanned: Money = Money.zero(Currency.BASE),
    val factFromSavings: Money = Money.zero(Currency.BASE),
) {
    val incomePlanned: Money get() = planned - fromSavingsPlanned

    val incomeFact: Money get() = fact - factFromSavings

    val unplannedFromSavings: Money get() = if (factFromSavings > fromSavingsPlanned) factFromSavings - fromSavingsPlanned else Money.zero(Currency.BASE)

    val hasSavingsPart: Boolean get() = !fromSavingsPlanned.isZero || !factFromSavings.isZero

    val remaining: Money get() = incomePlanned - incomeFact

    val remainingWhole: Money get() = incomePlanned.withoutFraction() - incomeFact.withoutFraction()

    val hasPlan: Boolean get() = planned.minor > 0L

    val isIdle: Boolean get() = !hasPlan && fact.isZero

    fun expectedByNow(elapsedShare: Double): Money {
        val share = elapsedShare.coerceIn(0.0, 1.0)
        val settled = items
            .filter { !it.fromSavings && it.subcategory != null && it.planned != null && it.fact.wholeUnits >= it.planned.wholeUnits }
            .mapNotNull { it.planned }
            .sumIn(Currency.BASE)
        val flexible = incomePlanned - settled
        return settled + Money((flexible.minor * share).toLong(), Currency.BASE)
    }

    fun pace(elapsedShare: Double): Pace {
        if (incomePlanned.minor <= 0L) return Pace.ON_TRACK
        val expected = expectedByNow(elapsedShare)
        val tolerance = (incomePlanned.wholeUnits * BudgetCalculator.PACE_TOLERANCE).toLong().coerceAtLeast(1L)
        return if (incomeFact.wholeUnits - expected.wholeUnits > tolerance) Pace.AHEAD else Pace.ON_TRACK
    }

    fun aheadBy(elapsedShare: Double): Money = (incomeFact.withoutFraction() - expectedByNow(elapsedShare).withoutFraction())

    private fun isSlightOverrun(): Boolean {
        val plannedWhole = incomePlanned.wholeUnits
        if (plannedWhole <= 0L) return false
        return (incomeFact.wholeUnits - plannedWhole).toDouble() / plannedWhole <= BudgetCalculator.SLIGHT_OVERRUN
    }

    val progress: Double
        get() = when {
            incomePlanned.wholeUnits > 0L -> incomeFact.wholeUnits.toDouble() / incomePlanned.wholeUnits
            incomeFact.wholeUnits > 0L -> 1.0
            else -> 0.0
        }

    val status: BudgetStatus
        get() = when {
            incomeFact.wholeUnits > incomePlanned.wholeUnits && isSlightOverrun() -> BudgetStatus.OVER_SLIGHTLY
            incomeFact.wholeUnits > incomePlanned.wholeUnits -> BudgetStatus.OVER
            incomePlanned.wholeUnits > 0L && incomeFact.wholeUnits == incomePlanned.wholeUnits -> BudgetStatus.DONE
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
    val expenseFromSavings: Money = Money.zero(Currency.BASE),
    val plannedFromSavings: Money = Money.zero(Currency.BASE),
    val unplannedFromSavings: Money = Money.zero(Currency.BASE),
) {
    val netSaved: Money get() = savedToSavings - takenFromSavings

    val expenseFromIncome: Money get() = expenseFact - expenseFromSavings

    val remaining: Money get() = incomeFact - expenseFromIncome - netSaved

    val hasSavingsSpending: Boolean get() = !expenseFromSavings.isZero || !plannedFromSavings.isZero
}

object BudgetCalculator {

    const val WARNING_THRESHOLD: Double = 0.85
    const val SLIGHT_OVERRUN: Double = 0.02
    const val PACE_TOLERANCE: Double = 0.05

    fun categoryBudgets(
        categories: List<Category>,
        lines: List<BudgetLine>,
        transactions: List<Transaction>,
        period: Period,
        subcategories: List<Subcategory> = emptyList(),
        savingsAccountIds: Set<String> = emptySet(),
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
                    freeFromSavings = categoryLines.firstOrNull { it.subcategoryId == null }?.fromSavings == true,
                    fromSavingsPlanned = categoryLines.filter { it.fromSavings }.map { it.planned }.sumIn(Currency.BASE),
                    factFromSavings = related.filterIsInstance<Transaction.Expense>()
                        .filter { it.accountId in savingsAccountIds }
                        .map { it.amountBase }
                        .sumIn(Currency.BASE),
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
        val savingsBySubcategory = itemLines.filter { it.fromSavings }.map { it.subcategoryId }.toSet()
        val ids = (plannedBySubcategory.keys + factBySubcategory.keys).filterNotNull().distinct()
        if (ids.isEmpty() && factBySubcategory[null] == null) return emptyList()
        val items = ids.map { id ->
            SubcategoryBudget(
                subcategory = subcategoriesById[id],
                planned = plannedBySubcategory[id],
                fact = factBySubcategory[id] ?: Money.zero(Currency.BASE),
                fromSavings = id in savingsBySubcategory,
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
            expenseFromSavings = expense.map { it.factFromSavings }.sumIn(Currency.BASE),
            plannedFromSavings = expense.map { it.fromSavingsPlanned }.sumIn(Currency.BASE),
            unplannedFromSavings = expense.map { it.unplannedFromSavings }.sumIn(Currency.BASE),
        )
    }

    private fun Transaction.belongsTo(categoryId: String): Boolean = when (this) {
        is Transaction.Expense -> this.categoryId == categoryId
        is Transaction.Income -> this.categoryId == categoryId
        is Transaction.Transfer, is Transaction.Exchange -> false
    }
}
