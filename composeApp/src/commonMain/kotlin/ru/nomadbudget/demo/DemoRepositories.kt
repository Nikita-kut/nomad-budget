package ru.nomadbudget.demo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.nomadbudget.core.TodayProvider
import ru.nomadbudget.data.local.DraftsStore
import ru.nomadbudget.data.local.InMemoryStore
import ru.nomadbudget.data.local.OfflineCache
import ru.nomadbudget.data.local.UserScopedStore
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.logic.PeriodBudgetLine
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.BankRateCategory
import ru.nomadbudget.domain.model.BankRateWatch
import ru.nomadbudget.domain.model.BankRateWatchDraft
import ru.nomadbudget.domain.model.BankWatchData
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.BaseChange
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DebtCloseReason
import ru.nomadbudget.domain.model.EarlyPlan
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.IsoCurrencies
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.PaymentChange
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.UserSettings
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.AuthState
import ru.nomadbudget.domain.repository.BalanceCheckRepository
import ru.nomadbudget.domain.repository.BankRateWatchRepository
import ru.nomadbudget.domain.repository.BaseCurrencySource
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CapitalRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.CurrencyRepository
import ru.nomadbudget.domain.repository.DebtRepository
import ru.nomadbudget.domain.repository.DraftRepository
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.FlushResult
import ru.nomadbudget.domain.repository.InMemoryBaseCurrency
import ru.nomadbudget.domain.repository.KeyValueStore
import ru.nomadbudget.domain.repository.OfflineStatus
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.SessionCheck
import ru.nomadbudget.domain.repository.TransactionRepository
import ru.nomadbudget.domain.repository.UserSettingsRepository
import ru.nomadbudget.presentation.home.HomeViewModel

val demoModule = module {
    single { DemoDatabase(DemoDatabase.anchorToday()) }
    single<KeyValueStore> { UserScopedStore(InMemoryStore()) { DemoAuthRepository.USER_ID } }
    single<TodayProvider> { TodayProvider { get<DemoDatabase>().today } }
    single { OfflineCache(get()) } bind OfflineStatus::class
    single<UserSettingsRepository> { DemoUserSettingsRepository(get()) }
    single<BaseCurrencySource> { InMemoryBaseCurrency() }
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
    single<CapitalRepository> { DemoCapitalRepository(get()) }
    single<BankRateWatchRepository> { DemoBankRateWatchRepository(get()) }
    viewModelOf(::HomeViewModel)
}

private class DemoBankRateWatchRepository(private val db: DemoDatabase) : BankRateWatchRepository {
    override suspend fun load(currencies: List<Currency>): BankWatchData {
        val watch = db.bankWatch ?: return BankWatchData(botUsername = BOT)
        val history = db.bankSnapshots.filter { it.category == BankRateCategory.EXCHANGE }.sortedBy { it.takenAt }
        val latest = db.bankSnapshots.groupBy { it.category }.mapValues { (_, list) -> list.maxBy { it.takenAt } }
        return BankWatchData(watch, history, db.bankWeek.toList(), latest, db.telegramSubscribers.toList(), BOT)
    }

    override suspend fun create(draft: BankRateWatchDraft): BankRateWatch = fromDraft(db.newId("watch"), draft, "demo-${db.newId("code")}").also { db.bankWatch = it }

    override suspend fun update(id: String, draft: BankRateWatchDraft): BankRateWatch {
        val current = requireNotNull(db.bankWatch) { "Слежение не найдено" }
        return fromDraft(current.id, draft, current.linkCode).copy(enabled = current.enabled, lastRate = current.lastRate, lastCheckedAt = current.lastCheckedAt).also { db.bankWatch = it }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        db.bankWatch = db.bankWatch?.copy(enabled = enabled)
    }

    override suspend fun delete(id: String) {
        db.bankWatch = null
        db.telegramSubscribers.clear()
    }

    override suspend fun removeSubscriber(id: String) {
        db.telegramSubscribers.removeAll { it.id == id }
    }

    private fun fromDraft(id: String, draft: BankRateWatchDraft, linkCode: String) = BankRateWatch(
        id = id, provider = draft.provider, from = draft.from, to = draft.to, category = draft.category, target = draft.target,
        goodPercentile = draft.goodPercentile, improvePercent = draft.improvePercent, tradeOpen = draft.tradeOpen, tradeClose = draft.tradeClose, digestAt = draft.digestAt, quietFrom = draft.quietFrom, quietTo = draft.quietTo,
        timezone = draft.timezone, enabled = true, linkCode = linkCode,
    )

    private companion object {
        const val BOT = "nomad_budget_demo_bot"
    }
}

private class DemoAuthRepository : AuthRepository {
    private val flow = MutableStateFlow<AuthState>(AuthState.SignedIn(USER_ID))
    override val state: Flow<AuthState> = flow

    override val currentUserId: String? get() = (flow.value as? AuthState.SignedIn)?.userId

    override suspend fun signIn(email: String, password: String) {
        flow.value = AuthState.SignedIn(USER_ID)
    }

    override suspend fun signOut() {
        flow.value = AuthState.SignedOut()
    }

    override suspend fun ensureSession(): SessionCheck = SessionCheck.FRESH

    override suspend fun changePassword(newPassword: String) = Unit

    override suspend fun deleteAccount() = signOut()

    companion object {
        const val USER_ID = "demo"
    }
}

private class DemoUserSettingsRepository(private val db: DemoDatabase) : UserSettingsRepository {
    override fun cached(): UserSettings = db.settings

    override suspend fun get(): UserSettings = db.settings

    override suspend fun save(settings: UserSettings): UserSettings {
        db.settings = settings
        return settings
    }

    override suspend fun lastBaseChange(): BaseChange? = db.baseChange

    override suspend fun changeBase(code: String) = db.changeBase(code.trim().uppercase())

    override suspend fun revertBase() = db.revertBase()
}

private class DemoCurrencyRepository(private val db: DemoDatabase) : CurrencyRepository {
    override suspend fun getAll(): List<Currency> = db.currencies.toList()

    override suspend fun ensure(code: String): Currency {
        val existing = db.currencies.firstOrNull { it.code == code.trim().uppercase() }
        if (existing != null) return existing
        val iso = requireNotNull(IsoCurrencies.find(code)) { "Неизвестный код валюты $code" }
        return iso.toCurrency().also { db.currencies += it }
    }
}

private class DemoAccountRepository(private val db: DemoDatabase) : AccountRepository {
    override suspend fun getAll(): List<Account> = db.accounts.toList()

    override suspend fun add(
        name: String,
        currency: Currency,
        kind: AccountKind,
        isSavings: Boolean,
        isFund: Boolean,
        sortOrder: Int,
        openingBalance: Money,
    ): Account = Account(db.newId("account"), name, currency, kind, isSavings, openingBalance, sortOrder, isFund = isFund).also { db.accounts += it }

    override suspend fun setRole(id: String, isSavings: Boolean, isFund: Boolean) = replace(id) { it.copy(isSavings = isSavings, isFund = isFund) }

    override suspend fun setFund(id: String, monthly: Money?, goal: Money?, day: Int?) =
        replace(id) { it.copy(fundMonthly = monthly, fundGoal = goal, fundDay = day) }

    override suspend fun rename(id: String, name: String) = replace(id) { it.copy(name = name) }

    override suspend fun setSortOrder(id: String, sortOrder: Int) = replace(id) { it.copy(sortOrder = sortOrder) }

    override suspend fun setArchived(id: String, archived: Boolean) = replace(id) { it.copy(isArchived = archived) }

    private fun replace(id: String, change: (Account) -> Account) {
        db.accounts.replaceEach { if (it.id == id) change(it) else it }
    }
}

private class DemoCategoryRepository(private val db: DemoDatabase) : CategoryRepository {
    override suspend fun getCategories(): List<Category> = db.categories.toList()

    override suspend fun getSubcategories(): List<Subcategory> = db.subcategories.toList()

    override suspend fun addCategory(name: String, kind: CategoryKind, sortOrder: Int, isSystem: Boolean): Category =
        Category(db.newId("category"), name, kind, sortOrder, isSystem = isSystem).also { db.categories += it }

    override suspend fun setSystem(id: String, isSystem: Boolean) {
        db.categories.replaceEach { if (it.id == id) it.copy(isSystem = isSystem) else it }
    }

    override suspend fun setCategorySortOrder(id: String, sortOrder: Int) {
        db.categories.replaceEach { if (it.id == id) it.copy(sortOrder = sortOrder) else it }
    }

    override suspend fun renameCategory(id: String, name: String) {
        db.categories.replaceEach { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun setCategoryArchived(id: String, archived: Boolean) {
        db.categories.replaceEach { if (it.id == id) it.copy(isArchived = archived) else it }
    }

    override suspend fun addSubcategory(categoryId: String, name: String): Subcategory =
        Subcategory(db.newId("subcategory"), categoryId, name).also { db.subcategories += it }

    override suspend fun renameSubcategory(id: String, name: String) {
        db.subcategories.replaceEach { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteSubcategory(id: String) {
        db.subcategories.removeAll { it.id == id }
    }

    override suspend fun archiveSubcategory(id: String, archived: Boolean) {
        db.subcategories.replaceEach { if (it.id == id) it.copy(isArchived = archived) else it }
    }

    override suspend fun mergeSubcategory(fromId: String, toId: String) {
        db.transactions.replaceEach { if (it is Transaction.Expense && it.subcategoryId == fromId) it.copy(subcategoryId = toId) else it }
        db.budgetLines.values.forEach { lines ->
            val from = lines.firstOrNull { it.subcategoryId == fromId } ?: return@forEach
            val to = lines.firstOrNull { it.subcategoryId == toId }
            lines.remove(from)
            if (to == null) lines += from.copy(subcategoryId = toId) else lines.replaceEach { if (it === to) it.copy(planned = it.planned + from.planned) else it }
        }
        db.subcategories.removeAll { it.id == fromId }
    }
}

private class DemoPeriodRepository(private val db: DemoDatabase) : PeriodRepository {
    override suspend fun ensure(period: Period): String = db.periodId(period)
}

private class DemoTransactionRepository(private val db: DemoDatabase) : TransactionRepository {
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

    override suspend fun flushPending(): FlushResult = FlushResult(0, 0)

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

    override suspend fun getAllLines(): List<PeriodBudgetLine> = db.budgetLines.flatMap { (periodId, lines) ->
        val start = LocalDate.parse(periodId.removePrefix("demo:"))
        lines.map { PeriodBudgetLine(start, it) }
    }

    override suspend fun getSavingsTarget(periodId: String): Money? = db.savingsTargets[periodId]

    override suspend fun setSavingsTarget(periodId: String, target: Money?) {
        if (target == null) db.savingsTargets.remove(periodId) else db.savingsTargets[periodId] = target
    }

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

    override suspend fun update(debt: Debt, principalChanged: Boolean) {
        db.debts.replaceEach { if (it.id == debt.id) debt else it }
    }

    override suspend fun close(id: String, reason: DebtCloseReason, on: LocalDate, writeOff: Boolean) {
        db.debts.replaceEach { debt ->
            if (debt.id != id || debt.isClosed) return@replaceEach debt
            val tail = if (writeOff && !debt.principalRemaining.isNegative) debt.principalRemaining else Money.zero(debt.currency)
            debt.copy(isClosed = true, closedOn = on, closeReason = reason, writtenOff = tail, principalRemaining = debt.principalRemaining - tail)
        }
    }

    override suspend fun changePayment(debtId: String, effectiveFrom: LocalDate, payment: Money, sourceTransactionId: String?) {
        db.debts.replaceEach { debt ->
            if (debt.id != debtId) return@replaceEach debt
            val history = debt.paymentChanges.ifEmpty { listOf(PaymentChange(debt.openedOn ?: db.today.minus(1, DateTimeUnit.YEAR), debt.monthlyPayment)) }
            debt.copy(monthlyPayment = payment, paymentChanges = history + PaymentChange(effectiveFrom, payment))
        }
    }

    override suspend fun setEarlyPlan(debtId: String, periodStart: LocalDate, amount: Money, fromCushion: Boolean) {
        db.debts.replaceEach { debt ->
            if (debt.id != debtId) return@replaceEach debt
            debt.copy(earlyPlans = debt.earlyPlans.filterNot { it.periodStart == periodStart } + EarlyPlan(periodStart, amount, fromCushion))
        }
    }

    override suspend fun reopen(id: String) {
        db.debts.replaceEach { debt ->
            if (debt.id != id) return@replaceEach debt
            debt.copy(isClosed = false, closedOn = null, closeReason = null, principalRemaining = debt.principalRemaining + debt.writtenOff, writtenOff = Money.zero(debt.currency))
        }
    }
}

private class DemoBalanceCheckRepository(private val db: DemoDatabase) : BalanceCheckRepository {
    override suspend fun getRecent(accounts: List<Account>): List<BalanceCheck> = db.balanceChecks.sortedByDescending { it.date }

    override suspend fun add(check: BalanceCheck): BalanceCheck = check.copy(id = db.newId("check")).also { db.balanceChecks += it }
}

private class DemoCapitalRepository(private val db: DemoDatabase) : ru.nomadbudget.domain.repository.CapitalRepository {
    override suspend fun getGoals(): List<ru.nomadbudget.domain.logic.CapitalGoal> = db.capitalGoals.toList()

    override suspend fun addGoal(name: String, amount: Money, targetDate: LocalDate): ru.nomadbudget.domain.logic.CapitalGoal =
        ru.nomadbudget.domain.logic.CapitalGoal(db.newId("goal"), name, amount, targetDate).also { db.capitalGoals += it }

    override suspend fun updateGoal(goal: ru.nomadbudget.domain.logic.CapitalGoal) {
        db.capitalGoals.replaceEach { if (it.id == goal.id) goal else it }
    }

    override suspend fun deleteGoal(id: String) {
        db.capitalGoals.removeAll { it.id == id }
    }
}
