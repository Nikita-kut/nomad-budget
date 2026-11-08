package ru.nomadbudget.presentation.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal object HomeDefaults {
    const val SORT_STEP = 10
    const val CORRECTION_SORT_ORDER = 900
    const val DEBT_CATEGORY = "Кредиты"
    const val LEGACY_DEBT_CATEGORY = "Долг"
    const val MIN_PASSWORD_LENGTH = 8
    const val UNDO_WINDOW_MS = 10_000L
    val DAYS_OF_MONTH: IntRange = 1..31
}

internal class HomeStore(initial: HomeState) {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<HomeState> get() = _state
    val value: HomeState get() = _state.value

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun update(block: (HomeState) -> HomeState) = _state.update(block)

    fun reset(state: HomeState) {
        _state.value = state
    }

    suspend fun message(text: String) = _messages.send(text)
}

internal class ActionRunner(private val scope: CoroutineScope, private val store: HomeStore) {

    fun run(fallback: String, busy: Boolean = false, block: suspend () -> Unit): Job? {
        if (busy) {
            if (store.value.saving) return null
            store.update { it.copy(saving = true) }
        }
        return scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.message(e.message ?: fallback)
            } finally {
                if (busy) store.update { it.copy(saving = false) }
            }
        }
    }
}
