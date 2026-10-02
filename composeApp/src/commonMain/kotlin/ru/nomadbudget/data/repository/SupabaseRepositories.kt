package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import ru.nomadbudget.data.dto.AccountDto
import ru.nomadbudget.data.dto.AccountInsertDto
import ru.nomadbudget.data.dto.BalanceCheckDto
import ru.nomadbudget.data.dto.BudgetLineDto
import ru.nomadbudget.data.dto.BudgetLineUpsertDto
import ru.nomadbudget.data.dto.CategoryDto
import ru.nomadbudget.data.dto.CategoryInsertDto
import ru.nomadbudget.data.dto.CurrencyDto
import ru.nomadbudget.data.dto.DebtDto
import ru.nomadbudget.data.dto.ExchangeRateDto
import ru.nomadbudget.data.dto.PeriodDto
import ru.nomadbudget.data.dto.PeriodInsertDto
import ru.nomadbudget.data.dto.SubcategoryDto
import ru.nomadbudget.data.dto.SubcategoryInsertDto
import ru.nomadbudget.data.dto.TransactionDto
import ru.nomadbudget.data.mapper.AccountMapper
import ru.nomadbudget.data.mapper.BalanceCheckMapper
import ru.nomadbudget.data.mapper.CategoryMapper
import ru.nomadbudget.data.mapper.CurrencyMapper
import ru.nomadbudget.data.mapper.DebtMapper
import ru.nomadbudget.data.mapper.RateMapper
import ru.nomadbudget.data.mapper.TransactionMapper
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DefaultRates
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.BalanceCheckRepository
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.CurrencyRepository
import ru.nomadbudget.domain.repository.DebtRepository
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.TransactionRepository

private object Tables {
    const val CURRENCIES = "currencies"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val SUBCATEGORIES = "subcategories"
    const val PERIODS = "periods"
    const val TRANSACTIONS = "transactions"
    const val BUDGET_LINES = "budget_lines"
    const val EXCHANGE_RATES = "exchange_rates"
    const val BALANCE_CHECKS = "balance_checks"
    const val DEBTS = "debts"
}

class CurrencyRepositoryImpl(private val client: SupabaseClient) : CurrencyRepository {

    private val mutex = Mutex()
    private var cached: List<Currency>? = null

    override suspend fun getAll(): List<Currency> = mutex.withLock {
        cached ?: loadFromRemote().also { cached = it }
    }

    private suspend fun loadFromRemote(): List<Currency> {
        val remote = client.from(Tables.CURRENCIES)
            .select { order("code", Order.ASCENDING) }
            .decodeList<CurrencyDto>()
            .map(CurrencyMapper::toDomain)
        val codes = remote.map { it.code }.toSet()
        return remote + Currency.builtIn.filter { it.code !in codes }
    }
}

suspend fun CurrencyRepository.byCode(): Map<String, Currency> = getAll().associateBy { it.code }

class AccountRepositoryImpl(
    private val client: SupabaseClient,
    private val currencies: CurrencyRepository,
) : AccountRepository {

    override suspend fun getAll(): List<Account> {
        val byCode = currencies.byCode()
        return client.from(Tables.ACCOUNTS)
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList<AccountDto>()
            .map { AccountMapper.toDomain(it, byCode) }
    }

    override suspend fun add(name: String, currency: Currency, kind: AccountKind, isSavings: Boolean, sortOrder: Int): Account {
        val byCode = currencies.byCode()
        return client.from(Tables.ACCOUNTS)
            .insert(AccountInsertDto(name, currency.code, kind.name.lowercase(), isSavings, sortOrder)) { select() }
            .decodeSingle<AccountDto>()
            .let { AccountMapper.toDomain(it, byCode) }
    }

    override suspend fun rename(id: String, name: String) {
        client.from(Tables.ACCOUNTS).update({ set("name", name) }) { filter { eq("id", id) } }
    }

    override suspend fun setSortOrder(id: String, sortOrder: Int) {
        client.from(Tables.ACCOUNTS).update({ set("sort_order", sortOrder) }) { filter { eq("id", id) } }
    }

    override suspend fun archive(id: String) {
        client.from(Tables.ACCOUNTS).update({ set("archived_at", Clock.System.now().toString()) }) { filter { eq("id", id) } }
    }
}

class CategoryRepositoryImpl(private val client: SupabaseClient) : CategoryRepository {

    override suspend fun getCategories(): List<Category> = client.from(Tables.CATEGORIES)
        .select { order("sort_order", Order.ASCENDING) }
        .decodeList<CategoryDto>()
        .map(CategoryMapper::toDomain)

    override suspend fun getSubcategories(): List<Subcategory> = client.from(Tables.SUBCATEGORIES)
        .select { order("name", Order.ASCENDING) }
        .decodeList<SubcategoryDto>()
        .map(CategoryMapper::toDomain)

    override suspend fun addCategory(name: String, kind: CategoryKind, sortOrder: Int): Category = client.from(Tables.CATEGORIES)
        .insert(CategoryInsertDto(name = name, kind = kind.name.lowercase(), sortOrder = sortOrder)) { select() }
        .decodeSingle<CategoryDto>()
        .let(CategoryMapper::toDomain)

    override suspend fun renameCategory(id: String, name: String) {
        client.from(Tables.CATEGORIES).update({ set("name", name) }) { filter { eq("id", id) } }
    }

    override suspend fun archiveCategory(id: String) {
        client.from(Tables.CATEGORIES).update({ set("archived_at", Clock.System.now().toString()) }) { filter { eq("id", id) } }
    }

    override suspend fun addSubcategory(categoryId: String, name: String): Subcategory = client.from(Tables.SUBCATEGORIES)
        .insert(SubcategoryInsertDto(categoryId = categoryId, name = name)) { select() }
        .decodeSingle<SubcategoryDto>()
        .let(CategoryMapper::toDomain)

    override suspend fun renameSubcategory(id: String, name: String) {
        client.from(Tables.SUBCATEGORIES).update({ set("name", name) }) { filter { eq("id", id) } }
    }

    override suspend fun deleteSubcategory(id: String) {
        client.from(Tables.SUBCATEGORIES).delete { filter { eq("id", id) } }
    }
}

class DebtRepositoryImpl(
    private val client: SupabaseClient,
    private val currencies: CurrencyRepository,
) : DebtRepository {

    override suspend fun getAll(): List<Debt> {
        val byCode = currencies.byCode()
        return client.from(Tables.DEBTS)
            .select { order("created_at", Order.ASCENDING) }
            .decodeList<DebtDto>()
            .mapNotNull { DebtMapper.toDomain(it, byCode) }
    }

    override suspend fun add(debt: Debt): Debt {
        val byCode = currencies.byCode()
        val saved = client.from(Tables.DEBTS)
            .insert(DebtMapper.toInsert(debt)) { select() }
            .decodeSingle<DebtDto>()
        return requireNotNull(DebtMapper.toDomain(saved, byCode)) { "Неизвестная валюта кредита" }
    }

    override suspend fun update(debt: Debt) {
        client.from(Tables.DEBTS).update(DebtMapper.toInsert(debt)) { filter { eq("id", debt.id) } }
    }

    override suspend fun close(id: String) {
        client.from(Tables.DEBTS).update({ set("closed_at", Clock.System.now().toString()) }) { filter { eq("id", id) } }
    }
}

class BalanceCheckRepositoryImpl(private val client: SupabaseClient) : BalanceCheckRepository {

    override suspend fun getRecent(accounts: List<Account>): List<BalanceCheck> {
        val byId = accounts.associateBy { it.id }
        return client.from(Tables.BALANCE_CHECKS)
            .select {
                order("check_date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
                limit(RECENT_CHECKS)
            }
            .decodeList<BalanceCheckDto>()
            .mapNotNull { BalanceCheckMapper.toDomain(it, byId) }
    }

    override suspend fun add(check: BalanceCheck): BalanceCheck = client.from(Tables.BALANCE_CHECKS)
        .insert(BalanceCheckMapper.toInsert(check)) { select() }
        .decodeSingle<BalanceCheckDto>()
        .let { check.copy(id = it.id) }

    private companion object {
        const val RECENT_CHECKS: Long = 30
    }
}

class PeriodRepositoryImpl(private val client: SupabaseClient) : PeriodRepository {

    override suspend fun ensure(period: Period): String {
        val existing = client.from(Tables.PERIODS)
            .select { filter { eq("start_date", period.start.toString()) } }
            .decodeSingleOrNull<PeriodDto>()
        if (existing != null) return existing.id
        return client.from(Tables.PERIODS)
            .insert(
                PeriodInsertDto(
                    startDate = period.start.toString(),
                    endDate = period.endExclusive.toString(),
                    title = period.title(),
                ),
            ) { select() }
            .decodeSingle<PeriodDto>()
            .id
    }
}

class TransactionRepositoryImpl(private val client: SupabaseClient) : TransactionRepository {

    override suspend fun getInPeriod(period: Period, accounts: List<Account>): List<Transaction> {
        val byId = accounts.associateBy { it.id }
        return client.from(Tables.TRANSACTIONS)
            .select {
                filter {
                    gte("tx_date", period.start.toString())
                    lt("tx_date", period.endExclusive.toString())
                }
                order("tx_date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList<TransactionDto>()
            .map { TransactionMapper.toDomain(it, byId) }
    }

    override suspend fun getAll(accounts: List<Account>): List<Transaction> {
        val byId = accounts.associateBy { it.id }
        return client.from(Tables.TRANSACTIONS)
            .select { order("tx_date", Order.DESCENDING) }
            .decodeList<TransactionDto>()
            .map { TransactionMapper.toDomain(it, byId) }
    }

    override suspend fun add(transaction: Transaction, accounts: List<Account>): Transaction {
        val byId = accounts.associateBy { it.id }
        return client.from(Tables.TRANSACTIONS)
            .insert(TransactionMapper.toInsert(transaction)) { select() }
            .decodeSingle<TransactionDto>()
            .let { TransactionMapper.toDomain(it, byId) }
    }

    override suspend fun update(transaction: Transaction, accounts: List<Account>): Transaction {
        val byId = accounts.associateBy { it.id }
        return client.from(Tables.TRANSACTIONS)
            .update(TransactionMapper.toInsert(transaction)) {
                filter { eq("id", transaction.id) }
                select()
            }
            .decodeSingle<TransactionDto>()
            .let { TransactionMapper.toDomain(it, byId) }
    }

    override suspend fun delete(id: String) {
        client.from(Tables.TRANSACTIONS).delete { filter { eq("id", id) } }
    }
}

class BudgetRepositoryImpl(private val client: SupabaseClient) : BudgetRepository {

    override suspend fun getLines(periodId: String): List<BudgetLine> = client.from(Tables.BUDGET_LINES)
        .select { filter { eq("period_id", periodId) } }
        .decodeList<BudgetLineDto>()
        .map { BudgetLine(categoryId = it.categoryId, planned = Money.rub(it.plannedBase), subcategoryId = it.subcategoryId) }

    override suspend fun setPlanned(periodId: String, line: BudgetLine) {
        client.from(Tables.BUDGET_LINES).upsert(
            BudgetLineUpsertDto(
                periodId = periodId,
                categoryId = line.categoryId,
                subcategoryId = line.subcategoryId,
                plannedBase = line.planned.minor,
            ),
        ) { onConflict = "period_id,category_id,subcategory_id" }
    }

    override suspend fun deleteLine(periodId: String, categoryId: String, subcategoryId: String?) {
        client.from(Tables.BUDGET_LINES).delete {
            filter {
                eq("period_id", periodId)
                eq("category_id", categoryId)
                if (subcategoryId == null) exact("subcategory_id", null) else eq("subcategory_id", subcategoryId)
            }
        }
    }
}

class ExchangeRateRepositoryImpl(
    private val client: SupabaseClient,
    private val currencies: CurrencyRepository,
) : ExchangeRateRepository {

    override suspend fun ratesOnOrBefore(date: LocalDate): RateTable {
        val byCode = currencies.byCode()
        val rows = client.from(Tables.EXCHANGE_RATES)
            .select {
                filter {
                    eq("base", Currency.BASE.code)
                    lte("rate_date", date.toString())
                }
                order("rate_date", Order.DESCENDING)
                limit(RECENT_ROWS)
            }
            .decodeList<ExchangeRateDto>()
        val latestPerQuote = rows.groupBy { it.quote }.values.map { it.first() }
        return DefaultRates.fill(latestPerQuote.mapNotNull { RateMapper.toDomain(it, byCode) })
    }

    override suspend fun history(): List<ExchangeRate> {
        val byCode = currencies.byCode()
        return client.from(Tables.EXCHANGE_RATES)
            .select {
                filter { eq("base", Currency.BASE.code) }
                order("rate_date", Order.ASCENDING)
                limit(HISTORY_ROWS)
            }
            .decodeList<ExchangeRateDto>()
            .mapNotNull { RateMapper.toDomain(it, byCode) }
    }

    private companion object {
        const val RECENT_ROWS: Long = 50
        const val HISTORY_ROWS: Long = 5000
    }
}
