package ru.nomadbudget.data.local

import ru.nomadbudget.core.config.AppConfig
import ru.nomadbudget.domain.repository.KeyValueStore

private fun storageGet(key: String): String? = js("localStorage.getItem(key)")

private fun storageSet(key: String, value: String) {
    js("localStorage.setItem(key, value)")
}

private fun storageRemove(key: String) {
    js("localStorage.removeItem(key)")
}

private fun storageClear(prefix: String) {
    js("Object.keys(localStorage).filter(function (k) { return k.indexOf(prefix) === 0; }).forEach(function (k) { localStorage.removeItem(k); })")
}

private fun storageKeys(prefix: String): String =
    js("Object.keys(localStorage).filter(function (k) { return k.indexOf(prefix) === 0; }).map(function (k) { return k.substring(prefix.length); }).join('\\n')")

actual class LocalStore actual constructor() : KeyValueStore {

    actual override fun get(key: String): String? = storageGet(PREFIX + key)

    actual override fun put(key: String, value: String) = storageSet(PREFIX + key, value)

    actual override fun remove(key: String) = storageRemove(PREFIX + key)

    actual override fun keys(prefix: String): List<String> =
        storageKeys(PREFIX + prefix).split('\n').filter { it.isNotEmpty() }.map { prefix + it }

    actual override fun clear() = storageClear(PREFIX)

    private companion object {
        const val PREFIX = AppConfig.STORAGE_PREFIX
    }
}
