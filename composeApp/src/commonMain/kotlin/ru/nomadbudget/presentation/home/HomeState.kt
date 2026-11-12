package ru.nomadbudget.presentation.home

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.logic.BalanceCalculator
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.logic.CapitalGoal
import ru.nomadbudget.domain.logic.CapitalModel
import ru.nomadbudget.domain.logic.CapitalSettings
import ru.nomadbudget.domain.logic.CategoryBudget
import ru.nomadbudget.domain.logic.CategoryHistory
import ru.nomadbudget.domain.logic.CostBasis
import ru.nomadbudget.domain.logic.CostBasisCalculator
import ru.nomadbudget.domain.logic.DebtCategories
import ru.nomadbudget.domain.logic.DebtLoad
import ru.nomadbudget.domain.logic.DebtMonth
import ru.nomadbudget.domain.logic.HistoryCalculator
import ru.nomadbudget.domain.logic.MarketRates
import ru.nomadbudget.domain.logic.MonthModel
import ru.nomadbudget.domain.logic.MonthPoint
import ru.nomadbudget.domain.logic.PeriodBudgetLine
import ru.nomadbudget.domain.logic.PlanCalculator
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.BankWatchData
import ru.nomadbudget.domain.model.Category
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
import ru.nomadbudget.domain.model.TransactionKind
import ru.nomadbudget.domain.model.UserSettings

enum class EntryType { EXPENSE, INCOME, TRANSFER }

val EntryType.transactionKind: TransactionKind
    get() = when (this) {
        EntryType.EXPENSE -> TransactionKind.EXPENSE
        EntryType.INCOME -> TransactionKind.INCOME
        EntryType.TRANSFER -> TransactionKind.TRANSFER
    }

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
    val rawTransactions: List<Transaction> = emptyList(),
    val budgetLines: List<BudgetLine> = emptyList(),
    val savingsTarget: Money? = null,
    val allPlanLines: List<PeriodBudgetLine> = emptyList(),
    val rates: RateTable = DefaultRates.table(),
    val balanceChecks: List<BalanceCheck> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val rateHistory: List<ExchangeRate> = emptyList(),
    val capitalGoals: List<CapitalGoal> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val bankWatch: BankWatchData = BankWatchData(),
    private val memo: Memo = Memo(),
) {
    val periodStartDay: Int = settings.periodStartDay
    val base: Currency = rates.base

    val needsOnboarding: Boolean = !loading && error == null && !offline && !settings.isOnboarded

    val costBasis: CostBasis = memo.get("costBasis", rawTransactions, accounts, rateHistory, rates) {
        CostBasisCalculator.calculate(accounts, rawTransactions, rateHistory, rates)
    }

    val transactions: List<Transaction> = memo.get("effectiveTransactions", rawTransactions, costBasis) { costBasis.applyTo(rawTransactions) }

    private val marketRates: MarketRates = memo.get("marketRates", rateHistory, rates) { MarketRates(rateHistory, rates) }

    fun marketBase(money: Money, date: LocalDate): Money = marketRates.toBase(money, date) ?: rates.toBase(money)

    fun marketRateOn(currency: Currency, date: LocalDate): Double? = marketRates.basePerUnit(currency, date)

    val systemCategoryIds: Set<String> = memo.get("system", categories) { categories.filter { it.isSystem }.map { it.id }.toSet() }

    val monthlyHistory: List<MonthPoint> = memo.get("history", transactions, accounts, period, categories, settings) {
        HistoryCalculator.monthly(transactions, accounts, period, periodStartDay, base, systemCategoryIds)
    }

    val openDebts: List<Debt> = debts.filterNot { it.isClosed }

    val debtCategoryIds: Set<String> = memo.get("debtCategories", debts, subcategories, transactions) {
        DebtCategories.categoryIds(openDebts, subcategories, transactions)
    }

    fun isDebtOnlyEnvelope(budget: CategoryBudget): Boolean = DebtCategories.isDebtOnlyEnvelope(budget, debtCategoryIds)

    fun isDebtCategory(categoryId: String?): Boolean = DebtCategories.isDebtCategory(categoryId, openDebts, debtCategoryIds)

    fun debtBySubcategoryName(name: String): Debt? = DebtCategories.byName(openDebts, name)

    val accountsById: Map<String, Account> = accounts.associateBy { it.id }
    val categoriesById: Map<String, Category> = categories.associateBy { it.id }
    val subcategoriesById: Map<String, Subcategory> = subcategories.associateBy { it.id }

    val activeAccounts: List<Account> = accounts
        .filterNot { it.isArchived }
        .sortedWith(compareBy<Account> { it.role.ordinal }.thenBy { it.sortOrder })
    val activeCategories: List<Category> = categories.filterNot { it.isArchived || it.isSystem }
    val activeSubcategories: List<Subcategory> = subcategories.filterNot { it.isArchived }

    val userCurrencies: List<Currency> = memo.get("userCurrencies", currencies, accounts, settings) {
        val codes = settings.currencyCodes.toSet() + accounts.filterNot { it.isArchived }.map { it.currency.code } + base.code
        currencies.filter { it.code in codes }.sortedWith(compareBy({ it != base }, { it.code }))
    }

    val foreignCurrencies: List<Currency> = userCurrencies.filter { it != base }

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

    val operationalAccounts: List<Account> = activeAccounts.filter { it.role == AccountRole.LIFE }

    val fundAccounts: List<Account> = activeAccounts.filter { it.role == AccountRole.FUND }

    val cushionAccounts: List<Account> = activeAccounts.filter { it.role == AccountRole.CUSHION }

    val totalBase: Money = BalanceCalculator.totalInBase(activeAccounts, balances, rates)

    val operationalBase: Money = BalanceCalculator.totalInBase(operationalAccounts, balances, rates)

    val fundsBalance: Money = BalanceCalculator.totalInBase(fundAccounts, balances, rates)

    val savingsBalance: Money = BalanceCalculator.totalInBase(cushionAccounts, balances, rates)

    val capitalSettings: CapitalSettings = settings.capital

    val debtMonths: List<DebtMonth> get() = memo.get("debtLoad", debts, transactions, rates, today, periodStartDay, systemCategoryIds) {
        DebtLoad.months(debts, transactions, rates, today, periodStartDay, systemCategoryIds)
    }

    val capital: CapitalModel get() = memo.get("capital", today, accounts, transactions, debts, rates, categories, savingsTarget, settings, capitalGoals) {
        CapitalModel.build(
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

    val categoryHistory: Map<String, CategoryHistory> get() = memo.get("categoryHistory", transactions, period, accounts, settings) {
        PlanCalculator.history(transactions, period, periodStartDay, base, accounts)
    }

    val exchanges: List<Transaction.Exchange> get() = memo.get("exchanges", transactions) { transactions.filterIsInstance<Transaction.Exchange>() }

    val dayNumber: Int? = if (today in period) period.dayNumber(today) else null

    val isFirstRun: Boolean = !loading && transactions.isEmpty() && budgetLines.isEmpty()

    fun accountName(id: String): String = accountsById[id]?.name ?: "?"

    fun categoryName(id: String?): String = id?.let { categoriesById[it]?.name } ?: "—"

    fun subcategoryName(id: String?): String? = id?.let { subcategoriesById[it]?.name }
}
