package ru.nomadbudget.presentation.home

import ru.nomadbudget.domain.model.BankRateWatch
import ru.nomadbudget.domain.model.BankRateWatchDraft
import ru.nomadbudget.domain.repository.BankRateWatchRepository

internal class BankWatchActions(
    private val store: HomeStore,
    private val runner: ActionRunner,
    private val repository: BankRateWatchRepository,
) {

    fun create(draft: BankRateWatchDraft) = runner.run("Не удалось включить слежение", busy = true) {
        validate(draft)
        repository.create(draft)
        reload()
        store.message("Слежение за курсом включено")
    }

    fun update(draft: BankRateWatchDraft) = runner.run("Не удалось сохранить слежение", busy = true) {
        validate(draft)
        val id = requireNotNull(store.value.bankWatch.watch?.id) { "Слежение не найдено" }
        repository.update(id, draft)
        reload()
        store.message("Слежение сохранено")
    }

    fun setEnabled(enabled: Boolean) = runner.run("Не удалось изменить слежение") {
        val watch = store.value.bankWatch.watch ?: return@run
        repository.setEnabled(watch.id, enabled)
        store.update { it.copy(bankWatch = it.bankWatch.copy(watch = watch.copy(enabled = enabled))) }
        store.message(if (enabled) "Слежение включено" else "Слежение на паузе")
    }

    fun delete() = runner.run("Не удалось удалить слежение", busy = true) {
        val watch = store.value.bankWatch.watch ?: return@run
        repository.delete(watch.id)
        reload()
        store.message("Слежение удалено")
    }

    fun removeSubscriber(id: String) = runner.run("Не удалось отписать") {
        repository.removeSubscriber(id)
        store.update { state -> state.copy(bankWatch = state.bankWatch.copy(subscribers = state.bankWatch.subscribers.filterNot { it.id == id })) }
        store.message("Подписчик отключён")
    }

    private suspend fun reload() {
        val data = repository.load(store.value.currencies)
        store.update { it.copy(bankWatch = data) }
    }

    private fun validate(draft: BankRateWatchDraft) {
        require(draft.from != draft.to) { "Выбери две разные валюты" }
        require(draft.target == null || draft.target > 0.0) { "Цель должна быть больше нуля" }
        require(draft.goodPercentile in BankRateWatch.PERCENTILES) { "Строгость: от 50 до 99" }
        require(draft.improvePercent > 0.0) { "Повтор при улучшении должен быть больше нуля" }
        require(BankRateWatch.validTradeMinutes(draft.tradeOpen, draft.tradeClose)) { "Часы биржи: начало раньше конца" }
        require(draft.digestAt == null || draft.digestAt in BankRateWatch.MINUTES) { "Время сводки: в пределах суток" }
        require(draft.quietFrom in BankRateWatch.HOURS && draft.quietTo in BankRateWatch.HOURS) { "Окно отправки: часы от 0 до 23" }
        require(draft.digestAt == null || BankRateWatch.inSendWindow(draft.digestAt, draft.quietFrom, draft.quietTo)) { "Время сводки должно быть внутри окна отправки" }
        require(draft.timezone.isNotBlank()) { "Укажи часовой пояс" }
    }
}
