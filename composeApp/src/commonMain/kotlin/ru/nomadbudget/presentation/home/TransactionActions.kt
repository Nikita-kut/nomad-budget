package ru.nomadbudget.presentation.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.domain.logic.BalanceCalculator
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.CorrectionCategory
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.repository.BalanceCheckRepository
import ru.nomadbudget.domain.repository.DraftRepository
import ru.nomadbudget.domain.repository.TransactionRepository
import ru.nomadbudget.presentation.format.MoneyFormat

data class UndoRequest(val id: String, val text: String)

internal class TransactionActions(
    private val store: HomeStore,
    private val runner: ActionRunner,
    private val scope: CoroutineScope,
    private val transactionRepository: TransactionRepository,
    private val balanceCheckRepository: BalanceCheckRepository,
    private val draftRepository: DraftRepository,
    private val categories: CategoryActions,
    private val today: LocalDate,
) {
    private val _undoRequests = Channel<UndoRequest>(Channel.BUFFERED)
    val undoRequests: Flow<UndoRequest> = _undoRequests.receiveAsFlow()
    private val pendingDeletes = mutableMapOf<String, Pair<Int, Transaction>>()
    private val deleteTimers = mutableMapOf<String, Job>()

    fun isPendingDelete(id: String): Boolean = id in pendingDeletes

    fun reset() {
        deleteTimers.values.forEach(Job::cancel)
        deleteTimers.clear()
        pendingDeletes.clear()
    }

    fun addEntry(draft: EntryDraft) = runner.run("Не удалось сохранить", busy = true) {
        val current = store.value
        val rates = current.rates
        require(rates.hasRate(draft.amount.currency)) { "Нет курса для ${draft.amount.currency.code}, операцию пока не записать" }
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
                subcategoryId = categories.resolveSubcategory(draft.categoryId, draft.subcategoryName),
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
        store.update {
            it.copy(
                transactions = (listOfNotNull(second, saved) + it.transactions).distinctBy { tx -> tx.id },
                pendingCount = transactionRepository.pendingCount(),
                drafts = draftRepository.all(),
                entryPrefill = null,
            )
        }
        val debtNote = debt?.let { debtNoteAfter(it.id, principal?.let { p -> -p }) }
        store.message(
            when {
                saved.pending -> "Записано без сети, отправится при обновлении"
                second != null -> "Записано двумя операциями: ${MoneyFormat.format(draft.amount, false)} + ${MoneyFormat.format(draft.splitAmount ?: draft.amount, false)}"
                draft.type == EntryType.EXPENSE -> "Расход записан" + debtNote.orEmpty()
                draft.type == EntryType.INCOME -> "Доход записан"
                else -> "Перевод записан"
            },
        )
    }

    fun addExchange(draft: ExchangeDraft) = runner.run("Не удалось сохранить обмен", busy = true) {
        val current = store.value
        require(current.rates.hasRate(draft.given.currency)) { "Нет курса для ${draft.given.currency.code}, обмен пока не записать" }
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
        prepend(saved)
        store.message(if (saved.pending) "Записано без сети, отправится при обновлении" else "Обмен записан")
    }

    fun repeatTransaction(source: Transaction) = runner.run("Не удалось повторить", busy = true) {
        val current = store.value
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
        prepend(saved)
        store.message("Операция повторена на сегодня")
    }

    fun updateTransaction(updated: Transaction, subcategoryName: String?) = runner.run("Не удалось обновить операцию", busy = true) {
        val current = store.value
        val original = current.transactions.firstOrNull { it.id == updated.id }
        require(original != null) { "Операция не найдена" }
        val withBase = withRecalculatedBase(original, updated)
        val resolved = when (withBase) {
            is Transaction.Expense -> withDebtPrincipal(
                original as? Transaction.Expense,
                withBase.copy(subcategoryId = subcategoryName?.let { categories.resolveSubcategory(withBase.categoryId, it) }),
            )
            is Transaction.Income, is Transaction.Transfer, is Transaction.Exchange -> withBase
        }
        val saved = transactionRepository.update(resolved, current.accounts)
        store.update { state -> state.copy(transactions = state.transactions.map { if (it.id == saved.id) saved else it }) }
        val debtNote = (saved as? Transaction.Expense)?.debtId?.let { debtId ->
            val oldPrincipal = (original as? Transaction.Expense)?.takeIf { it.debtId == debtId }?.debtPrincipal
            val newPrincipal = saved.debtPrincipal
            val currency = (newPrincipal ?: oldPrincipal)?.currency
            val delta = currency?.let { (oldPrincipal ?: Money.zero(it)) - (newPrincipal ?: Money.zero(it)) }
            debtNoteAfter(debtId, delta)
        }
        store.message("Операция обновлена" + debtNote.orEmpty())
    }

    fun deleteTransaction(id: String) {
        val transactions = store.value.transactions
        val index = transactions.indexOfFirst { it.id == id }
        if (index < 0) return
        val transaction = transactions[index]
        pendingDeletes[id] = index to transaction
        store.update { state -> state.copy(transactions = state.transactions.filterNot { it.id == id }) }
        scope.launch { _undoRequests.send(UndoRequest(id, "Удалено: ${undoLabel(transaction)}")) }
        deleteTimers.remove(id)?.cancel()
        deleteTimers[id] = scope.launch {
            delay(HomeDefaults.UNDO_WINDOW_MS)
            commitDelete(id)
        }
    }

    fun undoDelete(id: String) {
        deleteTimers.remove(id)?.cancel()
        val (index, transaction) = pendingDeletes.remove(id) ?: return
        store.update { state ->
            val list = state.transactions.toMutableList()
            list.add(index.coerceIn(0, list.size), transaction)
            state.copy(transactions = list)
        }
    }

    private fun commitDelete(id: String) {
        deleteTimers.remove(id)
        val (_, transaction) = pendingDeletes.remove(id) ?: return
        scope.launch {
            try {
                val removed = transaction as? Transaction.Expense
                transactionRepository.delete(id)
                store.update { state -> state.copy(transactions = state.transactions.filterNot { it.id == id }, pendingCount = transactionRepository.pendingCount()) }
                val debtNote = removed?.debtId?.let { debtId ->
                    removed.debtPrincipal?.let { debtNoteAfter(debtId, it, restored = true) } ?: ", остаток кредита не изменился"
                }
                debtNote?.let { store.message("Удалено" + it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                store.update { state -> state.copy(transactions = (listOf(transaction) + state.transactions).distinctBy { tx -> tx.id }) }
                store.message(e.message ?: "Не удалось удалить")
            }
        }
    }

    fun checkBalance(accountId: String, actual: Money, note: String, date: LocalDate) = runner.run("Не удалось записать сверку", busy = true) {
        val current = store.value
        val account = requireNotNull(current.accountsById[accountId]) { "Счёт не найден" }
        require(date <= today) { "Сверку нельзя провести будущей датой" }
        val computed = BalanceCalculator.balanceBefore(account, current.transactions, date.plus(1, DateTimeUnit.DAY))
        require(actual.currency == account.currency) { "Валюта не совпадает со счётом" }
        val check = balanceCheckRepository.add(
            BalanceCheck(id = "", accountId = accountId, date = date, actual = actual, computed = computed, note = note),
        )
        val difference = actual - computed
        val correction = if (difference.isZero) null else createCorrection(account.id, difference, computed, actual, date)
        store.update { state ->
            state.copy(
                balanceChecks = listOf(check) + state.balanceChecks,
                transactions = (listOfNotNull(correction) + state.transactions).distinctBy { tx -> tx.id },
            )
        }
        store.message(
            if (difference.isZero) "Сверка записана, остаток сходится"
            else "Сверка записана, корректировка ${MoneyFormat.formatSigned(difference)}",
        )
    }

    private fun prepend(saved: Transaction) {
        store.update { it.copy(transactions = (listOf(saved) + it.transactions).distinctBy { tx -> tx.id }, pendingCount = transactionRepository.pendingCount()) }
    }

    private suspend fun createCorrection(accountId: String, difference: Money, computed: Money, actual: Money, date: LocalDate): Transaction {
        val current = store.value
        require(current.rates.hasRate(difference.currency)) { "Нет курса для ${difference.currency.code}, корректировку не записать" }
        val kind = if (difference.isNegative) CategoryKind.EXPENSE else CategoryKind.INCOME
        val category = categories.ensureCategory(CorrectionCategory.NAME, kind)
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

    private fun withRecalculatedBase(original: Transaction, updated: Transaction): Transaction {
        val rates = store.value.rates
        val base = if (updated.ownAmount == original.ownAmount && updated.kind == original.kind) original.amountBase else rates.toBase(updated.ownAmount)
        return when (updated) {
            is Transaction.Expense -> updated.copy(amountBase = base)
            is Transaction.Income -> updated.copy(amountBase = base)
            is Transaction.Transfer -> updated.copy(amountBase = base)
            is Transaction.Exchange -> updated.copy(amountBase = base)
        }
    }

    private fun withDebtPrincipal(original: Transaction.Expense?, updated: Transaction.Expense): Transaction.Expense {
        val debtId = updated.debtId ?: return updated.copy(debtPrincipal = null, debtEarly = false)
        val debt = store.value.debts.firstOrNull { it.id == debtId } ?: return updated
        val restoredPrincipal = original?.takeIf { it.debtId == debtId }?.debtPrincipal
        val before = restoredPrincipal?.takeIf { it.currency == debt.currency }?.let { debt.principalRemaining + it } ?: debt.principalRemaining
        val principal = DebtCalculator.principalFor(debt.copy(principalRemaining = before), updated.amount, updated.debtEarly)
        return updated.copy(debtPrincipal = principal)
    }

    private fun debtNoteAfter(debtId: String, delta: Money?, restored: Boolean = false): String {
        val debt = store.value.debts.firstOrNull { it.id == debtId } ?: return ""
        if (delta == null) return ", остаток кредита не изменился: другая валюта"
        if (delta.currency != debt.currency) return ""
        val updated = debt.copy(principalRemaining = debt.principalRemaining + delta)
        store.update { state -> state.copy(debts = state.debts.map { if (it.id == debtId) updated else it }) }
        val remaining = MoneyFormat.format(updated.principalRemaining, false)
        return when {
            restored -> ", остаток кредита восстановлен: $remaining"
            delta.isNegative -> ", в погашение тела ${MoneyFormat.format(-delta, false)}, остаток $remaining"
            else -> ", остаток кредита $remaining"
        }
    }

    private fun rateSourceFor(amount: Money): RateSource = store.value.rates.rateFor(amount.currency)?.source ?: RateSource.API

    private fun undoLabel(transaction: Transaction): String {
        val amount = MoneyFormat.format(transaction.ownAmount, false)
        return when (transaction) {
            is Transaction.Expense -> "${store.value.categoryName(transaction.categoryId)}, $amount"
            is Transaction.Income -> "${store.value.categoryName(transaction.categoryId)}, $amount"
            is Transaction.Transfer -> "перевод $amount"
            is Transaction.Exchange -> "обмен $amount"
        }
    }
}
