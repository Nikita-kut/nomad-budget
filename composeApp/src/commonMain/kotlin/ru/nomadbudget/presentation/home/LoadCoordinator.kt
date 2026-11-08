package ru.nomadbudget.presentation.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.logic.CapitalGoal
import ru.nomadbudget.domain.logic.PeriodBudgetLine
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.UserSettings
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.BalanceCheckRepository
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CapitalRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.CurrencyRepository
import ru.nomadbudget.domain.repository.DebtRepository
import ru.nomadbudget.domain.repository.DraftRepository
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.FlushResult
import ru.nomadbudget.domain.repository.OfflineStatus
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.SessionCheck
import ru.nomadbudget.domain.repository.TransactionRepository
import ru.nomadbudget.domain.repository.UserSettingsRepository
import ru.nomadbudget.presentation.format.operations
import kotlin.time.Clock

internal class LoadCoordinator(
    private val store: HomeStore,
    private val runner: ActionRunner,
    private val scope: CoroutineScope,
    private val auth: AuthRepository,
    private val currencyRepository: CurrencyRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val periodRepository: PeriodRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val rateRepository: ExchangeRateRepository,
    private val balanceCheckRepository: BalanceCheckRepository,
    private val debtRepository: DebtRepository,
    private val capitalRepository: CapitalRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val draftRepository: DraftRepository,
    private val offline: OfflineStatus,
    private val today: LocalDate,
    private val isPendingDelete: (String) -> Boolean,
) {
    private var loadJob: Job? = null
    private var periodJob: Job? = null

    private val startDay: Int get() = store.value.periodStartDay

    fun initialState(): HomeState = initialState(userSettingsRepository, today)

    companion object {
        fun initialState(userSettingsRepository: UserSettingsRepository, today: LocalDate): HomeState {
            val settings = runCatching { userSettingsRepository.cached() }.getOrNull() ?: UserSettings()
            return HomeState(today = today, period = SalaryCycle.periodContaining(today, settings.periodStartDay), settings = settings)
        }
    }

    fun cancel() {
        periodJob?.cancel()
        loadJob?.cancel()
    }

    fun load() = reload(initial = true)

    fun refresh() = reload(initial = false)

    fun showPreviousPeriod() = switchPeriod(SalaryCycle.previous(store.value.period, startDay))

    fun showNextPeriod() = switchPeriod(SalaryCycle.next(store.value.period, startDay))

    fun showCurrentPeriod() = switchPeriod(SalaryCycle.periodContaining(today, startDay))

    fun switchPeriod(period: Period) {
        periodJob?.cancel()
        periodJob = runner.run("Не удалось загрузить период") {
            store.update { it.copy(period = period, periodId = null, budgetLines = emptyList(), savingsTarget = null) }
            val periodId = periodRepository.ensure(period)
            val (lines, savingsTarget) = coroutineScope {
                val lines = async { budgetRepository.getLines(periodId) }
                val target = async { budgetRepository.getSavingsTarget(periodId) }
                lines.await() to target.await()
            }
            store.update { if (it.period == period) it.copy(periodId = periodId, budgetLines = lines, savingsTarget = savingsTarget) else it }
        }
    }

    private fun periodFor(previous: HomeState, settings: UserSettings): Period =
        if (settings.periodStartDay == previous.periodStartDay) previous.period else SalaryCycle.periodContaining(today, settings.periodStartDay)

    private fun reload(initial: Boolean) {
        if (store.value.refreshing) return
        loadJob = scope.launch {
            try {
                store.update { it.copy(loading = initial, refreshing = !initial, error = null) }
                offline.beginLoad()
                if (auth.ensureSession() == SessionCheck.EXPIRED) {
                    store.update { it.copy(loading = false, refreshing = false) }
                    return@launch
                }
                val flush = runCatching { transactionRepository.flushPending() }.getOrDefault(FlushResult(0, 0))
                val previous = store.value
                val loaded = fetch(previous)
                val transactions = loaded.transactions.filterNot { isPendingDelete(it.id) }
                store.update {
                    val startDayChanged = loaded.settings.periodStartDay != it.periodStartDay
                    val applyPlan = startDayChanged || it.period == loaded.period
                    it.copy(
                        loading = false,
                        refreshing = false,
                        lastSyncedAt = if (offline.servedFromCache) it.lastSyncedAt else Clock.System.now(),
                        offline = offline.servedFromCache,
                        cachedAt = offline.oldestCachedAt,
                        pendingCount = transactionRepository.pendingCount(),
                        drafts = draftRepository.all(),
                        currencies = loaded.currencies,
                        accounts = loaded.accounts,
                        categories = loaded.categories,
                        subcategories = loaded.subcategories,
                        transactions = transactions,
                        rates = loaded.rates,
                        balanceChecks = loaded.checks,
                        debts = loaded.debts,
                        rateHistory = loaded.rateHistory,
                        period = if (startDayChanged) loaded.period else it.period,
                        periodId = if (applyPlan) loaded.periodId else it.periodId,
                        budgetLines = if (applyPlan) loaded.lines else it.budgetLines,
                        savingsTarget = if (applyPlan) loaded.savingsTarget else it.savingsTarget,
                        allPlanLines = loaded.allPlanLines,
                        capitalGoals = loaded.capitalGoals,
                        settings = loaded.settings,
                    )
                }
                if (flush.sent > 0) store.message("Отправлено: ${operations(flush.sent)}")
                if (flush.rejected > 0) store.message("Не отправлено: ${operations(flush.rejected)}. Они остались в очереди")
                if (offline.servedFromCache && !initial) store.message("Нет сети, показаны сохранённые данные")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.update { it.copy(loading = false, refreshing = false, error = if (initial) e.message ?: "Ошибка загрузки" else it.error) }
                if (!initial) store.message(e.message ?: "Не удалось обновить")
            }
        }
    }

    private suspend fun fetch(previous: HomeState): LoadedData = coroutineScope {
        val settingsAsync = async { userSettingsRepository.get() }
        val currencies = async { currencyRepository.getAll() }
        val accountsAsync = async { accountRepository.getAll() }
        val categories = async { categoryRepository.getCategories() }
        val subcategories = async { categoryRepository.getSubcategories() }
        val rates = async { rateRepository.ratesOnOrBefore(today) }
        val debts = async { debtRepository.getAll() }
        val rateHistory = async { rateRepository.history() }
        val allPlanLines = async { runCatching { budgetRepository.getAllLines() }.getOrDefault(previous.allPlanLines) }
        val capitalGoals = async { runCatching { capitalRepository.getGoals() }.getOrDefault(previous.capitalGoals) }
        val plan = async {
            val period = periodFor(previous, settingsAsync.await())
            val id = periodRepository.ensure(period)
            val lines = async { budgetRepository.getLines(id) }
            val target = async { budgetRepository.getSavingsTarget(id) }
            PlanData(period, id, lines.await(), target.await())
        }
        val accounts = accountsAsync.await()
        val transactions = async { transactionRepository.getAll(accounts) }
        val checks = async { balanceCheckRepository.getRecent(accounts) }
        val planData = plan.await()
        LoadedData(
            currencies = currencies.await(),
            accounts = accounts,
            categories = categories.await(),
            subcategories = subcategories.await(),
            transactions = transactions.await(),
            rates = rates.await(),
            checks = checks.await(),
            debts = debts.await(),
            rateHistory = rateHistory.await(),
            period = planData.period,
            periodId = planData.periodId,
            lines = planData.lines,
            savingsTarget = planData.savingsTarget,
            allPlanLines = allPlanLines.await(),
            capitalGoals = capitalGoals.await(),
            settings = settingsAsync.await(),
        )
    }

    private class PlanData(val period: Period, val periodId: String, val lines: List<BudgetLine>, val savingsTarget: Money?)

    private class LoadedData(
        val currencies: List<Currency>,
        val accounts: List<Account>,
        val categories: List<Category>,
        val subcategories: List<Subcategory>,
        val transactions: List<Transaction>,
        val rates: RateTable,
        val checks: List<BalanceCheck>,
        val debts: List<Debt>,
        val rateHistory: List<ExchangeRate>,
        val period: Period,
        val periodId: String,
        val lines: List<BudgetLine>,
        val savingsTarget: Money?,
        val allPlanLines: List<PeriodBudgetLine>,
        val capitalGoals: List<CapitalGoal>,
        val settings: UserSettings,
    )
}
