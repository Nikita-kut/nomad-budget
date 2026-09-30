package ru.nomadbudget.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction

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
    suspend fun delete(id: String)
}

interface BudgetRepository {
    suspend fun getLines(periodId: String): List<BudgetLine>
    suspend fun setPlanned(periodId: String, line: BudgetLine)
    suspend fun deleteLine(periodId: String, categoryId: String, subcategoryId: String?)
}

interface ExchangeRateRepository {
    suspend fun ratesOnOrBefore(date: LocalDate): RateTable
}

interface BalanceCheckRepository {
    suspend fun getRecent(accounts: List<Account>): List<BalanceCheck>
    suspend fun add(check: BalanceCheck): BalanceCheck
}
