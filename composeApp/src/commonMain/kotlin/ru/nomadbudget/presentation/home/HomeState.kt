package ru.nomadbudget.presentation.home

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.logic.BalanceCalculator
import ru.nomadbudget.domain.logic.BudgetCalculator
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.logic.CategoryBudget
import ru.nomadbudget.domain.logic.HistoryCalculator
import ru.nomadbudget.domain.logic.MonthPoint
import ru.nomadbudget.domain.logic.MonthSummary
import ru.nomadbudget.domain.logic.PlanCalculator
import ru.nomadbudget.domain.logic.PlanSummary
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DefaultRates
import ru.nomadbudget.domain.model.Draft
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn
import kotlin.time.Instant

enum class EntryType { EXPENSE, INCOME, TRANSFER }

data class EntryDraft(
    val type: EntryType,
    val date: LocalDate,
    val accountId: String,
    val amount: Money,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val subcategoryName: String = "",
    val note: String = "",
    val debtId: String? = null,
    val fromDraftId: String? = null,
)

data class PlanItemInput(val name: String, val planned: Money)

data class CategoryPlanDraft(
    val categoryId: String,
    val limit: Money?,
    val items: List<PlanItemInput>,
    val removedSubcategoryIds: List<String>,
)

data class EntryPrefill(val draftId: String, val amountText: String?, val note: String)

data class ExchangeDraft(
    val date: LocalDate,
    val fromAccountId: String,
    val toAccountId: String,
    val given: Money,
    val received: Money,
    val note: String = "",
)

data class JournalDay(
    val date: LocalDate,
    val transactions: List<Transaction>,
    val spentBase: Money,
)

data class HomeState(
    val today: LocalDate,
    val period: Period,
    val periodId: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val lastSyncedAt: Instant? = null,
    val offline: Boolean = false,
    val cachedAt: Instant? = null,
    val pendingCount: Int = 0,
    val drafts: List<Draft> = emptyList(),
    val entryPrefill: EntryPrefill? = null,
    val currencies: List<Currency> = Currency.builtIn,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val subcategories: List<Subcategory> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgetLines: List<BudgetLine> = emptyList(),
    val rates: RateTable = DefaultRates.table(),
    val balanceChecks: List<BalanceCheck> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val rateHistory: List<ExchangeRate> = emptyList(),
) {
    val monthlyHistory: List<MonthPoint> = HistoryCalculator.monthly(transactions, accounts, period)

    val openDebts: List<Debt> = debts.filterNot { it.isClosed }

    fun paidThisPeriod(debt: Debt): Money = inPeriod
        .filterIsInstance<Transaction.Expense>()
        .filter { it.debtId == debt.id }
        .map { it.amount }
        .filter { it.currency == debt.currency }
        .sumIn(debt.currency)

    val accountsById: Map<String, Account> = accounts.associateBy { it.id }
    val categoriesById: Map<String, Category> = categories.associateBy { it.id }
    val subcategoriesById: Map<String, Subcategory> = subcategories.associateBy { it.id }

    val activeAccounts: List<Account> = accounts
        .filterNot { it.isArchived }
        .sortedWith(compareBy<Account> { it.isSavings }.thenBy { it.sortOrder })
    val activeCategories: List<Category> = categories.filterNot { it.isArchived }

    val foreignCurrencies: List<Currency> = currencies.filter { it != Currency.BASE }

    private val extraCurrencyOrder: List<String> = foreignCurrencies.filter { it != Currency.USD }.map { it.code }.sorted()

    fun currencyOrdinal(currency: Currency): Int = extraCurrencyOrder.indexOf(currency.code).coerceAtLeast(0)

    val currenciesWithoutRate: List<Currency> = foreignCurrencies.filterNot(rates::hasRate)

    val inPeriod: List<Transaction> = transactions.filter { it.date in period }

    private val budgetCategories: List<Category> = run {
        val usedInPeriod = inPeriod.mapNotNull {
            when (it) {
                is Transaction.Expense -> it.categoryId
                is Transaction.Income -> it.categoryId
                is Transaction.Transfer, is Transaction.Exchange -> null
            }
        }.toSet()
        activeCategories + categories.filter { it.isArchived && it.id in usedInPeriod }
    }

    val budgets: List<CategoryBudget> =
        BudgetCalculator.categoryBudgets(budgetCategories, budgetLines, inPeriod, period, subcategories)

    val summary: MonthSummary = BudgetCalculator.monthSummary(budgets, inPeriod, accounts, period)

    val planSummary: PlanSummary = PlanCalculator.summary(budgets)

    val balances: Map<String, Money> = accounts.associate { it.id to BalanceCalculator.balance(it, transactions) }

    val totalBase: Money = activeAccounts
        .mapNotNull { rates.toBaseOrNull(balances.getValue(it.id)) }
        .sumIn(Currency.BASE)

    val operationalAccounts: List<Account> = activeAccounts.filterNot { it.isSavings }

    val operationalBase: Money = operationalAccounts
        .mapNotNull { rates.toBaseOrNull(balances.getValue(it.id)) }
        .sumIn(Currency.BASE)

    val operationalAtPeriodStartBase: Money = operationalAccounts
        .mapNotNull { rates.toBaseOrNull(BalanceCalculator.balanceBefore(it, transactions, period.start)) }
        .sumIn(Currency.BASE)

    val hasExpensePlan: Boolean = budgets.any { it.category.kind == CategoryKind.EXPENSE && it.planned.minor > 0L }

    val journal: List<JournalDay> = inPeriod
        .groupBy { it.date }
        .entries
        .sortedByDescending { it.key }
        .map { (date, list) ->
            JournalDay(
                date = date,
                transactions = list,
                spentBase = list.filterIsInstance<Transaction.Expense>().map { it.amountBase }.sumIn(Currency.BASE),
            )
        }

    val exchanges: List<Transaction.Exchange> = transactions.filterIsInstance<Transaction.Exchange>()

    val dayNumber: Int? = if (today in period) period.dayNumber(today) else null

    fun accountName(id: String): String = accountsById[id]?.name ?: "?"

    fun categoryName(id: String?): String = id?.let { categoriesById[it]?.name } ?: "—"

    fun subcategoryName(id: String?): String? = id?.let { subcategoriesById[it]?.name }
}
