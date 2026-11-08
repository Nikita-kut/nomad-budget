package ru.nomadbudget.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import ru.nomadbudget.core.TodayProvider
import ru.nomadbudget.data.local.OfflineCache
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.logic.BalanceCalculator
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.CorrectionCategory
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Draft
import ru.nomadbudget.domain.model.DraftParser
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.BalanceCheckRepository
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.CurrencyRepository
import ru.nomadbudget.domain.repository.DebtRepository
import ru.nomadbudget.domain.repository.DraftRepository
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.TransactionRepository
import ru.nomadbudget.presentation.format.MoneyFormat
import kotlin.time.Clock
import ru.nomadbudget.data.local.KeyValueStore
import ru.nomadbudget.presentation.theme.ThemeSettings
import kotlinx.coroutines.async
import ru.nomadbudget.domain.repository.FlushResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.logic.PeriodBudgetLine
import ru.nomadbudget.domain.logic.CapitalGoal
import ru.nomadbudget.domain.logic.CapitalSettings

class HomeViewModel(
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
    private val offlineCache: OfflineCache,
    private val draftRepository: DraftRepository,
    todayProvider: TodayProvider,
    private val localStore: KeyValueStore,
    private val capitalRepository: ru.nomadbudget.domain.repository.CapitalRepository,
) : ViewModel() {

    private val _undoRequests = Channel<UndoRequest>(Channel.BUFFERED)
    val undoRequests: Flow<UndoRequest> = _undoRequests.receiveAsFlow()
    private val pendingDeletes = mutableMapOf<String, Pair<Int, Transaction>>()
    private val deleteTimers = mutableMapOf<String, Job>()

    private val _navigateToEntry = Channel<Unit>(Channel.BUFFERED)
    val navigateToEntry: Flow<Unit> = _navigateToEntry.receiveAsFlow()

    private val today: LocalDate = todayProvider.today()

    private val _state = MutableStateFlow(HomeState(today = today, period = SalaryCycle.periodContaining(today)))
    val state: StateFlow<HomeState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        load()
    }

    fun load() = reload(initial = true)

    fun refresh() = reload(initial = false)

    private fun reload(initial: Boolean) {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            try {
                _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
                offlineCache.beginLoad()
                val flush = runCatching { transactionRepository.flushPending() }.getOrDefault(FlushResult(0, 0))
                val sent = flush.sent
                val period = _state.value.period
                val previous = _state.value
                val loaded = coroutineScope {
                    val currencies = async { currencyRepository.getAll() }
                    val accountsAsync = async { accountRepository.getAll() }
                    val categories = async { categoryRepository.getCategories() }
                    val subcategories = async { categoryRepository.getSubcategories() }
                    val rates = async { rateRepository.ratesOnOrBefore(today) }
                    val debts = async { debtRepository.getAll() }
                    val rateHistory = async { rateRepository.history() }
                    val allPlanLines = async { runCatching { budgetRepository.getAllLines() }.getOrDefault(previous.allPlanLines) }
                    val capitalGoals = async { runCatching { capitalRepository.getGoals() }.getOrDefault(previous.capitalGoals) }
                    val capitalSettings = async { runCatching { capitalRepository.getSettings() }.getOrDefault(previous.capitalSettings) }
                    val plan = async {
                        val id = periodRepository.ensure(period)
                        val lines = async { budgetRepository.getLines(id) }
                        val target = async { budgetRepository.getSavingsTarget(id) }
                        Triple(id, lines.await(), target.await())
                    }
                    val accounts = accountsAsync.await()
                    val transactions = async { transactionRepository.getAll(accounts) }
                    val checks = async { balanceCheckRepository.getRecent(accounts) }
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
                        periodId = plan.await().first,
                        lines = plan.await().second,
                        savingsTarget = plan.await().third,
                        allPlanLines = allPlanLines.await(),
                        capitalGoals = capitalGoals.await(),
                        capitalSettings = capitalSettings.await(),
                    )
                }
                val currencies = loaded.currencies
                val accounts = loaded.accounts
                val categories = loaded.categories
                val subcategories = loaded.subcategories
                val transactions = loaded.transactions.filterNot { it.id in pendingDeletes }
                val rates = loaded.rates
                val checks = loaded.checks
                val debts = loaded.debts
                val rateHistory = loaded.rateHistory
                val periodId = loaded.periodId
                val lines = loaded.lines
                val savingsTarget = loaded.savingsTarget
                val allPlanLines = loaded.allPlanLines
                val capitalGoals = loaded.capitalGoals
                val capitalSettings = loaded.capitalSettings
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        lastSyncedAt = if (offlineCache.servedFromCache) it.lastSyncedAt else Clock.System.now(),
                        offline = offlineCache.servedFromCache,
                        cachedAt = offlineCache.oldestCachedAt,
                        pendingCount = transactionRepository.pendingCount(),
                        drafts = draftRepository.all(),
                        currencies = currencies,
                        accounts = accounts,
                        categories = categories,
                        subcategories = subcategories,
                        transactions = transactions,
                        rates = rates,
                        balanceChecks = checks,
                        debts = debts,
                        rateHistory = rateHistory,
                        periodId = if (it.period == period) periodId else it.periodId,
                        budgetLines = if (it.period == period) lines else it.budgetLines,
                        savingsTarget = if (it.period == period) savingsTarget else it.savingsTarget,
                        allPlanLines = allPlanLines,
                        capitalGoals = capitalGoals,
                        capitalSettings = capitalSettings,
                    )
                }
                if (sent > 0) _messages.send("Отправлено операций: $sent")
                if (flush.rejected > 0) _messages.send("Сервер не принял операций: ${flush.rejected}. Они остались в очереди, остальные отправлены")
                if (offlineCache.servedFromCache && !initial) _messages.send("Нет сети, показаны сохранённые данные")
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, error = if (initial) e.message ?: "Ошибка загрузки" else it.error) }
                if (!initial) _messages.send(e.message ?: "Не удалось обновить")
            }
        }
    }

    fun showPreviousPeriod() = switchPeriod(SalaryCycle.previous(_state.value.period))

    fun showNextPeriod() = switchPeriod(SalaryCycle.next(_state.value.period))

    fun showCurrentPeriod() = switchPeriod(SalaryCycle.periodContaining(today))

    private var periodJob: Job? = null

    private fun switchPeriod(period: Period) {
        periodJob?.cancel()
        periodJob = viewModelScope.launch {
            try {
                _state.update { it.copy(period = period, periodId = null, budgetLines = emptyList(), savingsTarget = null) }
                val periodId = periodRepository.ensure(period)
                val (lines, savingsTarget) = coroutineScope {
                    val lines = async { budgetRepository.getLines(periodId) }
                    val target = async { budgetRepository.getSavingsTarget(periodId) }
                    lines.await() to target.await()
                }
                _state.update { if (it.period == period) it.copy(periodId = periodId, budgetLines = lines, savingsTarget = savingsTarget) else it }
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось загрузить период")
            }
        }
    }

    fun addEntry(draft: EntryDraft) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val current = _state.value
                val rates = current.rates
                require(rates.hasRate(draft.amount.currency)) { "Нет курса для ${draft.amount.currency.code}, заполни таблицу курсов" }
                val rateSource = rateSourceFor(draft.amount)
                val debt = draft.debtId?.takeIf { draft.type == EntryType.EXPENSE }?.let { id -> current.debts.firstOrNull { it.id == id } }
                val principal = debt?.let { DebtCalculator.principalFor(it, draft.amount, draft.debtEarly) }
                val transaction = when (draft.type) {
                    EntryType.EXPENSE -> Transaction.Expense(
                        id = "",
                        date = draft.date,
                        accountId = draft.accountId,
                        amount = draft.amount,
                        categoryId = requireNotNull(draft.categoryId) { "Выбери категорию" },
                        subcategoryId = resolveSubcategory(draft.categoryId, draft.subcategoryName),
                        amountBase = rates.toBase(draft.amount),
                        rateSource = rateSource,
                        note = draft.note,
                        debtId = debt?.id,
                        debtPrincipal = principal,
                        debtEarly = debt != null && draft.debtEarly,
                    )
                    EntryType.INCOME -> Transaction.Income(
                        id = "",
                        date = draft.date,
                        accountId = draft.accountId,
                        amount = draft.amount,
                        categoryId = requireNotNull(draft.categoryId) { "Выбери категорию" },
                        amountBase = rates.toBase(draft.amount),
                        rateSource = rateSource,
                        note = draft.note,
                    )
                    EntryType.TRANSFER -> Transaction.Transfer(
                        id = "",
                        date = draft.date,
                        fromAccountId = draft.accountId,
                        toAccountId = requireNotNull(draft.toAccountId) { "Выбери счёт назначения" },
                        amount = draft.amount,
                        amountBase = rates.toBase(draft.amount),
                        note = draft.note,
                    )
                }
                val saved = transactionRepository.add(transaction, current.accounts)
                val second = if (transaction is Transaction.Expense && draft.splitAccountId != null && draft.splitAmount != null) {
                    require(rates.hasRate(draft.splitAmount.currency)) { "Нет курса для ${draft.splitAmount.currency.code}" }
                    transactionRepository.add(
                        transaction.copy(
                            id = "",
                            accountId = draft.splitAccountId,
                            amount = draft.splitAmount,
                            amountBase = rates.toBase(draft.splitAmount),
                            rateSource = rateSourceFor(draft.splitAmount),
                            debtId = null,
                            debtPrincipal = null,
                            debtEarly = false,
                        ),
                        current.accounts,
                    )
                } else {
                    null
                }
                draft.fromDraftId?.let(draftRepository::remove)
                _state.update {
                    it.copy(
                        saving = false,
                        transactions = (listOfNotNull(second, saved) + it.transactions).distinctBy { tx -> tx.id },
                        pendingCount = transactionRepository.pendingCount(),
                        drafts = draftRepository.all(),
                        entryPrefill = null,
                    )
                }
                val debtNote = debt?.let { debtNoteAfter(it.id, principal?.let { p -> -p }) }
                _messages.send(
                    when {
                        saved.pending -> "Записано без сети, отправится при обновлении"
                        second != null -> "Записано двумя операциями: ${MoneyFormat.format(draft.amount, false)} + ${MoneyFormat.format(draft.splitAmount ?: draft.amount, false)}"
                        draft.type == EntryType.EXPENSE -> "Расход записан" + debtNote.orEmpty()
                        draft.type == EntryType.INCOME -> "Доход записан"
                        else -> "Перевод записан"
                    },
                )
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(e.message ?: "Не удалось сохранить")
            }
        }
    }

    fun addExchange(draft: ExchangeDraft) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val current = _state.value
                require(current.rates.hasRate(draft.given.currency)) { "Нет курса для ${draft.given.currency.code}, заполни таблицу курсов" }
                val transaction = Transaction.Exchange(
                    id = "",
                    date = draft.date,
                    fromAccountId = draft.fromAccountId,
                    toAccountId = draft.toAccountId,
                    given = draft.given,
                    received = draft.received,
                    amountBase = current.rates.toBase(draft.given),
                    note = draft.note,
                )
                val saved = transactionRepository.add(transaction, current.accounts)
                _state.update { it.copy(saving = false, transactions = (listOf(saved) + it.transactions).distinctBy { tx -> tx.id }, pendingCount = transactionRepository.pendingCount()) }
                _messages.send(if (saved.pending) "Записано без сети, отправится при обновлении" else "Обмен записан")
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(e.message ?: "Не удалось сохранить обмен")
            }
        }
    }

    fun repeatTransaction(source: Transaction) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val current = _state.value
                val rates = current.rates
                val copy = when (source) {
                    is Transaction.Expense -> {
                        require(source.debtId == null) { "Платёж по кредиту повторяй через форму записи" }
                        require(rates.hasRate(source.amount.currency)) { "Нет курса для ${source.amount.currency.code}" }
                        source.copy(id = "", date = today, amountBase = rates.toBase(source.amount), debtPrincipal = null, debtEarly = false, pending = false)
                    }
                    is Transaction.Income -> {
                        require(rates.hasRate(source.amount.currency)) { "Нет курса для ${source.amount.currency.code}" }
                        source.copy(id = "", date = today, amountBase = rates.toBase(source.amount), pending = false)
                    }
                    is Transaction.Transfer -> source.copy(id = "", date = today, amountBase = rates.toBase(source.amount), pending = false)
                    is Transaction.Exchange -> source.copy(id = "", date = today, amountBase = rates.toBase(source.given), pending = false)
                }
                val saved = transactionRepository.add(copy, current.accounts)
                _state.update { it.copy(saving = false, transactions = (listOf(saved) + it.transactions).distinctBy { tx -> tx.id }, pendingCount = transactionRepository.pendingCount()) }
                _messages.send("Операция повторена на сегодня")
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(e.message ?: "Не удалось повторить")
            }
        }
    }

    fun updateTransaction(updated: Transaction, subcategoryName: String?) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(saving = true) }
                val current = _state.value
                val original = current.transactions.firstOrNull { it.id == updated.id }
                require(original != null) { "Операция не найдена" }
                val withBase = withRecalculatedBase(original, updated)
                val resolved = when (withBase) {
                    is Transaction.Expense -> withDebtPrincipal(
                        original as? Transaction.Expense,
                        withBase.copy(subcategoryId = subcategoryName?.let { resolveSubcategory(withBase.categoryId, it) }),
                    )
                    is Transaction.Income, is Transaction.Transfer, is Transaction.Exchange -> withBase
                }
                val saved = transactionRepository.update(resolved, current.accounts)
                _state.update { state -> state.copy(saving = false, transactions = state.transactions.map { if (it.id == saved.id) saved else it }) }
                val debtNote = (saved as? Transaction.Expense)?.debtId?.let { debtId ->
                    val oldPrincipal = (original as? Transaction.Expense)?.takeIf { it.debtId == debtId }?.debtPrincipal
                    val newPrincipal = saved.debtPrincipal
                    val currency = (newPrincipal ?: oldPrincipal)?.currency
                    val delta = currency?.let { (oldPrincipal ?: Money.zero(it)) - (newPrincipal ?: Money.zero(it)) }
                    debtNoteAfter(debtId, delta)
                }
                _messages.send("Операция обновлена" + debtNote.orEmpty())
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(e.message ?: "Не удалось обновить операцию")
            }
        }
    }

    private fun withRecalculatedBase(original: Transaction, updated: Transaction): Transaction {
        val rates = _state.value.rates
        fun base(oldAmount: Money, oldBase: Money, newAmount: Money): Money =
            if (newAmount == oldAmount) oldBase else rates.toBase(newAmount)
        return when (updated) {
            is Transaction.Expense -> {
                val old = original as? Transaction.Expense
                updated.copy(amountBase = if (old != null) base(old.amount, old.amountBase, updated.amount) else rates.toBase(updated.amount))
            }
            is Transaction.Income -> {
                val old = original as? Transaction.Income
                updated.copy(amountBase = if (old != null) base(old.amount, old.amountBase, updated.amount) else rates.toBase(updated.amount))
            }
            is Transaction.Transfer -> {
                val old = original as? Transaction.Transfer
                updated.copy(amountBase = if (old != null) base(old.amount, old.amountBase, updated.amount) else rates.toBase(updated.amount))
            }
            is Transaction.Exchange -> {
                val old = original as? Transaction.Exchange
                updated.copy(amountBase = if (old != null) base(old.given, old.amountBase, updated.given) else rates.toBase(updated.given))
            }
        }
    }

    fun deleteTransaction(id: String) {
        val transactions = _state.value.transactions
        val index = transactions.indexOfFirst { it.id == id }
        if (index < 0) return
        val transaction = transactions[index]
        pendingDeletes[id] = index to transaction
        _state.update { state -> state.copy(transactions = state.transactions.filterNot { it.id == id }) }
        viewModelScope.launch { _undoRequests.send(UndoRequest(id, "Удалено: ${undoLabel(transaction)}")) }
        deleteTimers.remove(id)?.cancel()
        deleteTimers[id] = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            commitDelete(id)
        }
    }

    fun undoDelete(id: String) {
        deleteTimers.remove(id)?.cancel()
        val (index, transaction) = pendingDeletes.remove(id) ?: return
        _state.update { state ->
            val list = state.transactions.toMutableList()
            list.add(index.coerceIn(0, list.size), transaction)
            state.copy(transactions = list)
        }
    }

    private fun commitDelete(id: String) {
        deleteTimers.remove(id)
        val (_, transaction) = pendingDeletes.remove(id) ?: return
        viewModelScope.launch {
            try {
                val removed = transaction as? Transaction.Expense
                transactionRepository.delete(id)
                _state.update { state -> state.copy(transactions = state.transactions.filterNot { it.id == id }, pendingCount = transactionRepository.pendingCount()) }
                val debtNote = removed?.debtId?.let { debtId ->
                    removed.debtPrincipal?.let { debtNoteAfter(debtId, it, restored = true) } ?: ", остаток кредита не менялся"
                }
                debtNote?.let { _messages.send("Удалено" + it) }
            } catch (e: Exception) {
                _state.update { state -> state.copy(transactions = (listOf(transaction) + state.transactions).distinctBy { tx -> tx.id }) }
                _messages.send(e.message ?: "Не удалось удалить")
            }
        }
    }

    fun setPlanned(categoryId: String, planned: Money) = setItemPlanned(categoryId, null, planned)

    fun setSavingsTarget(target: Money?) {
        viewModelScope.launch {
            try {
                val periodId = _state.value.periodId ?: periodRepository.ensure(_state.value.period)
                val value = target?.takeIf { it.minor > 0L }
                budgetRepository.setSavingsTarget(periodId, value)
                _state.update { it.copy(periodId = periodId, savingsTarget = value) }
                _messages.send(if (value == null) "План «Себе» убран" else "План «Себе»: ${MoneyFormat.format(value, false)}")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось сохранить план «Себе»")
            }
        }
    }

    fun setItemPlanned(categoryId: String, subcategoryId: String?, planned: Money) {
        viewModelScope.launch {
            try {
                val periodId = _state.value.periodId ?: periodRepository.ensure(_state.value.period)
                val line = BudgetLine(categoryId, planned, subcategoryId)
                budgetRepository.setPlanned(periodId, line)
                _state.update { state ->
                    state.copy(
                        periodId = periodId,
                        budgetLines = state.budgetLines.filterNot { it.categoryId == categoryId && it.subcategoryId == subcategoryId } + line,
                    )
                }
                _messages.send("План обновлён")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось сохранить план")
            }
        }
    }

    fun saveCategoryPlan(draft: CategoryPlanDraft) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(saving = true) }
                val current = _state.value
                val periodId = current.periodId ?: periodRepository.ensure(current.period)
                val categoryId = draft.categoryId
                val limitLine = draft.free?.takeIf { it.minor > 0L }?.let { BudgetLine(categoryId, it, null) }
                val hadLimit = current.budgetLines.any { it.categoryId == categoryId && it.subcategoryId == null }
                when {
                    limitLine != null -> budgetRepository.setPlanned(periodId, limitLine)
                    hadLimit -> budgetRepository.deleteLine(periodId, categoryId, null)
                }
                draft.removedSubcategoryIds.forEach { budgetRepository.deleteLine(periodId, categoryId, it) }
                val itemLines = draft.items.map { item ->
                    val existing = item.subcategoryId?.let { id -> current.subcategoriesById[id] }
                    val subcategoryId = if (existing != null) {
                        if (!existing.name.equals(item.name.trim(), ignoreCase = false) && item.name.isNotBlank()) {
                            categoryRepository.renameSubcategory(existing.id, item.name.trim())
                            _state.update { state -> state.copy(subcategories = state.subcategories.map { if (it.id == existing.id) it.copy(name = item.name.trim()) else it }) }
                        }
                        existing.id
                    } else {
                        requireNotNull(resolveSubcategory(categoryId, item.name)) { "Введи название подкатегории" }
                    }
                    BudgetLine(categoryId, item.planned, subcategoryId)
                }
                itemLines.forEach { budgetRepository.setPlanned(periodId, it) }
                val touched: Set<String?> = setOf<String?>(null) + draft.removedSubcategoryIds + itemLines.map { it.subcategoryId }
                _state.update { state ->
                    state.copy(
                        saving = false,
                        periodId = periodId,
                        budgetLines = state.budgetLines.filterNot { it.categoryId == categoryId && it.subcategoryId in touched } +
                            listOfNotNull(limitLine) + itemLines,
                    )
                }
                _messages.send("План «${current.categoryName(categoryId)}» сохранён")
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(e.message ?: "Не удалось сохранить план")
            }
        }
    }

    fun setPlannedForSubcategoryName(categoryId: String, subcategoryName: String, planned: Money) {
        viewModelScope.launch {
            try {
                val subcategoryId = resolveSubcategory(categoryId, subcategoryName)
                require(subcategoryId != null) { "Введи название подкатегории" }
                setItemPlanned(categoryId, subcategoryId, planned)
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось сохранить план")
            }
        }
    }

    fun removePlanLine(categoryId: String, subcategoryId: String?) {
        viewModelScope.launch {
            try {
                val periodId = _state.value.periodId ?: periodRepository.ensure(_state.value.period)
                budgetRepository.deleteLine(periodId, categoryId, subcategoryId)
                _state.update { state ->
                    state.copy(
                        periodId = periodId,
                        budgetLines = state.budgetLines.filterNot { it.categoryId == categoryId && it.subcategoryId == subcategoryId },
                    )
                }
                _messages.send("Строка плана удалена")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось удалить строку")
            }
        }
    }

    fun copyPlanFromPreviousPeriod() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(saving = true) }
                val current = _state.value
                val periodId = current.periodId ?: periodRepository.ensure(current.period)
                val previousId = periodRepository.ensure(SalaryCycle.previous(current.period))
                val previousLines = budgetRepository.getLines(previousId).filter { it.planned.minor > 0L }
                val previousTarget = budgetRepository.getSavingsTarget(previousId)
                require(previousLines.isNotEmpty() || previousTarget != null) { "В прошлом месяце план не заполнен" }
                previousTarget?.let { budgetRepository.setSavingsTarget(periodId, it) }
                val activeIds = current.activeCategories.map { it.id }.toSet()
                val toCopy = previousLines.filter { it.categoryId in activeIds }
                toCopy.forEach { budgetRepository.setPlanned(periodId, it) }
                _state.update { state ->
                    val copiedKeys = toCopy.map { it.categoryId to it.subcategoryId }.toSet()
                    state.copy(
                        saving = false,
                        periodId = periodId,
                        budgetLines = state.budgetLines.filterNot { (it.categoryId to it.subcategoryId) in copiedKeys } + toCopy,
                        savingsTarget = previousTarget ?: state.savingsTarget,
                    )
                }
                _messages.send("План скопирован: ${toCopy.size} категорий")
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(e.message ?: "Не удалось скопировать план")
            }
        }
    }

    fun addCategory(name: String, kind: CategoryKind) {
        viewModelScope.launch {
            try {
                val trimmed = name.trim()
                require(trimmed.isNotEmpty()) { "Введи название" }
                val exists = _state.value.activeCategories.any { it.kind == kind && it.name.equals(trimmed, ignoreCase = true) }
                require(!exists) { "Такая категория уже есть" }
                createCategory(trimmed, kind)
                _messages.send("Категория добавлена")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось добавить категорию")
            }
        }
    }

    fun renameCategory(id: String, name: String) {
        viewModelScope.launch {
            try {
                val trimmed = name.trim()
                require(trimmed.isNotEmpty()) { "Введи название" }
                categoryRepository.renameCategory(id, trimmed)
                _state.update { state ->
                    state.copy(categories = state.categories.map { if (it.id == id) it.copy(name = trimmed) else it })
                }
                _messages.send("Переименовано")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось переименовать")
            }
        }
    }

    fun archiveCategory(id: String) {
        viewModelScope.launch {
            try {
                categoryRepository.archiveCategory(id)
                _state.update { state ->
                    state.copy(categories = state.categories.map { if (it.id == id) it.copy(isArchived = true) else it })
                }
                _messages.send("Категория убрана в архив")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось архивировать")
            }
        }
    }

    fun addSubcategory(categoryId: String, name: String) {
        viewModelScope.launch {
            try {
                val id = resolveSubcategory(categoryId, name)
                require(id != null) { "Введи название" }
                _messages.send("Подкатегория добавлена")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось добавить подкатегорию")
            }
        }
    }

    fun renameSubcategory(id: String, name: String) {
        viewModelScope.launch {
            try {
                val trimmed = name.trim()
                require(trimmed.isNotEmpty()) { "Введи название" }
                categoryRepository.renameSubcategory(id, trimmed)
                _state.update { state ->
                    state.copy(subcategories = state.subcategories.map { if (it.id == id) it.copy(name = trimmed) else it })
                }
                _messages.send("Переименовано")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось переименовать")
            }
        }
    }

    fun deleteSubcategory(id: String) {
        viewModelScope.launch {
            try {
                val current = _state.value
                val used = current.transactions.any { it is Transaction.Expense && it.subcategoryId == id } ||
                    current.allPlanLines.any { it.line.subcategoryId == id }
                if (used) {
                    categoryRepository.archiveSubcategory(id, true)
                    _state.update { state -> state.copy(subcategories = state.subcategories.map { if (it.id == id) it.copy(isArchived = true) else it }) }
                    _messages.send("Подкатегория убрана в архив, история сохранена")
                } else {
                    categoryRepository.deleteSubcategory(id)
                    _state.update { state -> state.copy(subcategories = state.subcategories.filterNot { it.id == id }) }
                    _messages.send("Подкатегория удалена")
                }
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось удалить")
            }
        }
    }

    fun restoreSubcategory(id: String) {
        viewModelScope.launch {
            try {
                categoryRepository.archiveSubcategory(id, false)
                _state.update { state -> state.copy(subcategories = state.subcategories.map { if (it.id == id) it.copy(isArchived = false) else it }) }
                _messages.send("Подкатегория возвращена")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось вернуть")
            }
        }
    }

    fun mergeSubcategory(fromId: String, toId: String) {
        viewModelScope.launch {
            try {
                require(fromId != toId) { "Выбери другую подкатегорию" }
                _state.update { it.copy(saving = true) }
                categoryRepository.mergeSubcategory(fromId, toId)
                _state.update { it.copy(saving = false) }
                _messages.send("Подкатегории объединены")
                reload(initial = false)
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(e.message ?: "Не удалось объединить")
            }
        }
    }

    fun setCategorySystem(id: String, isSystem: Boolean) {
        viewModelScope.launch {
            try {
                categoryRepository.setSystem(id, isSystem)
                _state.update { state -> state.copy(categories = state.categories.map { if (it.id == id) it.copy(isSystem = isSystem) else it }) }
                _messages.send(if (isSystem) "Категория стала служебной: в бюджете не учитывается" else "Категория снова учитывается в бюджете")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось изменить категорию")
            }
        }
    }

    fun moveCategory(id: String, up: Boolean) {
        viewModelScope.launch {
            try {
                val current = _state.value
                val target = current.categoriesById[id] ?: return@launch
                val group = current.activeCategories.filter { it.kind == target.kind }.sortedBy { it.sortOrder }
                val index = group.indexOfFirst { it.id == id }
                val neighbourIndex = if (up) index - 1 else index + 1
                val neighbour = group.getOrNull(neighbourIndex) ?: return@launch
                val reordered = group.toMutableList().apply {
                    this[index] = neighbour
                    this[neighbourIndex] = target
                }
                val orders = reordered.mapIndexed { position, category -> category.id to (position + 1) * SORT_STEP }.toMap()
                orders.forEach { (categoryId, order) -> categoryRepository.setCategorySortOrder(categoryId, order) }
                _state.update { state -> state.copy(categories = state.categories.map { c -> orders[c.id]?.let { c.copy(sortOrder = it) } ?: c }) }
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось переставить категорию")
            }
        }
    }

    fun checkBalance(accountId: String, actual: Money, note: String, date: LocalDate = today) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(saving = true) }
                val current = _state.value
                val account = requireNotNull(current.accountsById[accountId]) { "Счёт не найден" }
                require(date <= today) { "Сверку нельзя провести будущей датой" }
                val computed = BalanceCalculator.balanceBefore(account, current.transactions, date.plus(1, DateTimeUnit.DAY))
                require(actual.currency == account.currency) { "Валюта не совпадает со счётом" }
                val check = balanceCheckRepository.add(
                    BalanceCheck(id = "", accountId = accountId, date = date, actual = actual, computed = computed, note = note),
                )
                val difference = actual - computed
                val correction = if (difference.isZero) null else createCorrection(account.id, difference, computed, actual, date)
                _state.update { state ->
                    state.copy(
                        saving = false,
                        balanceChecks = listOf(check) + state.balanceChecks,
                        transactions = (listOfNotNull(correction) + state.transactions).distinctBy { tx -> tx.id },
                    )
                }
                _messages.send(
                    if (difference.isZero) "Сверка записана, остаток сходится"
                    else "Сверка записана, корректировка ${MoneyFormat.formatSigned(difference)}",
                )
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(e.message ?: "Не удалось записать сверку")
            }
        }
    }

    fun addAccount(name: String, currency: Currency, role: AccountRole) {
        viewModelScope.launch {
            try {
                val trimmed = name.trim()
                require(trimmed.isNotEmpty()) { "Введи название счёта" }
                require(_state.value.activeAccounts.none { it.name.equals(trimmed, ignoreCase = true) }) { "Счёт с таким именем уже есть" }
                val order = (_state.value.accounts.maxOfOrNull { it.sortOrder } ?: 0) + SORT_STEP
                val isSavings = role == AccountRole.CUSHION
                val kind = if (isSavings) AccountKind.SAVINGS else AccountKind.ACCOUNT
                val created = accountRepository.add(trimmed, currency, kind, isSavings, role == AccountRole.FUND, order)
                _state.update { it.copy(accounts = it.accounts + created) }
                _messages.send("Счёт добавлен")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось добавить счёт")
            }
        }
    }

    fun renameAccount(id: String, name: String) {
        viewModelScope.launch {
            try {
                val trimmed = name.trim()
                require(trimmed.isNotEmpty()) { "Введи название счёта" }
                accountRepository.rename(id, trimmed)
                _state.update { state -> state.copy(accounts = state.accounts.map { if (it.id == id) it.copy(name = trimmed) else it }) }
                _messages.send("Счёт переименован")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось переименовать счёт")
            }
        }
    }

    fun setAccountRole(id: String, role: AccountRole) {
        viewModelScope.launch {
            try {
                val isSavings = role == AccountRole.CUSHION
                val isFund = role == AccountRole.FUND
                accountRepository.setRole(id, isSavings, isFund)
                _state.update { state -> state.copy(accounts = state.accounts.map { if (it.id == id) it.copy(isSavings = isSavings, isFund = isFund) else it }) }
                _messages.send(
                    when (role) {
                        AccountRole.LIFE -> "Счёт теперь на жизнь"
                        AccountRole.FUND -> "Счёт теперь копилка"
                        AccountRole.CUSHION -> "Счёт теперь подушка"
                    },
                )
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось изменить счёт")
            }
        }
    }

    fun setFund(id: String, monthly: Money?, goal: Money?, day: Int?) {
        viewModelScope.launch {
            try {
                require(day == null || day in 1..31) { "День от 1 до 31" }
                val m = monthly?.takeIf { it.minor > 0L }
                val g = goal?.takeIf { it.minor > 0L }
                accountRepository.setFund(id, m, g, day)
                _state.update { state -> state.copy(accounts = state.accounts.map { if (it.id == id) it.copy(fundMonthly = m, fundGoal = g, fundDay = day) else it }) }
                _messages.send("Копилка обновлена")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось сохранить копилку")
            }
        }
    }

    fun payDebt(debtId: String) {
        viewModelScope.launch {
            val current = _state.value
            val debt = current.debts.firstOrNull { it.id == debtId } ?: return@launch
            val lastPayment = current.transactions.filterIsInstance<Transaction.Expense>().filter { it.debtId == debtId }.maxByOrNull { it.date }
            val categoryId = lastPayment?.categoryId
                ?: current.activeCategories.firstOrNull { it.kind == CategoryKind.EXPENSE && it.name.equals(DEBT_CATEGORY, ignoreCase = true) }?.id
            _state.update {
                it.copy(
                    entryPrefill = EntryPrefill(
                        draftId = null,
                        amountText = MoneyFormat.format(debt.plannedPayment, true).filter { c -> c.isDigit() || c == ',' },
                        note = "",
                        categoryId = categoryId,
                        subcategoryName = lastPayment?.subcategoryId?.let(current::subcategoryName) ?: debt.name,
                        accountId = lastPayment?.accountId?.takeIf { id -> current.accountsById[id]?.isArchived == false },
                        debtId = debtId,
                        date = today,
                    ),
                )
            }
            _navigateToEntry.send(Unit)
        }
    }

    fun prefillMissedExpense(accountId: String, date: LocalDate) {
        viewModelScope.launch {
            _state.update { it.copy(entryPrefill = EntryPrefill(draftId = null, amountText = null, note = "", accountId = accountId, date = date)) }
            _navigateToEntry.send(Unit)
        }
    }

    fun moveAccount(id: String, up: Boolean) {
        viewModelScope.launch {
            try {
                val current = _state.value
                val target = current.accountsById[id] ?: return@launch
                val group = current.activeAccounts.filter { it.role == target.role }.sortedBy { it.sortOrder }
                val index = group.indexOfFirst { it.id == id }
                val neighbourIndex = if (up) index - 1 else index + 1
                val neighbour = group.getOrNull(neighbourIndex) ?: return@launch
                val reordered = group.toMutableList().apply {
                    this[index] = neighbour
                    this[neighbourIndex] = target
                }
                val orders = reordered.mapIndexed { position, account -> account.id to (position + 1) * SORT_STEP }.toMap()
                orders.forEach { (accountId, order) -> accountRepository.setSortOrder(accountId, order) }
                _state.update { state ->
                    state.copy(
                        accounts = state.accounts
                            .map { account -> orders[account.id]?.let { account.copy(sortOrder = it) } ?: account }
                            .sortedBy { it.sortOrder },
                    )
                }
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось переставить счёт")
            }
        }
    }

    fun archiveAccount(id: String) {
        viewModelScope.launch {
            try {
                accountRepository.archive(id)
                _state.update { state -> state.copy(accounts = state.accounts.map { if (it.id == id) it.copy(isArchived = true) else it }) }
                _messages.send("Счёт убран в архив")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось архивировать счёт")
            }
        }
    }

    fun addSubcategoryWithPlan(categoryId: String, name: String, planned: Money?) {
        viewModelScope.launch {
            try {
                val id = resolveSubcategory(categoryId, name)
                require(id != null) { "Введи название" }
                if (planned != null && planned.minor > 0L) setItemPlanned(categoryId, id, planned) else _messages.send("Подкатегория добавлена")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось добавить подкатегорию")
            }
        }
    }

    fun saveDebt(debt: Debt) {
        viewModelScope.launch {
            try {
                require(debt.name.isNotBlank()) { "Введи название кредита" }
                if (debt.id.isEmpty()) {
                    val created = debtRepository.add(debt)
                    _state.update { it.copy(debts = it.debts + created) }
                    _messages.send("Кредит добавлен")
                } else {
                    val current = _state.value.debts.firstOrNull { it.id == debt.id }
                    val principalChanged = current == null || current.principalRemaining != debt.principalRemaining
                    debtRepository.update(debt, principalChanged)
                    _state.update { state ->
                        state.copy(
                            debts = state.debts.map { existing ->
                                when {
                                    existing.id != debt.id -> existing
                                    principalChanged -> debt
                                    else -> debt.copy(principalRemaining = existing.principalRemaining)
                                }
                            },
                        )
                    }
                    _messages.send("Кредит обновлён")
                }
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось сохранить кредит")
            }
        }
    }

    fun closeDebt(id: String) {
        viewModelScope.launch {
            try {
                debtRepository.close(id)
                _state.update { state -> state.copy(debts = state.debts.map { if (it.id == id) it.copy(isClosed = true) else it }) }
                _messages.send("Кредит закрыт")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось закрыть кредит")
            }
        }
    }

    private fun withDebtPrincipal(original: Transaction.Expense?, updated: Transaction.Expense): Transaction.Expense {
        val debtId = updated.debtId ?: return updated.copy(debtPrincipal = null, debtEarly = false)
        val debt = _state.value.debts.firstOrNull { it.id == debtId } ?: return updated
        val restoredPrincipal = original?.takeIf { it.debtId == debtId }?.debtPrincipal
        val before = restoredPrincipal?.takeIf { it.currency == debt.currency }?.let { debt.principalRemaining + it } ?: debt.principalRemaining
        val principal = DebtCalculator.principalFor(debt.copy(principalRemaining = before), updated.amount, updated.debtEarly)
        return updated.copy(debtPrincipal = principal)
    }

    private fun debtNoteAfter(debtId: String, delta: Money?, restored: Boolean = false): String {
        val debt = _state.value.debts.firstOrNull { it.id == debtId } ?: return ""
        if (delta == null) return ", остаток кредита не менял: другая валюта"
        if (delta.currency != debt.currency) return ""
        val updated = debt.copy(principalRemaining = debt.principalRemaining + delta)
        _state.update { state -> state.copy(debts = state.debts.map { if (it.id == debtId) updated else it }) }
        val remaining = MoneyFormat.format(updated.principalRemaining, false)
        return when {
            restored -> ", остаток кредита восстановлен: $remaining"
            delta.isNegative -> ", в погашение тела ${MoneyFormat.format(-delta, false)}, остаток $remaining"
            delta.isZero -> ", остаток кредита $remaining"
            else -> ", остаток кредита $remaining"
        }
    }

    fun addCapitalGoal(name: String, amount: Money, targetDate: LocalDate) {
        viewModelScope.launch {
            try {
                require(name.isNotBlank()) { "Введи название цели" }
                require(amount.minor > 0L) { "Сумма цели должна быть больше нуля" }
                require(targetDate > today) { "Дата цели должна быть в будущем" }
                val goal = capitalRepository.addGoal(name.trim(), amount, targetDate)
                _state.update { it.copy(capitalGoals = (it.capitalGoals + goal).sortedBy { g -> g.targetDate }) }
                _messages.send("Цель добавлена")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось добавить цель")
            }
        }
    }

    fun deleteCapitalGoal(id: String) {
        viewModelScope.launch {
            try {
                capitalRepository.deleteGoal(id)
                _state.update { state -> state.copy(capitalGoals = state.capitalGoals.filterNot { it.id == id }) }
                _messages.send("Цель удалена")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось удалить цель")
            }
        }
    }

    fun saveCapitalSettings(settings: ru.nomadbudget.domain.logic.CapitalSettings) {
        viewModelScope.launch {
            try {
                require(settings.cushionMonths in 1..36) { "Месяцев подушки от 1 до 36" }
                require(settings.annualReturn in 0.0..100.0) { "Доходность от 0 до 100 %" }
                capitalRepository.saveSettings(settings)
                _state.update { it.copy(capitalSettings = settings) }
                _messages.send("Настройки капитала сохранены")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось сохранить настройки")
            }
        }
    }

    fun reloadDrafts() {
        _state.update { it.copy(drafts = draftRepository.all()) }
    }

    fun addDraft(text: String) {
        viewModelScope.launch {
            try {
                require(text.isNotBlank()) { "Введи текст заметки" }
                draftRepository.add(text)
                reloadDrafts()
                _messages.send("Заметка сохранена во Входящие")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось сохранить заметку")
            }
        }
    }

    fun removeDraft(id: String) {
        draftRepository.remove(id)
        reloadDrafts()
    }

    fun useDraft(draft: Draft) {
        viewModelScope.launch {
            _state.update {
                it.copy(entryPrefill = EntryPrefill(draft.id, DraftParser.amountText(draft.text), DraftParser.noteWithoutAmount(draft.text)))
            }
            _navigateToEntry.send(Unit)
        }
    }

    fun clearPrefill() {
        _state.update { it.copy(entryPrefill = null) }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                auth.signOut()
                val theme = localStore.get(ThemeSettings.KEY)
                localStore.clear()
                theme?.let { localStore.put(ThemeSettings.KEY, it) }
                _state.value = HomeState(today = today, period = SalaryCycle.periodContaining(today))
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось выйти")
            }
        }
    }

    private suspend fun createCorrection(accountId: String, difference: Money, computed: Money, actual: Money, date: LocalDate): Transaction {
        val current = _state.value
        require(current.rates.hasRate(difference.currency)) { "Нет курса для ${difference.currency.code}, корректировку не записать" }
        val kind = if (difference.isNegative) CategoryKind.EXPENSE else CategoryKind.INCOME
        val category = ensureCategory(CorrectionCategory.NAME, kind)
        val amount = if (difference.isNegative) -difference else difference
        val note = "Сверка: расчёт ${MoneyFormat.format(computed)}, факт ${MoneyFormat.format(actual)}"
        val transaction = when (kind) {
            CategoryKind.EXPENSE -> Transaction.Expense(
                id = "", date = date, accountId = accountId, amount = amount, categoryId = category.id, subcategoryId = null,
                amountBase = current.rates.toBase(amount), rateSource = rateSourceFor(amount), note = note,
            )
            CategoryKind.INCOME -> Transaction.Income(
                id = "", date = date, accountId = accountId, amount = amount, categoryId = category.id,
                amountBase = current.rates.toBase(amount), rateSource = rateSourceFor(amount), note = note,
            )
        }
        return transactionRepository.add(transaction, current.accounts)
    }

    private suspend fun ensureCategory(name: String, kind: CategoryKind): Category {
        val existing = _state.value.categories.firstOrNull { it.kind == kind && it.name.equals(name, ignoreCase = true) && !it.isArchived }
        if (existing != null && !existing.isSystem) {
            categoryRepository.setSystem(existing.id, true)
            val system = existing.copy(isSystem = true)
            _state.update { state -> state.copy(categories = state.categories.map { if (it.id == system.id) system else it }) }
            return system
        }
        return existing ?: createCategory(name, kind, sortOrder = CORRECTION_SORT_ORDER, isSystem = true)
    }

    private suspend fun createCategory(name: String, kind: CategoryKind, sortOrder: Int? = null, isSystem: Boolean = false): Category {
        val order = sortOrder ?: ((_state.value.categories.filter { it.kind == kind }.maxOfOrNull { it.sortOrder } ?: 0) + SORT_STEP)
        val created = categoryRepository.addCategory(name, kind, order, isSystem)
        _state.update { it.copy(categories = it.categories + created) }
        return created
    }

    private suspend fun resolveSubcategory(categoryId: String, name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val existing = _state.value.subcategories.firstOrNull {
            it.categoryId == categoryId && it.name.equals(trimmed, ignoreCase = true)
        }
        if (existing != null) {
            if (existing.isArchived) {
                categoryRepository.archiveSubcategory(existing.id, false)
                _state.update { state -> state.copy(subcategories = state.subcategories.map { if (it.id == existing.id) it.copy(isArchived = false) else it }) }
            }
            return existing.id
        }
        val created = categoryRepository.addSubcategory(categoryId, trimmed)
        _state.update { it.copy(subcategories = it.subcategories + created) }
        return created.id
    }

    private fun rateSourceFor(amount: Money): RateSource =
        _state.value.rates.rateFor(amount.currency)?.source ?: RateSource.API

    private companion object {
        const val SORT_STEP = 10
        const val CORRECTION_SORT_ORDER = 900
        const val DEBT_CATEGORY = "Долг"
    }

    private fun undoLabel(transaction: Transaction): String = when (transaction) {
        is Transaction.Expense -> "${_state.value.categoryName(transaction.categoryId)}, ${MoneyFormat.format(transaction.amount, false)}"
        is Transaction.Income -> "${_state.value.categoryName(transaction.categoryId)}, ${MoneyFormat.format(transaction.amount, false)}"
        is Transaction.Transfer -> "перевод ${MoneyFormat.format(transaction.amount, false)}"
        is Transaction.Exchange -> "обмен ${MoneyFormat.format(transaction.given, false)}"
    }
}

data class UndoRequest(val id: String, val text: String)

private const val UNDO_WINDOW_MS = 10_000L

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
    val periodId: String,
    val lines: List<BudgetLine>,
    val savingsTarget: Money?,
    val allPlanLines: List<PeriodBudgetLine>,
    val capitalGoals: List<CapitalGoal>,
    val capitalSettings: CapitalSettings,
)
