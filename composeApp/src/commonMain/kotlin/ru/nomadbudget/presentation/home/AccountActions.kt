package ru.nomadbudget.presentation.home

import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.repository.AccountRepository

internal class AccountActions(
    private val store: HomeStore,
    private val runner: ActionRunner,
    private val accountRepository: AccountRepository,
) {

    fun addAccount(draft: NewAccountDraft) = runner.run("Не удалось добавить счёт") {
        val name = draft.name
        val currency = draft.currency
        val role = draft.role
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Введи название счёта" }
        require(store.value.activeAccounts.none { it.name.equals(trimmed, ignoreCase = true) }) { "Счёт с таким именем уже есть" }
        val order = (store.value.accounts.maxOfOrNull { it.sortOrder } ?: 0) + HomeDefaults.SORT_STEP
        val isSavings = role == AccountRole.CUSHION
        val kind = if (isSavings) AccountKind.SAVINGS else AccountKind.ACCOUNT
        require(draft.fundDay == null || draft.fundDay in HomeDefaults.DAYS_OF_MONTH) { "День от 1 до 31" }
        val opening = draft.openingBalance ?: Money.zero(currency)
        var created = accountRepository.add(trimmed, currency, kind, isSavings, role == AccountRole.FUND, order, opening)
        if (role == AccountRole.FUND && (draft.fundMonthly != null || draft.fundGoal != null || draft.fundDay != null)) {
            val monthly = draft.fundMonthly?.takeIf { it.minor > 0L }
            val goal = draft.fundGoal?.takeIf { it.minor > 0L }
            accountRepository.setFund(created.id, monthly, goal, draft.fundDay)
            created = created.copy(fundMonthly = monthly, fundGoal = goal, fundDay = draft.fundDay)
        }
        store.update { it.copy(accounts = it.accounts + created) }
        store.message(
            when (role) {
                AccountRole.LIFE -> "Счёт добавлен"
                AccountRole.FUND -> "Копилка добавлена"
                AccountRole.CUSHION -> "Подушка добавлена"
            },
        )
    }

    fun renameAccount(id: String, name: String) = runner.run("Не удалось переименовать счёт") {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Введи название счёта" }
        accountRepository.rename(id, trimmed)
        store.update { state -> state.copy(accounts = state.accounts.map { if (it.id == id) it.copy(name = trimmed) else it }) }
        store.message("Счёт переименован")
    }

    fun setAccountRole(id: String, role: AccountRole) = runner.run("Не удалось изменить счёт") {
        val isSavings = role == AccountRole.CUSHION
        val isFund = role == AccountRole.FUND
        accountRepository.setRole(id, isSavings, isFund)
        store.update { state -> state.copy(accounts = state.accounts.map { if (it.id == id) it.copy(isSavings = isSavings, isFund = isFund) else it }) }
        store.message(
            when (role) {
                AccountRole.LIFE -> "Счёт теперь на жизнь"
                AccountRole.FUND -> "Счёт теперь копилка"
                AccountRole.CUSHION -> "Счёт теперь подушка"
            },
        )
    }

    fun setFund(id: String, monthly: Money?, goal: Money?, day: Int?) = runner.run("Не удалось сохранить копилку") {
        require(day == null || day in HomeDefaults.DAYS_OF_MONTH) { "День от 1 до 31" }
        val m = monthly?.takeIf { it.minor > 0L }
        val g = goal?.takeIf { it.minor > 0L }
        accountRepository.setFund(id, m, g, day)
        store.update { state -> state.copy(accounts = state.accounts.map { if (it.id == id) it.copy(fundMonthly = m, fundGoal = g, fundDay = day) else it }) }
        store.message("Копилка обновлена")
    }

    fun moveAccount(id: String, up: Boolean) = runner.run("Не удалось переставить счёт") {
        val current = store.value
        val target = current.accountsById[id] ?: return@run
        val group = current.activeAccounts.filter { it.role == target.role }.sortedBy { it.sortOrder }
        val index = group.indexOfFirst { it.id == id }
        val neighbourIndex = if (up) index - 1 else index + 1
        val neighbour = group.getOrNull(neighbourIndex) ?: return@run
        val reordered = group.toMutableList().apply {
            this[index] = neighbour
            this[neighbourIndex] = target
        }
        val orders = reordered.mapIndexed { position, account -> account.id to (position + 1) * HomeDefaults.SORT_STEP }.toMap()
        orders.forEach { (accountId, order) -> accountRepository.setSortOrder(accountId, order) }
        store.update { state ->
            state.copy(
                accounts = state.accounts
                    .map { account -> orders[account.id]?.let { account.copy(sortOrder = it) } ?: account }
                    .sortedBy { it.sortOrder },
            )
        }
    }

    fun archiveAccount(id: String) = setArchived(id, archived = true, "Счёт убран в архив")

    fun restoreAccount(id: String) = setArchived(id, archived = false, "Счёт возвращён из архива")

    private fun setArchived(id: String, archived: Boolean, done: String) = runner.run("Не удалось изменить счёт") {
        accountRepository.setArchived(id, archived)
        store.update { state -> state.copy(accounts = state.accounts.map { if (it.id == id) it.copy(isArchived = archived) else it }) }
        store.message(done)
    }
}

data class NewAccountDraft(
    val name: String,
    val currency: Currency,
    val role: AccountRole,
    val openingBalance: Money? = null,
    val fundMonthly: Money? = null,
    val fundGoal: Money? = null,
    val fundDay: Int? = null,
)
