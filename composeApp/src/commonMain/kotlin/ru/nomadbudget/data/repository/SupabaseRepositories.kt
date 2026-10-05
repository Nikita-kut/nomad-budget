package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import ru.nomadbudget.data.local.OfflineCache
import ru.nomadbudget.data.local.PendingQueue
import ru.nomadbudget.data.local.PendingTransaction
import io.github.jan.supabase.exceptions.RestException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
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

class CurrencyRepositoryImpl(private val client: SupabaseClient, private val cache: OfflineCache) : CurrencyRepository {

    private val mutex = Mutex()
    private var cached: List<Currency>? = null

    override suspend fun getAll(): List<Currency> = mutex.withLock {
        cached ?: loadFromRemote().also { cached = it }
    }

    private suspend fun loadFromRemote(): List<Currency> {
        val remote = cache.throughOrDefault("currencies", ListSerializer(CurrencyDto.serializer()), emptyList()) {
            client.from(Tables.CURRENCIES).select { order("code", Order.ASCENDING) }.decodeList<CurrencyDto>()
        }.map(CurrencyMapper::toDomain)
        val codes = remote.map { it.code }.toSet()
        return remote + Currency.builtIn.filter { it.code !in codes }
    }
}

suspend fun CurrencyRepository.byCode(): Map<String, Currency> = getAll().associateBy { it.code }

class AccountRepositoryImpl(
    private val client: SupabaseClient,
    private val currencies: CurrencyRepository,
    private val cache: OfflineCache,
) : AccountRepository {

    override suspend fun getAll(): List<Account> {
        val byCode = currencies.byCode()
        return cache.through("accounts", ListSerializer(AccountDto.serializer())) {
            client.from(Tables.ACCOUNTS).select { order("sort_order", Order.ASCENDING) }.decodeList<AccountDto>()
        }.map { AccountMapper.toDomain(it, byCode) }
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

class CategoryRepositoryImpl(private val client: SupabaseClient, private val cache: OfflineCache) : CategoryRepository {

    override suspend fun getCategories(): List<Category> = cache.through("categories", ListSerializer(CategoryDto.serializer())) {
        client.from(Tables.CATEGORIES).select { order("sort_order", Order.ASCENDING) }.decodeList<CategoryDto>()
    }.map(CategoryMapper::toDomain)

    override suspend fun getSubcategories(): List<Subcategory> = cache.through("subcategories", ListSerializer(SubcategoryDto.serializer())) {
        client.from(Tables.SUBCATEGORIES).select { order("name", Order.ASCENDING) }.decodeList<SubcategoryDto>()
    }.map(CategoryMapper::toDomain)

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
    private val cache: OfflineCache,
) : DebtRepository {

    override suspend fun getAll(): List<Debt> {
        val byCode = currencies.byCode()
        return cache.throughOrDefault("debts", ListSerializer(DebtDto.serializer()), emptyList()) {
            client.from(Tables.DEBTS).select { order("created_at", Order.ASCENDING) }.decodeList<DebtDto>()
        }.mapNotNull { DebtMapper.toDomain(it, byCode) }
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

class BalanceCheckRepositoryImpl(private val client: SupabaseClient, private val cache: OfflineCache) : BalanceCheckRepository {

    override suspend fun getRecent(accounts: List<Account>): List<BalanceCheck> {
        val byId = accounts.associateBy { it.id }
        return cache.throughOrDefault("balance_checks", ListSerializer(BalanceCheckDto.serializer()), emptyList()) {
            client.from(Tables.BALANCE_CHECKS)
                .select {
                    order("check_date", Order.DESCENDING)
                    order("created_at", Order.DESCENDING)
                    limit(RECENT_CHECKS)
                }
                .decodeList<BalanceCheckDto>()
        }.mapNotNull { BalanceCheckMapper.toDomain(it, byId) }
    }

    override suspend fun add(check: BalanceCheck): BalanceCheck = client.from(Tables.BALANCE_CHECKS)
        .insert(BalanceCheckMapper.toInsert(check)) { select() }
        .decodeSingle<BalanceCheckDto>()
        .let { check.copy(id = it.id) }

    private companion object {
        const val RECENT_CHECKS: Long = 30
    }
}

class PeriodRepositoryImpl(private val client: SupabaseClient, private val cache: OfflineCache) : PeriodRepository {

    override suspend fun ensure(period: Period): String {
        val key = "period:${period.start}"
        return cache.throughOrDefault(key, String.serializer(), OFFLINE_PREFIX + period.start) {
            val existing = client.from(Tables.PERIODS)
                .select { filter { eq("start_date", period.start.toString()) } }
                .decodeSingleOrNull<PeriodDto>()
            existing?.id ?: client.from(Tables.PERIODS)
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

    companion object {
        const val OFFLINE_PREFIX = "offline:"
    }
}

@OptIn(ExperimentalUuidApi::class)
class TransactionRepositoryImpl(
    private val client: SupabaseClient,
    private val cache: OfflineCache,
    private val queue: PendingQueue,
) : TransactionRepository {

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
        val remote = cache.through("transactions", ListSerializer(TransactionDto.serializer())) {
            client.from(Tables.TRANSACTIONS).select { order("tx_date", Order.DESCENDING) }.decodeList<TransactionDto>()
        }
        val remoteIds = remote.map { it.id }.toSet()
        val pending = queue.all()
            .filterNot { it.id in remoteIds }
            .map { TransactionMapper.toDomain(TransactionMapper.toDto(it.id, it.dto), byId, pending = true) }
        return pending + remote.map { TransactionMapper.toDomain(it, byId) }
    }

    override suspend fun add(transaction: Transaction, accounts: List<Account>): Transaction {
        val byId = accounts.associateBy { it.id }
        val id = Uuid.random().toString()
        val insert = TransactionMapper.toInsert(transaction).copy(id = id)
        return try {
            client.from(Tables.TRANSACTIONS)
                .insert(insert) { select() }
                .decodeSingle<TransactionDto>()
                .let { TransactionMapper.toDomain(it, byId) }
        } catch (e: Exception) {
            if (!OfflineCache.isOffline(e)) throw e
            queue.add(PendingTransaction(id, insert))
            TransactionMapper.toDomain(TransactionMapper.toDto(id, insert), byId, pending = true)
        }
    }

    override suspend fun flushPending(): Int {
        var sent = 0
        for (item in queue.all()) {
            try {
                client.from(Tables.TRANSACTIONS).insert(item.dto)
                queue.remove(item.id)
                sent++
            } catch (e: RestException) {
                if (e.message.orEmpty().contains(DUPLICATE_KEY) || e.message.orEmpty().contains("duplicate", ignoreCase = true)) {
                    queue.remove(item.id)
                    sent++
                } else {
                    throw e
                }
            } catch (e: Exception) {
                break
            }
        }
        return sent
    }

    override fun pendingCount(): Int = queue.size

    override suspend fun update(transaction: Transaction, accounts: List<Account>): Transaction {
        require(!queue.contains(transaction.id)) { "Операция ещё не отправлена, нажми «Обновить» при сети" }
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
        if (queue.contains(id)) {
            queue.remove(id)
            return
        }
        client.from(Tables.TRANSACTIONS).delete { filter { eq("id", id) } }
    }

    private companion object {
        const val DUPLICATE_KEY = "23505"
    }
}

class BudgetRepositoryImpl(private val client: SupabaseClient, private val cache: OfflineCache) : BudgetRepository {

    override suspend fun getLines(periodId: String): List<BudgetLine> {
        if (periodId.startsWith(PeriodRepositoryImpl.OFFLINE_PREFIX)) return emptyList()
        return cache.throughOrDefault("lines:$periodId", ListSerializer(BudgetLineDto.serializer()), emptyList()) {
            client.from(Tables.BUDGET_LINES).select { filter { eq("period_id", periodId) } }.decodeList<BudgetLineDto>()
        }.map { BudgetLine(categoryId = it.categoryId, planned = Money.rub(it.plannedBase), subcategoryId = it.subcategoryId) }
    }

    override suspend fun getSavingsTarget(periodId: String): Money? {
        if (periodId.startsWith(PeriodRepositoryImpl.OFFLINE_PREFIX)) return null
        return cache.throughOrDefault("target:$periodId", ListSerializer(PeriodDto.serializer()), emptyList()) {
            client.from(Tables.PERIODS).select { filter { eq("id", periodId) } }.decodeList<PeriodDto>()
        }.firstOrNull()?.savingsTarget?.let(Money::rub)
    }

    override suspend fun setSavingsTarget(periodId: String, target: Money?) {
        client.from(Tables.PERIODS).update({ set("savings_target", target?.minor) }) { filter { eq("id", periodId) } }
    }

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
    private val cache: OfflineCache,
) : ExchangeRateRepository {

    override suspend fun ratesOnOrBefore(date: LocalDate): RateTable {
        val byCode = currencies.byCode()
        val rows = cache.throughOrDefault("rates", ListSerializer(ExchangeRateDto.serializer()), emptyList()) {
            client.from(Tables.EXCHANGE_RATES)
                .select {
                    filter {
                        eq("base", Currency.BASE.code)
                        lte("rate_date", date.toString())
                    }
                    order("rate_date", Order.DESCENDING)
                    limit(RECENT_ROWS)
                }
                .decodeList<ExchangeRateDto>()
        }
        val latestPerQuote = rows.groupBy { it.quote }.values.map { it.first() }
        return DefaultRates.fill(latestPerQuote.mapNotNull { RateMapper.toDomain(it, byCode) })
    }

    override suspend fun history(): List<ExchangeRate> {
        val byCode = currencies.byCode()
        return cache.throughOrDefault("rate_history", ListSerializer(ExchangeRateDto.serializer()), emptyList()) {
            client.from(Tables.EXCHANGE_RATES)
                .select {
                    filter { eq("base", Currency.BASE.code) }
                    order("rate_date", Order.ASCENDING)
                    limit(HISTORY_ROWS)
                }
                .decodeList<ExchangeRateDto>()
        }.mapNotNull { RateMapper.toDomain(it, byCode) }
    }

    private companion object {
        const val RECENT_ROWS: Long = 50
        const val HISTORY_ROWS: Long = 5000
    }
}
