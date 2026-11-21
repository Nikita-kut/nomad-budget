package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

enum class BudgetStatus { OK, WARNING, DONE, OVER_SLIGHTLY, OVER }

enum class Pace { ON_TRACK, AHEAD, BEHIND }

data class BudgetLine(
    val categoryId: String,
    val planned: Money,
    val subcategoryId: String? = null,
)

data class SubcategoryBudget(
    val subcategory: Subcategory?,
    val planned: Money?,
    val fact: Money,
    val fundFact: Money = Money.zero(fact.currency),
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
    val freePlanned: Money = Money.zero(fact.currency),
    val itemsPlanned: Money = Money.zero(fact.currency),
    val fundFact: Money = Money.zero(fact.currency),
) {
    val remaining: Money get() = planned - fact

    val remainingWhole: Money get() = planned.withoutFraction() - fact.withoutFraction()

    val hasPlan: Boolean get() = planned.minor > 0L

    val isIdle: Boolean get() = !hasPlan && fact.isZero && fundFact.isZero

    fun expectedByNow(elapsedShare: Double): Money {
        val share = elapsedShare.coerceIn(0.0, 1.0)
        val settled = items
            .filter { it.subcategory != null && it.planned != null && it.fact.wholeUnits >= it.planned.wholeUnits }
            .mapNotNull { it.planned }
            .sumIn(planned.currency)
        val flexible = planned - settled
        return settled + Money((flexible.minor * share).toLong(), planned.currency)
    }

    fun pace(elapsedShare: Double): Pace {
        if (planned.minor <= 0L) return Pace.ON_TRACK
        val expected = expectedByNow(elapsedShare)
        val tolerance = (planned.wholeUnits * BudgetCalculator.PACE_TOLERANCE).toLong().coerceAtLeast(1L)
        val delta = fact.wholeUnits - expected.wholeUnits
        return when {
            delta > tolerance -> Pace.AHEAD
            delta < -tolerance -> Pace.BEHIND
            else -> Pace.ON_TRACK
        }
    }

    fun aheadBy(elapsedShare: Double): Money = fact.withoutFraction() - expectedByNow(elapsedShare).withoutFraction()

    private fun isSlightOverrun(): Boolean {
        val plannedWhole = planned.wholeUnits
        if (plannedWhole <= 0L) return false
        return (fact.wholeUnits - plannedWhole).toDouble() / plannedWhole <= BudgetCalculator.SLIGHT_OVERRUN
    }

    val progress: Double
        get() = when {
            planned.wholeUnits > 0L -> fact.wholeUnits.toDouble() / planned.wholeUnits
            fact.wholeUnits > 0L -> 1.0
            else -> 0.0
        }

    val status: BudgetStatus
        get() = when {
            fact.wholeUnits > planned.wholeUnits && isSlightOverrun() -> BudgetStatus.OVER_SLIGHTLY
            fact.wholeUnits > planned.wholeUnits -> BudgetStatus.OVER
            planned.wholeUnits > 0L && fact.wholeUnits == planned.wholeUnits -> BudgetStatus.DONE
            progress >= BudgetCalculator.WARNING_THRESHOLD -> BudgetStatus.WARNING
            else -> BudgetStatus.OK
        }
}

data class EnvelopeSpend(
    val tx: Transaction,
    val categoryId: String,
    val subcategoryId: String?,
    val amountBase: Money,
    val fund: Account? = null,
)

object BudgetCalculator {

    const val WARNING_THRESHOLD: Double = 0.85
    const val SLIGHT_OVERRUN: Double = 0.02
    const val PACE_TOLERANCE: Double = 0.05

    fun isEnvelopeSpend(tx: Transaction.Expense, accountsById: Map<String, Account>): Boolean =
        tx.debtId == null && (accountsById[tx.accountId]?.role ?: AccountRole.LIFE) == AccountRole.LIFE

    fun envelopeSpends(transactions: List<Transaction>, accountsById: Map<String, Account>): List<EnvelopeSpend> =
        transactions.mapNotNull { tx ->
            when (tx) {
                is Transaction.Expense -> EnvelopeSpend(tx, tx.categoryId, tx.subcategoryId, tx.amountBase).takeIf { isEnvelopeSpend(tx, accountsById) }
                is Transaction.Transfer -> fundContribution(tx, tx.fromAccountId, tx.toAccountId, accountsById)
                is Transaction.Exchange -> fundContribution(tx, tx.fromAccountId, tx.toAccountId, accountsById)
                is Transaction.Income -> null
            }
        }

    private fun fundContribution(tx: Transaction, fromId: String, toId: String, accountsById: Map<String, Account>): EnvelopeSpend? {
        val from = accountsById[fromId]
        val to = accountsById[toId]
        val fromLife = (from?.role ?: AccountRole.LIFE) == AccountRole.LIFE
        val toLife = (to?.role ?: AccountRole.LIFE) == AccountRole.LIFE
        to?.fundTarget?.takeIf { fromLife }?.let { return EnvelopeSpend(tx, it.categoryId, it.subcategoryId, tx.amountBase, to) }
        from?.fundTarget?.takeIf { toLife }?.let { return EnvelopeSpend(tx, it.categoryId, it.subcategoryId, -tx.amountBase, from) }
        return null
    }

    fun isCoveredByFund(tx: Transaction.Expense, accountsById: Map<String, Account>): Boolean =
        tx.debtId == null && accountsById[tx.accountId]?.fundTarget?.covers(tx.categoryId, tx.subcategoryId) == true

    fun categoryBudgets(
        categories: List<Category>,
        lines: List<BudgetLine>,
        transactions: List<Transaction>,
        period: Period,
        subcategories: List<Subcategory> = emptyList(),
        accounts: List<Account> = emptyList(),
        base: Currency,
    ): List<CategoryBudget> {
        val subcategoriesById = subcategories.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        val inPeriod = transactions.filter { it.date in period }
        val spends = envelopeSpends(inPeriod, accountsById).groupBy { it.categoryId }
        val incomes = inPeriod.filterIsInstance<Transaction.Income>().groupBy { it.categoryId }
        return categories
            .filterNot { it.isSystem }
            .sortedBy { it.sortOrder }
            .map { category ->
                val categorySpends = spends[category.id].orEmpty()
                val categoryIncomes = incomes[category.id].orEmpty()
                val fundSpends = inPeriod.filterIsInstance<Transaction.Expense>().filter {
                    it.categoryId == category.id && it.debtId == null && accountsById[it.accountId]?.role == AccountRole.FUND && !isCoveredByFund(it, accountsById)
                }
                val categoryLines = if (category.isArchived) emptyList() else lines.filter { it.categoryId == category.id }
                val itemLines = categoryLines.filter { it.subcategoryId != null }
                val itemsPlanned = itemLines.map { it.planned }.sumIn(base)
                val freePlanned = categoryLines.firstOrNull { it.subcategoryId == null }?.planned ?: Money.zero(base)
                CategoryBudget(
                    category = category,
                    planned = freePlanned + itemsPlanned,
                    fact = (categorySpends.map { it.amountBase } + categoryIncomes.map { it.amountBase }).sumIn(base),
                    transactionCount = categorySpends.size + categoryIncomes.size,
                    items = buildItems(categorySpends, itemLines, subcategoriesById, base, fundSpends),
                    plannedByItems = itemLines.isNotEmpty(),
                    freePlanned = freePlanned,
                    itemsPlanned = itemsPlanned,
                    fundFact = fundSpends.map { it.amountBase }.sumIn(base),
                )
            }
    }

    private fun buildItems(
        spends: List<EnvelopeSpend>,
        itemLines: List<BudgetLine>,
        subcategoriesById: Map<String, Subcategory>,
        base: Currency,
        fundSpends: List<Transaction.Expense> = emptyList(),
    ): List<SubcategoryBudget> {
        val factBySubcategory = spends
            .groupBy { it.subcategoryId }
            .mapValues { (_, list) -> list.map { it.amountBase }.sumIn(base) }
        val fundBySubcategory = fundSpends.groupBy { it.subcategoryId }.mapValues { (_, list) -> list.map { it.amountBase }.sumIn(base) }
        val plannedBySubcategory = itemLines.associate { it.subcategoryId to it.planned }
        val ids = (plannedBySubcategory.keys + factBySubcategory.keys + fundBySubcategory.keys).filterNotNull().distinct()
        if (ids.isEmpty() && factBySubcategory[null] == null) return emptyList()
        val items = ids.map { id ->
            SubcategoryBudget(
                subcategory = subcategoriesById[id],
                planned = plannedBySubcategory[id],
                fact = factBySubcategory[id] ?: Money.zero(base),
                fundFact = fundBySubcategory[id] ?: Money.zero(base),
            )
        }.sortedWith(compareByDescending<SubcategoryBudget> { it.planned?.minor ?: -1L }.thenBy { it.subcategory?.name.orEmpty() })
        val withoutSubcategory = factBySubcategory[null]?.let { SubcategoryBudget(subcategory = null, planned = null, fact = it) }
        return items + listOfNotNull(withoutSubcategory)
    }

    fun cushionInflow(transactions: List<Transaction>, cushionIds: Set<String>, systemCategoryIds: Set<String> = emptySet(), base: Currency): Money =
        transactions.mapNotNull {
            when (it) {
                is Transaction.Transfer -> it.amountBase.takeIf { _ -> it.toAccountId in cushionIds && it.fromAccountId !in cushionIds }
                is Transaction.Exchange -> it.amountBase.takeIf { _ -> it.toAccountId in cushionIds && it.fromAccountId !in cushionIds }
                is Transaction.Income -> it.amountBase.takeIf { _ -> it.accountId in cushionIds && it.categoryId !in systemCategoryIds }
                is Transaction.Expense -> null
            }
        }.sumIn(base)

    fun cushionOutflow(transactions: List<Transaction>, cushionIds: Set<String>, base: Currency): Money =
        transactions.mapNotNull {
            when (it) {
                is Transaction.Transfer -> it.amountBase.takeIf { _ -> it.fromAccountId in cushionIds && it.toAccountId !in cushionIds }
                is Transaction.Exchange -> it.amountBase.takeIf { _ -> it.fromAccountId in cushionIds && it.toAccountId !in cushionIds }
                is Transaction.Income, is Transaction.Expense -> null
            }
        }.sumIn(base)

    fun cushionToDebts(transactions: List<Transaction>, cushionIds: Set<String>, systemCategoryIds: Set<String> = emptySet(), base: Currency): Money =
        transactions.filterIsInstance<Transaction.Expense>()
            .filter { it.accountId in cushionIds && it.categoryId !in systemCategoryIds && it.debtId != null }
            .map { it.amountBase }
            .sumIn(base)

    fun cushionSpent(transactions: List<Transaction>, cushionIds: Set<String>, systemCategoryIds: Set<String> = emptySet(), base: Currency): Money =
        transactions.filterIsInstance<Transaction.Expense>()
            .filter { it.accountId in cushionIds && it.categoryId !in systemCategoryIds }
            .map { it.amountBase }
            .sumIn(base)
}
