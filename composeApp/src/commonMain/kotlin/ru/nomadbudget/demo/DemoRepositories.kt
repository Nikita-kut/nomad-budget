package ru.nomadbudget.demo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.nomadbudget.core.TodayProvider
import ru.nomadbudget.data.local.DraftsStore
import ru.nomadbudget.data.local.InMemoryStore
import ru.nomadbudget.data.local.KeyValueStore
import ru.nomadbudget.data.local.OfflineCache
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.AuthState
import ru.nomadbudget.domain.repository.BalanceCheckRepository
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.CurrencyRepository
import ru.nomadbudget.domain.repository.DebtRepository
import ru.nomadbudget.domain.repository.DraftRepository
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.TransactionRepository
import ru.nomadbudget.presentation.home.HomeViewModel

val demoModule = module {
    single { DemoDatabase(DemoDatabase.anchorToday()) }
    single<KeyValueStore> { InMemoryStore() }
    single<TodayProvider> { TodayProvider { get<DemoDatabase>().today } }
    single { OfflineCache(get()) }
    single<DraftRepository> {
        DraftsStore(get()).apply {
            add("кофе 300")
            add("такси до дома 650")
        }
    }
    single<AuthRepository> { DemoAuthRepository() }
    single<CurrencyRepository> { DemoCurrencyRepository(get()) }
    single<AccountRepository> { DemoAccountRepository(get()) }
    single<CategoryRepository> { DemoCategoryRepository(get()) }
    single<PeriodRepository> { DemoPeriodRepository(get()) }
    single<TransactionRepository> { DemoTransactionRepository(get()) }
    single<BudgetRepository> { DemoBudgetRepository(get()) }
    single<ExchangeRateRepository> { DemoExchangeRateRepository(get()) }
    single<BalanceCheckRepository> { DemoBalanceCheckRepository(get()) }
    single<DebtRepository> { DemoDebtRepository(get()) }
    viewModelOf(::HomeViewModel)
}

private class DemoAuthRepository : AuthRepository {
    private val flow = MutableStateFlow(AuthState.SIGNED_IN)
    override val state: Flow<AuthState> = flow

    override suspend fun signIn(email: String, password: String) {
        flow.value = AuthState.SIGNED_IN
    }

    override suspend fun signOut() {
        flow.value = AuthState.SIGNED_OUT
    }
}

private class DemoCurrencyRepository(private val db: DemoDatabase) : CurrencyRepository {
    override suspend fun getAll(): List<Currency> = db.currencies
}

private class DemoAccountRepository(private val db: DemoDatabase) : AccountRepository {
    override suspend fun getAll(): List<Account> = db.accounts.toList()

    override suspend fun add(name: String, currency: Currency, kind: AccountKind, isSavings: Boolean, sortOrder: Int): Account =
        Account(db.newId("account"), name, currency, kind, isSavings, Money.zero(currency), sortOrder).also { db.accounts += it }

    override suspend fun rename(id: String, name: String) = replace(id) { it.copy(name = name) }

    override suspend fun setSortOrder(id: String, sortOrder: Int) = replace(id) { it.copy(sortOrder = sortOrder) }

    override suspend fun archive(id: String) = replace(id) { it.copy(isArchived = true) }

    private fun replace(id: String, change: (Account) -> Account) {
        db.accounts.replaceEach { if (it.id == id) change(it) else it }
    }
}

private class DemoCategoryRepository(private val db: DemoDatabase) : CategoryRepository {
    override suspend fun getCategories(): List<Category> = db.categories.toList()

    override suspend fun getSubcategories(): List<Subcategory> = db.subcategories.toList()

    override suspend fun addCategory(name: String, kind: CategoryKind, sortOrder: Int): Category =
        Category(db.newId("category"), name, kind, sortOrder).also { db.categories += it }

    override suspend fun renameCategory(id: String, name: String) {
        db.categories.replaceEach { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun archiveCategory(id: String) {
        db.categories.replaceEach { if (it.id == id) it.copy(isArchived = true) else it }
    }

    override suspend fun addSubcategory(categoryId: String, name: String): Subcategory =
        Subcategory(db.newId("subcategory"), categoryId, name).also { db.subcategories += it }

    override suspend fun renameSubcategory(id: String, name: String) {
        db.subcategories.replaceEach { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteSubcategory(id: String) {
        db.subcategories.removeAll { it.id == id }
    }
}

private class DemoPeriodRepository(private val db: DemoDatabase) : PeriodRepository {
    override suspend fun ensure(period: Period): String = db.periodId(period)
}

private class DemoTransactionRepository(private val db: DemoDatabase) : TransactionRepository {
    override suspend fun getInPeriod(period: Period, accounts: List<Account>): List<Transaction> = db.transactions.filter { it.date in period }

    override suspend fun getAll(accounts: List<Account>): List<Transaction> = db.transactions.sortedByDescending { it.date }

    override suspend fun add(transaction: Transaction, accounts: List<Account>): Transaction {
        val saved = transaction.withId(db.newId("tx"))
        db.transactions += saved
        db.applyDebtPrincipal(saved, sign = 1)
        return saved
    }

    override suspend fun update(transaction: Transaction, accounts: List<Account>): Transaction {
        val index = db.transactions.indexOfFirst { it.id == transaction.id }
        require(index >= 0) { "Операция не найдена" }
        db.applyDebtPrincipal(db.transactions[index], sign = -1)
        db.transactions[index] = transaction
        db.applyDebtPrincipal(transaction, sign = 1)
        return transaction
    }

    override suspend fun delete(id: String) {
        val existing = db.transactions.firstOrNull { it.id == id } ?: return
        db.applyDebtPrincipal(existing, sign = -1)
        db.transactions.remove(existing)
    }

    override suspend fun flushPending(): Int = 0

    override fun pendingCount(): Int = 0

    private fun Transaction.withId(id: String): Transaction = when (this) {
        is Transaction.Expense -> copy(id = id)
        is Transaction.Income -> copy(id = id)
        is Transaction.Transfer -> copy(id = id)
        is Transaction.Exchange -> copy(id = id)
    }
}

private class DemoBudgetRepository(private val db: DemoDatabase) : BudgetRepository {
    override suspend fun getLines(periodId: String): List<BudgetLine> = db.budgetLines[periodId].orEmpty().toList()

    override suspend fun setPlanned(periodId: String, line: BudgetLine) {
        val lines = db.budgetLines.getOrPut(periodId) { mutableListOf() }
        lines.removeAll { it.categoryId == line.categoryId && it.subcategoryId == line.subcategoryId }
        lines += line
    }

    override suspend fun deleteLine(periodId: String, categoryId: String, subcategoryId: String?) {
        db.budgetLines[periodId]?.removeAll { it.categoryId == categoryId && it.subcategoryId == subcategoryId }
    }
}

private class DemoExchangeRateRepository(private val db: DemoDatabase) : ExchangeRateRepository {
    override suspend fun ratesOnOrBefore(date: LocalDate): RateTable = db.ratesOn(date)

    override suspend fun history(): List<ExchangeRate> = db.rateHistory.sortedBy { it.date }
}

private class DemoDebtRepository(private val db: DemoDatabase) : DebtRepository {
    override suspend fun getAll(): List<Debt> = db.debts.toList()

    override suspend fun add(debt: Debt): Debt = debt.copy(id = db.newId("debt")).also { db.debts += it }

    override suspend fun update(debt: Debt) {
        db.debts.replaceEach { if (it.id == debt.id) debt else it }
    }

    override suspend fun close(id: String) {
        db.debts.replaceEach { if (it.id == id) it.copy(isClosed = true) else it }
    }
}

private class DemoBalanceCheckRepository(private val db: DemoDatabase) : BalanceCheckRepository {
    override suspend fun getRecent(accounts: List<Account>): List<BalanceCheck> = db.balanceChecks.sortedByDescending { it.date }

    override suspend fun add(check: BalanceCheck): BalanceCheck = check.copy(id = db.newId("check")).also { db.balanceChecks += it }
}
