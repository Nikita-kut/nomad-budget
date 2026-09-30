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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.CorrectionCategory
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
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.TransactionRepository
import ru.nomadbudget.presentation.format.MoneyFormat
import kotlin.time.Clock

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
) : ViewModel() {

    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val _state = MutableStateFlow(HomeState(today = today, period = SalaryCycle.periodContaining(today)))
    val state: StateFlow<HomeState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(loading = true, error = null) }
                val currencies = currencyRepository.getAll()
                val accounts = accountRepository.getAll()
                val categories = categoryRepository.getCategories()
                val subcategories = categoryRepository.getSubcategories()
                val transactions = transactionRepository.getAll(accounts)
                val rates = rateRepository.ratesOnOrBefore(today)
                val checks = balanceCheckRepository.getRecent(accounts)
                val period = _state.value.period
                val periodId = periodRepository.ensure(period)
                val lines = budgetRepository.getLines(periodId)
                _state.update {
                    it.copy(
                        loading = false,
                        currencies = currencies,
                        accounts = accounts,
                        categories = categories,
                        subcategories = subcategories,
                        transactions = transactions,
                        rates = rates,
                        balanceChecks = checks,
                        periodId = periodId,
                        budgetLines = lines,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Ошибка загрузки") }
            }
        }
    }

    fun showPreviousPeriod() = switchPeriod(SalaryCycle.previous(_state.value.period))

    fun showNextPeriod() = switchPeriod(SalaryCycle.next(_state.value.period))

    fun showCurrentPeriod() = switchPeriod(SalaryCycle.periodContaining(today))

    private fun switchPeriod(period: Period) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(period = period, periodId = null, budgetLines = emptyList()) }
                val periodId = periodRepository.ensure(period)
                val lines = budgetRepository.getLines(periodId)
                _state.update { it.copy(periodId = periodId, budgetLines = lines) }
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось загрузить период")
            }
        }
    }

    fun addEntry(draft: EntryDraft) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(saving = true) }
                val current = _state.value
                val rates = current.rates
                require(rates.hasRate(draft.amount.currency)) { "Нет курса для ${draft.amount.currency.code}, заполни таблицу курсов" }
                val rateSource = rateSourceFor(draft.amount)
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
                _state.update { it.copy(saving = false, transactions = listOf(saved) + it.transactions) }
                _messages.send(
                    when (draft.type) {
                        EntryType.EXPENSE -> "Расход записан"
                        EntryType.INCOME -> "Доход записан"
                        EntryType.TRANSFER -> "Перевод записан"
                    },
                )
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(e.message ?: "Не удалось сохранить")
            }
        }
    }

    fun addExchange(draft: ExchangeDraft) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(saving = true) }
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
                _state.update { it.copy(saving = false, transactions = listOf(saved) + it.transactions) }
                _messages.send("Обмен записан")
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.send(e.message ?: "Не удалось сохранить обмен")
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            try {
                transactionRepository.delete(id)
                _state.update { state -> state.copy(transactions = state.transactions.filterNot { it.id == id }) }
                _messages.send("Удалено")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось удалить")
            }
        }
    }

    fun setPlanned(categoryId: String, planned: Money) = setItemPlanned(categoryId, null, planned)

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
                require(previousLines.isNotEmpty()) { "В прошлом месяце план не заполнен" }
                val activeIds = current.activeCategories.map { it.id }.toSet()
                val toCopy = previousLines.filter { it.categoryId in activeIds }
                toCopy.forEach { budgetRepository.setPlanned(periodId, it) }
                _state.update { state ->
                    val copiedKeys = toCopy.map { it.categoryId to it.subcategoryId }.toSet()
                    state.copy(
                        saving = false,
                        periodId = periodId,
                        budgetLines = state.budgetLines.filterNot { (it.categoryId to it.subcategoryId) in copiedKeys } + toCopy,
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
                val used = _state.value.transactions.any { it is Transaction.Expense && it.subcategoryId == id }
                require(!used) { "Подкатегория используется в операциях, удалить нельзя" }
                categoryRepository.deleteSubcategory(id)
                _state.update { state -> state.copy(subcategories = state.subcategories.filterNot { it.id == id }) }
                _messages.send("Подкатегория удалена")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось удалить")
            }
        }
    }

    fun checkBalance(accountId: String, actual: Money, note: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(saving = true) }
                val current = _state.value
                val account = requireNotNull(current.accountsById[accountId]) { "Счёт не найден" }
                val computed = current.balances.getValue(accountId)
                require(actual.currency == account.currency) { "Валюта не совпадает со счётом" }
                val check = balanceCheckRepository.add(
                    BalanceCheck(id = "", accountId = accountId, date = today, actual = actual, computed = computed, note = note),
                )
                val difference = actual - computed
                val correction = if (difference.isZero) null else createCorrection(account.id, difference, computed, actual)
                _state.update { state ->
                    state.copy(
                        saving = false,
                        balanceChecks = listOf(check) + state.balanceChecks,
                        transactions = listOfNotNull(correction) + state.transactions,
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

    fun signOut() {
        viewModelScope.launch {
            try {
                auth.signOut()
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось выйти")
            }
        }
    }

    private suspend fun createCorrection(accountId: String, difference: Money, computed: Money, actual: Money): Transaction {
        val current = _state.value
        require(current.rates.hasRate(difference.currency)) { "Нет курса для ${difference.currency.code}, корректировку не записать" }
        val kind = if (difference.isNegative) CategoryKind.EXPENSE else CategoryKind.INCOME
        val category = ensureCategory(CorrectionCategory.NAME, kind)
        val amount = if (difference.isNegative) -difference else difference
        val note = "Сверка: расчёт ${MoneyFormat.format(computed)}, факт ${MoneyFormat.format(actual)}"
        val transaction = when (kind) {
            CategoryKind.EXPENSE -> Transaction.Expense(
                id = "", date = today, accountId = accountId, amount = amount, categoryId = category.id, subcategoryId = null,
                amountBase = current.rates.toBase(amount), rateSource = rateSourceFor(amount), note = note,
            )
            CategoryKind.INCOME -> Transaction.Income(
                id = "", date = today, accountId = accountId, amount = amount, categoryId = category.id,
                amountBase = current.rates.toBase(amount), rateSource = rateSourceFor(amount), note = note,
            )
        }
        return transactionRepository.add(transaction, current.accounts)
    }

    private suspend fun ensureCategory(name: String, kind: CategoryKind): Category {
        val existing = _state.value.activeCategories.firstOrNull { it.kind == kind && it.name.equals(name, ignoreCase = true) }
        return existing ?: createCategory(name, kind, sortOrder = CORRECTION_SORT_ORDER)
    }

    private suspend fun createCategory(name: String, kind: CategoryKind, sortOrder: Int? = null): Category {
        val order = sortOrder ?: ((_state.value.categories.filter { it.kind == kind }.maxOfOrNull { it.sortOrder } ?: 0) + SORT_STEP)
        val created = categoryRepository.addCategory(name, kind, order)
        _state.update { it.copy(categories = it.categories + created) }
        return created
    }

    private suspend fun resolveSubcategory(categoryId: String, name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val existing = _state.value.subcategories.firstOrNull {
            it.categoryId == categoryId && it.name.equals(trimmed, ignoreCase = true)
        }
        if (existing != null) return existing.id
        val created = categoryRepository.addSubcategory(categoryId, trimmed)
        _state.update { it.copy(subcategories = it.subcategories + created) }
        return created.id
    }

    private fun rateSourceFor(amount: Money): RateSource =
        _state.value.rates.rateFor(amount.currency)?.source ?: RateSource.API

    private companion object {
        const val SORT_STEP = 10
        const val CORRECTION_SORT_ORDER = 900
    }
}
