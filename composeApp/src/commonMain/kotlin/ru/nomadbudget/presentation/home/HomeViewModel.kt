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
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.TransactionRepository
import kotlin.time.Clock

class HomeViewModel(
    private val auth: AuthRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val periodRepository: PeriodRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val rateRepository: ExchangeRateRepository,
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
                val accounts = accountRepository.getAll()
                val categories = categoryRepository.getCategories()
                val subcategories = categoryRepository.getSubcategories()
                val transactions = transactionRepository.getAll(accounts)
                val rates = rateRepository.ratesOnOrBefore(today)
                val period = _state.value.period
                val periodId = periodRepository.ensure(period)
                val lines = budgetRepository.getLines(periodId)
                _state.update {
                    it.copy(
                        loading = false,
                        accounts = accounts,
                        categories = categories,
                        subcategories = subcategories,
                        transactions = transactions,
                        rates = rates,
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

    fun setPlanned(categoryId: String, planned: Money) {
        viewModelScope.launch {
            try {
                val periodId = _state.value.periodId ?: periodRepository.ensure(_state.value.period)
                val line = BudgetLine(categoryId, planned)
                budgetRepository.setPlanned(periodId, line)
                _state.update { state ->
                    state.copy(
                        periodId = periodId,
                        budgetLines = state.budgetLines.filterNot { it.categoryId == categoryId } + line,
                    )
                }
                _messages.send("План обновлён")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Не удалось сохранить план")
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
}
