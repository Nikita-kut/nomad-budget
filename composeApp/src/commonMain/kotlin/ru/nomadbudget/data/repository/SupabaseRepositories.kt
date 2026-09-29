package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalDate
import ru.nomadbudget.data.dto.AccountDto
import ru.nomadbudget.data.dto.BudgetLineDto
import ru.nomadbudget.data.dto.BudgetLineUpsertDto
import ru.nomadbudget.data.dto.CategoryDto
import ru.nomadbudget.data.dto.ExchangeRateDto
import ru.nomadbudget.data.dto.PeriodDto
import ru.nomadbudget.data.dto.PeriodInsertDto
import ru.nomadbudget.data.dto.SubcategoryDto
import ru.nomadbudget.data.dto.SubcategoryInsertDto
import ru.nomadbudget.data.dto.TransactionDto
import ru.nomadbudget.data.mapper.AccountMapper
import ru.nomadbudget.data.mapper.CategoryMapper
import ru.nomadbudget.data.mapper.RateMapper
import ru.nomadbudget.data.mapper.TransactionMapper
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.DefaultRates
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.TransactionRepository

private object Tables {
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val SUBCATEGORIES = "subcategories"
    const val PERIODS = "periods"
    const val TRANSACTIONS = "transactions"
    const val BUDGET_LINES = "budget_lines"
    const val EXCHANGE_RATES = "exchange_rates"
}

class AccountRepositoryImpl(private val client: SupabaseClient) : AccountRepository {

    override suspend fun getAll(): List<Account> = client.from(Tables.ACCOUNTS)
        .select {
            filter { exact("archived_at", null) }
            order("sort_order", Order.ASCENDING)
        }
        .decodeList<AccountDto>()
        .map(AccountMapper::toDomain)
}

class CategoryRepositoryImpl(private val client: SupabaseClient) : CategoryRepository {

    override suspend fun getCategories(): List<Category> = client.from(Tables.CATEGORIES)
        .select {
            filter { exact("archived_at", null) }
            order("sort_order", Order.ASCENDING)
        }
        .decodeList<CategoryDto>()
        .map(CategoryMapper::toDomain)

    override suspend fun getSubcategories(): List<Subcategory> = client.from(Tables.SUBCATEGORIES)
        .select { order("name", Order.ASCENDING) }
        .decodeList<SubcategoryDto>()
        .map(CategoryMapper::toDomain)

    override suspend fun addSubcategory(categoryId: String, name: String): Subcategory = client.from(Tables.SUBCATEGORIES)
        .insert(SubcategoryInsertDto(categoryId = categoryId, name = name)) { select() }
        .decodeSingle<SubcategoryDto>()
        .let(CategoryMapper::toDomain)
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

    override suspend fun delete(id: String) {
        client.from(Tables.TRANSACTIONS).delete { filter { eq("id", id) } }
    }
}

class BudgetRepositoryImpl(private val client: SupabaseClient) : BudgetRepository {

    override suspend fun getLines(periodId: String): List<BudgetLine> = client.from(Tables.BUDGET_LINES)
        .select { filter { eq("period_id", periodId) } }
        .decodeList<BudgetLineDto>()
        .map { BudgetLine(categoryId = it.categoryId, planned = Money.rub(it.plannedBase)) }

    override suspend fun setPlanned(periodId: String, line: BudgetLine) {
        client.from(Tables.BUDGET_LINES).upsert(
            BudgetLineUpsertDto(
                periodId = periodId,
                categoryId = line.categoryId,
                plannedBase = line.planned.minor,
            ),
        ) { onConflict = "period_id,category_id" }
    }
}

class ExchangeRateRepositoryImpl(private val client: SupabaseClient) : ExchangeRateRepository {

    override suspend fun ratesOnOrBefore(date: LocalDate): RateTable {
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
        return DefaultRates.fill(latestPerQuote.map(RateMapper::toDomain))
    }

    private companion object {
        const val RECENT_ROWS: Long = 20
    }
}
