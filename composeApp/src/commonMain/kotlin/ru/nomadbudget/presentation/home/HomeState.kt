package ru.nomadbudget.presentation.home

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.logic.BalanceCalculator
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.logic.CategoryBudget
import ru.nomadbudget.domain.logic.HistoryCalculator
import ru.nomadbudget.domain.logic.MonthPoint
import ru.nomadbudget.domain.logic.PlanCalculator
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
import ru.nomadbudget.domain.logic.CategoryHistory
import ru.nomadbudget.domain.logic.PeriodBudgetLine
import ru.nomadbudget.domain.logic.SavingsForecast
import ru.nomadbudget.domain.logic.MonthModel
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.UserSettings
import ru.nomadbudget.domain.logic.CapitalSettings

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
    val debtEarly: Boolean = false,
    val fromDraftId: String? = null,
    val splitAccountId: String? = null,
    val splitAmount: Money? = null,
)

data class PlanItemInput(val name: String, val planned: Money, val subcategoryId: String? = null)

data class CategoryPlanDraft(
    val categoryId: String,
    val free: Money?,
    val items: List<PlanItemInput>,
    val removedSubcategoryIds: List<String>,
)

data class EntryPrefill(
    val draftId: String?,
    val amountText: String?,
    val note: String,
    val categoryId: String? = null,
    val subcategoryName: String? = null,
    val accountId: String? = null,
    val debtId: String? = null,
    val date: LocalDate? = null,
)

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
    val savingsTarget: Money? = null,
    val allPlanLines: List<PeriodBudgetLine> = emptyList(),
    val rates: RateTable = DefaultRates.table(),
    val balanceChecks: List<BalanceCheck> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val rateHistory: List<ExchangeRate> = emptyList(),
    val capitalGoals: List<ru.nomadbudget.domain.logic.CapitalGoal> = emptyList(),
    val settings: UserSettings = UserSettings(),
    private val memo: Memo = Memo(),
) {
    val periodStartDay: Int = settings.periodStartDay

    val capitalSettings: CapitalSettings = settings.capital

    val needsOnboarding: Boolean = !loading && error == null && !offline && !settings.isOnboarded

    val incomeDaysHint: String? = settings.incomeDays.takeIf { it.isNotEmpty() }?.let { days ->
        val sorted = days.sorted()
        val listed = if (sorted.size == 1) sorted.first().toString() else sorted.dropLast(1).joinToString(", ") + " и " + sorted.last()
        "Доход обычно приходит $listed числа"
    }

    val systemCategoryIds: Set<String> = memo.get("system", categories) { categories.filter { it.isSystem }.map { it.id }.toSet() }

    val monthlyHistory: List<MonthPoint> = memo.get("history", transactions, accounts, period, categories, settings) {
        HistoryCalculator.monthly(transactions, accounts, period, periodStartDay, systemCategoryIds)
    }

    val openDebts: List<Debt> = debts.filterNot { it.isClosed }

    val debtCategoryIds: Set<String> = memo.get("debtCategories", debts, subcategories, transactions) {
        val debtNames = openDebts.map { it.name.trim().lowercase() }.toSet()
        val bySubcategory = subcategories.filter { it.name.trim().lowercase() in debtNames }.map { it.categoryId }
        val byPayments = transactions.filterIsInstance<Transaction.Expense>().filter { it.debtId != null }.map { it.categoryId }
        (bySubcategory + byPayments).toSet()
    }

    fun isDebtOnlyEnvelope(budget: CategoryBudget): Boolean =
        budget.category.id in debtCategoryIds && !budget.hasPlan && budget.fact.isZero

    fun isDebtCategory(categoryId: String?): Boolean =
        openDebts.isNotEmpty() && categoryId != null && (debtCategoryIds.isEmpty() || categoryId in debtCategoryIds)

    fun debtBySubcategoryName(name: String): Debt? = openDebts.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }

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
        .sortedWith(compareBy<Account> { it.role.ordinal }.thenBy { it.sortOrder })
    val activeCategories: List<Category> = categories.filterNot { it.isArchived || it.isSystem }
    val activeSubcategories: List<Subcategory> = subcategories.filterNot { it.isArchived }

    val foreignCurrencies: List<Currency> = currencies.filter { it != Currency.BASE }

    private val extraCurrencyOrder: List<String> = foreignCurrencies.filter { it != Currency.USD }.map { it.code }.sorted()

    fun currencyOrdinal(currency: Currency): Int = extraCurrencyOrder.indexOf(currency.code).coerceAtLeast(0)

    val currenciesWithoutRate: List<Currency> = foreignCurrencies.filterNot(rates::hasRate)

    val inPeriod: List<Transaction> = memo.get("inPeriod", transactions, period) { transactions.filter { it.date in period } }

    val balances: Map<String, Money> = memo.get("balances", accounts, transactions) { BalanceCalculator.balances(accounts, transactions) }

    val month: MonthModel = memo.get("month", period, today, categories, subcategories, budgetLines, transactions, accounts, debts, rates, savingsTarget) {
        MonthModel.build(
        period = period,
        today = today,
        categories = categories,
        subcategories = subcategories,
        lines = budgetLines,
        transactions = transactions,
        accounts = accounts,
        debts = debts,
        rates = rates,
        balances = balances,
        savingsTarget = savingsTarget,
        )
    }

    val budgets: List<CategoryBudget> = month.incomeBudgets + month.envelopes

    val cushionAccounts: List<Account> = activeAccounts.filter { it.role == AccountRole.CUSHION }

    val fundAccounts: List<Account> = activeAccounts.filter { it.role == AccountRole.FUND }

    val savingsBalance: Money
        get() = cushionAccounts.mapNotNull { rates.toBaseOrNull(balances.getValue(it.id)) }.sumIn(Currency.BASE)

    val fundsBalance: Money
        get() = fundAccounts.mapNotNull { rates.toBaseOrNull(balances.getValue(it.id)) }.sumIn(Currency.BASE)

    val savingsForecast: SavingsForecast? get() = HistoryCalculator.savingsForecast(monthlyHistory, today in period, savingsBalance, savingsTarget)

    val capital: ru.nomadbudget.domain.logic.CapitalModel get() = memo.get("capital", today, accounts, transactions, debts, rates, categories, savingsTarget, settings, capitalGoals) {
        ru.nomadbudget.domain.logic.CapitalModel.build(
            today = today,
            accounts = accounts,
            balances = balances,
            transactions = transactions,
            debts = debts,
            rates = rates,
            systemCategoryIds = systemCategoryIds,
            planMonthly = savingsTarget,
            settings = capitalSettings,
            goals = capitalGoals,
            startDay = periodStartDay,
        )
    }

    val categoryHistory: Map<String, CategoryHistory> get() = memo.get("categoryHistory", transactions, period, accounts, settings) { PlanCalculator.history(transactions, period, periodStartDay, accounts) }

    val totalBase: Money = activeAccounts
        .mapNotNull { rates.toBaseOrNull(balances.getValue(it.id)) }
        .sumIn(Currency.BASE)

    val operationalAccounts: List<Account> = activeAccounts.filter { it.role == AccountRole.LIFE }

    val operationalBase: Money = operationalAccounts
        .mapNotNull { rates.toBaseOrNull(balances.getValue(it.id)) }
        .sumIn(Currency.BASE)

    val operationalAtPeriodStartBase: Money get() = memo.get("opStart", accounts, transactions, period, rates) {
        operationalAccounts
            .mapNotNull { rates.toBaseOrNull(BalanceCalculator.balanceBefore(it, transactions, period.start)) }
            .sumIn(Currency.BASE)
    }

    val hasExpensePlan: Boolean = month.envelopes.any { it.hasPlan }

    val journal: List<JournalDay> get() = memo.get("journal", transactions, period) {
        inPeriod
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
    }

    val exchanges: List<Transaction.Exchange> get() = memo.get("exchanges", transactions) { transactions.filterIsInstance<Transaction.Exchange>() }

    val dayNumber: Int? = if (today in period) period.dayNumber(today) else null

    val isCurrentPeriod: Boolean = today in period

    val daysLeft: Int = if (today in period) period.lengthDays - period.dayNumber(today) + 1 else 0

    val isFirstRun: Boolean = !loading && transactions.isEmpty() && budgetLines.isEmpty()

    val elapsedShare: Double = when {
        today in period -> period.dayNumber(today).toDouble() / period.lengthDays
        today >= period.endExclusive -> 1.0
        else -> 0.0
    }

    fun accountName(id: String): String = accountsById[id]?.name ?: "?"

    fun categoryName(id: String?): String = id?.let { categoriesById[it]?.name } ?: "—"

    fun subcategoryName(id: String?): String? = id?.let { subcategoriesById[it]?.name }
}
