package ru.nomadbudget.presentation.home

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class Memo {
    private class Entry(val keys: Array<out Any?>, val value: Any?)

    private val entries = AtomicReference<Map<String, Entry>>(emptyMap())

    @Suppress("UNCHECKED_CAST")
    fun <T> get(slot: String, vararg keys: Any?, compute: () -> T): T {
        val cached = entries.load()[slot]
        if (cached != null && cached.keys.size == keys.size && cached.keys.indices.all { cached.keys[it] === keys[it] }) {
            return cached.value as T
        }
        val value = compute()
        while (true) {
            val current = entries.load()
            if (entries.compareAndSet(current, current + (slot to Entry(keys, value)))) break
        }
        return value
    }

    override fun equals(other: Any?): Boolean = other is Memo

    override fun hashCode(): Int = 0
}
