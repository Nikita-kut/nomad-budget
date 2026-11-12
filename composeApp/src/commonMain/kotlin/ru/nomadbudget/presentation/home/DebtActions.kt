package ru.nomadbudget.presentation.home

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DebtCloseReason
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.repository.DebtRepository
import ru.nomadbudget.presentation.format.MoneyFormat

internal class DebtActions(
    private val store: HomeStore,
    private val runner: ActionRunner,
    private val debtRepository: DebtRepository,
    private val inbox: InboxActions,
    private val today: LocalDate,
) {

    fun saveDebt(debt: Debt) = runner.run("Не удалось сохранить кредит") {
        require(debt.name.isNotBlank()) { "Введи название кредита" }
        if (debt.id.isEmpty()) {
            val created = debtRepository.add(debt)
            store.update { it.copy(debts = it.debts + created) }
            store.message("Кредит добавлен")
        } else {
            val current = store.value.debts.firstOrNull { it.id == debt.id }
            val principalChanged = current == null || current.principalRemaining != debt.principalRemaining
            debtRepository.update(debt, principalChanged)
            store.update { state ->
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
            store.message("Кредит обновлён")
        }
    }

    fun closeDebt(id: String) = close(id, writeOff = false, reason = DebtCloseReason.MANUAL)

    fun closePaidOff(id: String) {
        val state = store.value
        val early = state.transactions.any { it is Transaction.Expense && it.debtId == id && it.debtEarly }
        close(id, writeOff = true, reason = if (early) DebtCloseReason.EARLY else DebtCloseReason.PAID_OFF)
    }

    private fun close(id: String, writeOff: Boolean, reason: DebtCloseReason) = runner.run("Не удалось закрыть кредит") {
        val on = store.value.today
        debtRepository.close(id, reason, on, writeOff)
        store.update { state ->
            state.copy(debts = state.debts.map { debt ->
                if (debt.id != id) return@map debt
                val tail = if (writeOff && !debt.principalRemaining.isNegative) debt.principalRemaining else Money.zero(debt.currency)
                debt.copy(isClosed = true, closedOn = on, closeReason = reason, writtenOff = tail, principalRemaining = debt.principalRemaining - tail)
            })
        }
        store.message("Кредит закрыт, он в архиве внизу экрана")
    }

    fun reopenDebt(id: String) = runner.run("Не удалось открыть кредит") {
        debtRepository.reopen(id)
        store.update { state ->
            state.copy(debts = state.debts.map { debt ->
                if (debt.id != id) return@map debt
                debt.copy(isClosed = false, closedOn = null, closeReason = null, principalRemaining = debt.principalRemaining + debt.writtenOff, writtenOff = Money.zero(debt.currency))
            })
        }
        store.message("Кредит снова открыт")
    }

    fun payDebt(debtId: String) {
        val current = store.value
        val debt = current.debts.firstOrNull { it.id == debtId } ?: return
        val lastPayment = current.transactions.filterIsInstance<Transaction.Expense>().filter { it.debtId == debtId }.maxByOrNull { it.date }
        val categoryId = lastPayment?.categoryId
            ?: current.activeCategories.firstOrNull { it.kind == CategoryKind.EXPENSE && it.name.equals(HomeDefaults.DEBT_CATEGORY, ignoreCase = true) }?.id
            ?: current.activeCategories.firstOrNull { it.kind == CategoryKind.EXPENSE && it.name.equals(HomeDefaults.LEGACY_DEBT_CATEGORY, ignoreCase = true) }?.id
        inbox.openEntry(
            EntryPrefill(
                draftId = null,
                amountText = MoneyFormat.editText(debt.plannedPayment),
                note = "",
                categoryId = categoryId,
                subcategoryName = lastPayment?.subcategoryId?.let(current::subcategoryName) ?: debt.name,
                accountId = lastPayment?.accountId?.takeIf { id -> current.accountsById[id]?.isArchived == false },
                debtId = debtId,
                date = today,
            ),
        )
    }
}
