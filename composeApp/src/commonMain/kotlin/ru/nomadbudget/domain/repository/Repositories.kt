package ru.nomadbudget.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Draft
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.logic.PeriodBudgetLine

enum class AuthState { LOADING, SIGNED_IN, SIGNED_OUT }

interface AuthRepository {
    val state: Flow<AuthState>
    suspend fun signIn(email: String, password: String)
    suspend fun signOut()
}

interface CurrencyRepository {
    suspend fun getAll(): List<Currency>
}

interface AccountRepository {
    suspend fun getAll(): List<Account>
    suspend fun add(name: String, currency: Currency, kind: AccountKind, isSavings: Boolean, sortOrder: Int): Account
    suspend fun rename(id: String, name: String)
    suspend fun setSortOrder(id: String, sortOrder: Int)
    suspend fun archive(id: String)
}

interface CategoryRepository {
    suspend fun getCategories(): List<Category>
    suspend fun getSubcategories(): List<Subcategory>
    suspend fun addCategory(name: String, kind: CategoryKind, sortOrder: Int): Category
    suspend fun renameCategory(id: String, name: String)
    suspend fun archiveCategory(id: String)
    suspend fun addSubcategory(categoryId: String, name: String): Subcategory
    suspend fun renameSubcategory(id: String, name: String)
    suspend fun deleteSubcategory(id: String)
}

interface PeriodRepository {
    suspend fun ensure(period: Period): String
}

interface TransactionRepository {
    suspend fun getInPeriod(period: Period, accounts: List<Account>): List<Transaction>
    suspend fun getAll(accounts: List<Account>): List<Transaction>
    suspend fun add(transaction: Transaction, accounts: List<Account>): Transaction
    suspend fun update(transaction: Transaction, accounts: List<Account>): Transaction
    suspend fun delete(id: String)
    suspend fun flushPending(): Int
    fun pendingCount(): Int
}

interface BudgetRepository {
    suspend fun getLines(periodId: String): List<BudgetLine>
    suspend fun getAllLines(): List<PeriodBudgetLine>
    suspend fun getSavingsTarget(periodId: String): Money?
    suspend fun setSavingsTarget(periodId: String, target: Money?)
    suspend fun setPlanned(periodId: String, line: BudgetLine)
    suspend fun deleteLine(periodId: String, categoryId: String, subcategoryId: String?)
}

interface ExchangeRateRepository {
    suspend fun ratesOnOrBefore(date: LocalDate): RateTable
    suspend fun history(): List<ExchangeRate>
}

interface DebtRepository {
    suspend fun getAll(): List<Debt>
    suspend fun add(debt: Debt): Debt
    suspend fun update(debt: Debt)
    suspend fun close(id: String)
}

interface DraftRepository {
    fun all(): List<Draft>
    fun add(text: String): Draft
    fun remove(id: String)
    fun count(): Int
}

interface BalanceCheckRepository {
    suspend fun getRecent(accounts: List<Account>): List<BalanceCheck>
    suspend fun add(check: BalanceCheck): BalanceCheck
}
