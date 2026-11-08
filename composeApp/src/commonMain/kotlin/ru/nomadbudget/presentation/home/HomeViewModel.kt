package ru.nomadbudget.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import ru.nomadbudget.core.TodayProvider
import ru.nomadbudget.domain.logic.CapitalSettings
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Draft
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.AuthState
import ru.nomadbudget.domain.repository.BalanceCheckRepository
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CapitalRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.CurrencyRepository
import ru.nomadbudget.domain.repository.DebtRepository
import ru.nomadbudget.domain.repository.DraftRepository
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.KeyValueStore
import ru.nomadbudget.domain.repository.OfflineStatus
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.TransactionRepository
import ru.nomadbudget.domain.repository.UserSettingsRepository
import ru.nomadbudget.presentation.onboarding.OnboardingResult

class HomeViewModel(
    auth: AuthRepository,
    currencyRepository: CurrencyRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    periodRepository: PeriodRepository,
    transactionRepository: TransactionRepository,
    budgetRepository: BudgetRepository,
    rateRepository: ExchangeRateRepository,
    balanceCheckRepository: BalanceCheckRepository,
    debtRepository: DebtRepository,
    offlineStatus: OfflineStatus,
    draftRepository: DraftRepository,
    todayProvider: TodayProvider,
    localStore: KeyValueStore,
    capitalRepository: CapitalRepository,
    userSettingsRepository: UserSettingsRepository,
) : ViewModel() {

    private val today: LocalDate = todayProvider.today()
    private val store: HomeStore
    private val runner: ActionRunner
    private val loader: LoadCoordinator
    private val categories: CategoryActions
    private val transactions: TransactionActions
    private val plans: PlanActions
    private val accounts: AccountActions
    private val inbox: InboxActions
    private val debtsActions: DebtActions
    private val settings: SettingsActions

    private var activeUserId: String? = null

    val state: StateFlow<HomeState>
    val messages: Flow<String>
    val undoRequests: Flow<UndoRequest>
    val navigateToEntry: Flow<Unit>

    init {
        store = HomeStore(LoadCoordinator.initialState(userSettingsRepository, today))
        runner = ActionRunner(viewModelScope, store)
        categories = CategoryActions(store, runner, categoryRepository, onMerged = { loader.refresh() })
        transactions = TransactionActions(store, runner, viewModelScope, transactionRepository, balanceCheckRepository, draftRepository, categories, today)
        loader = LoadCoordinator(
            store = store,
            runner = runner,
            scope = viewModelScope,
            auth = auth,
            currencyRepository = currencyRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            periodRepository = periodRepository,
            transactionRepository = transactionRepository,
            budgetRepository = budgetRepository,
            rateRepository = rateRepository,
            balanceCheckRepository = balanceCheckRepository,
            debtRepository = debtRepository,
            capitalRepository = capitalRepository,
            userSettingsRepository = userSettingsRepository,
            draftRepository = draftRepository,
            offline = offlineStatus,
            today = today,
            isPendingDelete = transactions::isPendingDelete,
        )
        plans = PlanActions(store, runner, periodRepository, budgetRepository, categoryRepository, categories)
        accounts = AccountActions(store, runner, accountRepository)
        inbox = InboxActions(store, runner, draftRepository)
        debtsActions = DebtActions(store, runner, debtRepository, inbox, today)
        settings = SettingsActions(
            store = store,
            runner = runner,
            auth = auth,
            localStore = localStore,
            userSettingsRepository = userSettingsRepository,
            capitalRepository = capitalRepository,
            currencyRepository = currencyRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            loader = loader,
            today = today,
        )
        state = store.state
        messages = store.messages
        undoRequests = transactions.undoRequests
        navigateToEntry = inbox.navigateToEntry

        viewModelScope.launch {
            auth.state.collect { authState ->
                when (authState) {
                    is AuthState.SignedIn -> if (authState.userId != activeUserId) {
                        activeUserId = authState.userId
                        resetState()
                        load()
                    }
                    is AuthState.SignedOut -> if (activeUserId != null) {
                        activeUserId = null
                        resetState()
                    }
                    AuthState.Loading -> Unit
                }
            }
        }
    }

    private fun resetState() {
        transactions.reset()
        loader.cancel()
        store.reset(loader.initialState())
    }

    fun load() = loader.load()

    fun refresh() = loader.refresh()

    fun showPreviousPeriod() = loader.showPreviousPeriod()

    fun showNextPeriod() = loader.showNextPeriod()

    fun showCurrentPeriod() = loader.showCurrentPeriod()

    fun addEntry(draft: EntryDraft) {
        transactions.addEntry(draft)
    }

    fun addExchange(draft: ExchangeDraft) {
        transactions.addExchange(draft)
    }

    fun repeatTransaction(source: Transaction) {
        transactions.repeatTransaction(source)
    }

    fun updateTransaction(updated: Transaction, subcategoryName: String?) {
        transactions.updateTransaction(updated, subcategoryName)
    }

    fun deleteTransaction(id: String) = transactions.deleteTransaction(id)

    fun undoDelete(id: String) = transactions.undoDelete(id)

    fun checkBalance(accountId: String, actual: Money, note: String, date: LocalDate = today) {
        transactions.checkBalance(accountId, actual, note, date)
    }

    fun setSavingsTarget(target: Money?) {
        plans.setSavingsTarget(target)
    }

    fun saveCategoryPlan(draft: CategoryPlanDraft) {
        plans.saveCategoryPlan(draft)
    }

    fun copyPlanFromPreviousPeriod() {
        plans.copyPlanFromPreviousPeriod()
    }

    fun addSubcategoryWithPlan(categoryId: String, name: String, planned: Money?) {
        plans.addSubcategoryWithPlan(categoryId, name, planned)
    }

    fun addCategory(name: String, kind: CategoryKind) {
        categories.addCategory(name, kind)
    }

    fun renameCategory(id: String, name: String) {
        categories.renameCategory(id, name)
    }

    fun archiveCategory(id: String) {
        categories.archiveCategory(id)
    }

    fun renameSubcategory(id: String, name: String) {
        categories.renameSubcategory(id, name)
    }

    fun deleteSubcategory(id: String) {
        categories.deleteSubcategory(id)
    }

    fun restoreSubcategory(id: String) {
        categories.restoreSubcategory(id)
    }

    fun mergeSubcategory(fromId: String, toId: String) {
        categories.mergeSubcategory(fromId, toId)
    }

    fun setCategorySystem(id: String, isSystem: Boolean) {
        categories.setCategorySystem(id, isSystem)
    }

    fun moveCategory(id: String, up: Boolean) {
        categories.moveCategory(id, up)
    }

    fun addAccount(draft: NewAccountDraft) {
        accounts.addAccount(draft)
    }

    fun renameAccount(id: String, name: String) {
        accounts.renameAccount(id, name)
    }

    fun setAccountRole(id: String, role: AccountRole) {
        accounts.setAccountRole(id, role)
    }

    fun setFund(id: String, monthly: Money?, goal: Money?, day: Int?) {
        accounts.setFund(id, monthly, goal, day)
    }

    fun moveAccount(id: String, up: Boolean) {
        accounts.moveAccount(id, up)
    }

    fun archiveAccount(id: String) {
        accounts.archiveAccount(id)
    }

    fun saveDebt(debt: Debt) {
        debtsActions.saveDebt(debt)
    }

    fun closeDebt(id: String) {
        debtsActions.closeDebt(id)
    }

    fun payDebt(debtId: String) = debtsActions.payDebt(debtId)

    fun prefillMissedExpense(accountId: String, date: LocalDate) {
        inbox.prefillMissedExpense(accountId, date)
    }

    fun reloadDrafts() = inbox.reloadDrafts()

    fun addDraft(text: String) {
        inbox.addDraft(text)
    }

    fun removeDraft(id: String) = inbox.removeDraft(id)

    fun useDraft(draft: Draft) {
        inbox.useDraft(draft)
    }

    fun clearPrefill() = inbox.clearPrefill()

    fun addCapitalGoal(name: String, amount: Money, targetDate: LocalDate) {
        settings.addCapitalGoal(name, amount, targetDate)
    }

    fun deleteCapitalGoal(id: String) {
        settings.deleteCapitalGoal(id)
    }

    fun saveCapitalSettings(capital: CapitalSettings) {
        settings.saveCapitalSettings(capital)
    }

    fun setPeriodStartDay(day: Int) {
        settings.setPeriodStartDay(day)
    }

    fun setIncomeDays(days: List<Int>) {
        settings.setIncomeDays(days)
    }

    fun addCurrency(code: String) {
        settings.addCurrency(code)
    }

    fun changePassword(newPassword: String) {
        settings.changePassword(newPassword)
    }

    fun deleteAccount() {
        settings.deleteAccount()
    }

    fun signOut() {
        settings.signOut()
    }

    fun completeOnboarding(result: OnboardingResult) {
        settings.completeOnboarding(result)
    }
}
