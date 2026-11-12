package ru.nomadbudget.presentation.home

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.DebtCloseReason
import ru.nomadbudget.domain.model.EarlyRepaymentMode
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.PaymentChange
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.repository.DebtRepository
import ru.nomadbudget.domain.repository.TransactionRepository
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat

internal class DebtActions(
    private val store: HomeStore,
    private val runner: ActionRunner,
    private val debtRepository: DebtRepository,
    private val inbox: InboxActions,
    private val today: LocalDate,
    private val transactionRepository: TransactionRepository,
    private val categories: CategoryActions,
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

    fun repayEarly(draft: EarlyRepaymentDraft) = runner.run("Не удалось записать досрочное погашение", busy = true) {
        val state = store.value
        val debt = requireNotNull(state.debts.firstOrNull { it.id == draft.debtId }) { "Кредит не найден" }
        val account = requireNotNull(state.accountsById[draft.accountId]) { "Выбери счёт" }
        require(draft.amount.minor > 0L) { "Введи сумму" }
        require(draft.amount.currency == debt.currency && account.currency == debt.currency) { "Счёт и сумма должны быть в валюте кредита" }
        require(state.rates.hasRate(draft.amount.currency)) { "Нет курса для ${draft.amount.currency.code}" }
        require(!state.offline || draft.mode == EarlyRepaymentMode.REDUCE_TERM) { "Нужна сеть: без неё досрочку можно записать только с уменьшением срока" }
        val categoryId = requireNotNull(debtCategoryId(state, debt.id)) { "Нужна категория расходов «${HomeDefaults.DEBT_CATEGORY}»" }
        val lastPayment = lastPayment(state, debt.id)
        val principal = DebtCalculator.principalFor(debt, draft.amount, early = true)
        val transaction = Transaction.Expense(
            id = "",
            date = draft.date,
            accountId = account.id,
            amount = draft.amount,
            categoryId = categoryId,
            subcategoryId = categories.resolveSubcategory(categoryId, lastPayment?.subcategoryId?.let(state::subcategoryName) ?: debt.name),
            amountBase = state.marketBase(draft.amount, draft.date),
            rateSource = state.rates.rateFor(draft.amount.currency)?.source ?: RateSource.API,
            note = "Досрочное погашение",
            debtId = debt.id,
            debtPrincipal = principal,
            debtEarly = true,
        )
        val saved = transactionRepository.add(transaction, state.accounts)
        var updated = debt.copy(principalRemaining = debt.principalRemaining - (principal ?: Money.zero(debt.currency)))
        if (draft.mode == EarlyRepaymentMode.REDUCE_PAYMENT) {
            val payment = requireNotNull(draft.newPayment) { "Впиши новый платёж из графика банка" }
            val from = requireNotNull(draft.effectiveFrom) { "Укажи, с какого платежа" }
            debtRepository.changePayment(debt.id, from, payment, saved.id.takeUnless { saved.pending })
            val history = debt.paymentChanges.ifEmpty { listOf(PaymentChange(debt.openedOn ?: draft.date, debt.monthlyPayment)) }
            updated = updated.copy(monthlyPayment = payment, paymentChanges = history + PaymentChange(from, payment))
        }
        if (draft.mode == EarlyRepaymentMode.PAY_OFF) {
            debtRepository.close(debt.id, DebtCloseReason.EARLY, draft.date, writeOff = true)
            val tail = if (updated.principalRemaining.isNegative) Money.zero(debt.currency) else updated.principalRemaining
            updated = updated.copy(isClosed = true, closedOn = draft.date, closeReason = DebtCloseReason.EARLY, writtenOff = tail, principalRemaining = updated.principalRemaining - tail)
        }
        val result = updated
        store.update { it.copy(rawTransactions = (listOf(saved) + it.rawTransactions).distinctBy { tx -> tx.id }, debts = it.debts.map { d -> if (d.id == debt.id) result else d }, pendingCount = transactionRepository.pendingCount()) }
        store.message(
            when (draft.mode) {
                EarlyRepaymentMode.PAY_OFF -> "Кредит погашен и убран в архив"
                EarlyRepaymentMode.REDUCE_PAYMENT -> "Досрочка записана, платёж ${MoneyFormat.format(result.monthlyPayment, false)} с ${DateFormat.dayMonth(requireNotNull(draft.effectiveFrom))}"
                EarlyRepaymentMode.REDUCE_TERM -> "Досрочка записана, осталось ${MoneyFormat.format(result.principalRemaining, false)}"
            },
        )
    }

    private fun lastPayment(state: HomeState, debtId: String): Transaction.Expense? =
        state.transactions.filterIsInstance<Transaction.Expense>().filter { it.debtId == debtId }.maxByOrNull { it.date }

    private fun debtCategoryId(state: HomeState, debtId: String): String? =
        lastPayment(state, debtId)?.categoryId
            ?: state.activeCategories.firstOrNull { it.kind == CategoryKind.EXPENSE && it.name.equals(HomeDefaults.DEBT_CATEGORY, ignoreCase = true) }?.id
            ?: state.activeCategories.firstOrNull { it.kind == CategoryKind.EXPENSE && it.name.equals(HomeDefaults.LEGACY_DEBT_CATEGORY, ignoreCase = true) }?.id

    fun payDebt(debtId: String) {
        val current = store.value
        val debt = current.debts.firstOrNull { it.id == debtId } ?: return
        val lastPayment = lastPayment(current, debtId)
        val categoryId = debtCategoryId(current, debtId)
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

data class EarlyRepaymentDraft(
    val debtId: String,
    val accountId: String,
    val amount: Money,
    val date: LocalDate,
    val mode: EarlyRepaymentMode,
    val newPayment: Money? = null,
    val effectiveFrom: LocalDate? = null,
)

