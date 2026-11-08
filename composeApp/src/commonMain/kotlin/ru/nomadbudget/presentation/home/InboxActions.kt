package ru.nomadbudget.presentation.home

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Draft
import ru.nomadbudget.domain.model.DraftParser
import ru.nomadbudget.domain.repository.DraftRepository

internal class InboxActions(
    private val store: HomeStore,
    private val runner: ActionRunner,
    private val draftRepository: DraftRepository,
) {
    private val _navigateToEntry = Channel<Unit>(Channel.BUFFERED)
    val navigateToEntry: Flow<Unit> = _navigateToEntry.receiveAsFlow()

    fun openEntry(prefill: EntryPrefill) = runner.run("Не удалось открыть форму") {
        store.update { it.copy(entryPrefill = prefill) }
        _navigateToEntry.send(Unit)
    }

    fun prefillMissedExpense(accountId: String, date: LocalDate) =
        openEntry(EntryPrefill(draftId = null, amountText = null, note = "", accountId = accountId, date = date))

    fun useDraft(draft: Draft) = openEntry(EntryPrefill(draft.id, DraftParser.amountText(draft.text), DraftParser.noteWithoutAmount(draft.text)))

    fun clearPrefill() = store.update { it.copy(entryPrefill = null) }

    fun reloadDrafts() = store.update { it.copy(drafts = draftRepository.all()) }

    fun addDraft(text: String) = runner.run("Не удалось сохранить заметку") {
        require(text.isNotBlank()) { "Введи текст заметки" }
        draftRepository.add(text)
        reloadDrafts()
        store.message("Заметка сохранена во Входящие")
    }

    fun removeDraft(id: String) {
        draftRepository.remove(id)
        reloadDrafts()
    }
}
