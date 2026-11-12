package ru.nomadbudget.presentation.format

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.repository.KeyValueStore

object AmountMask {
    var hidden: Boolean by mutableStateOf(false)
}

class AmountPrivacy(private val store: KeyValueStore?) {
    var hideOnStart: Boolean by mutableStateOf(store?.get(KEY_ON_START) == ON)
        private set

    val hidden: Boolean get() = AmountMask.hidden

    init {
        AmountMask.hidden = hideOnStart || store?.get(KEY_HIDDEN) == ON
    }

    fun toggle() = setHidden(!AmountMask.hidden)

    fun setHidden(value: Boolean) {
        AmountMask.hidden = value
        if (value) store?.put(KEY_HIDDEN, ON) else store?.remove(KEY_HIDDEN)
    }

    fun changeHideOnStart(value: Boolean) {
        hideOnStart = value
        if (value) store?.put(KEY_ON_START, ON) else store?.remove(KEY_ON_START)
    }

    companion object {
        const val KEY_HIDDEN = "amounts_hidden"
        const val KEY_ON_START = "amounts_hide_on_start"
        private const val ON = "1"
    }
}

val LocalAmountPrivacy = staticCompositionLocalOf { AmountPrivacy(null) }

val LocalBaseCurrency = staticCompositionLocalOf { Currency.DEFAULT_BASE }
