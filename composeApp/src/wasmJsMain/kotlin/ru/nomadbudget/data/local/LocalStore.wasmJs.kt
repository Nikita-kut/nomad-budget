package ru.nomadbudget.data.local

private fun storageGet(key: String): String? = js("localStorage.getItem(key)")

private fun storageSet(key: String, value: String) {
    js("localStorage.setItem(key, value)")
}

private fun storageRemove(key: String) {
    js("localStorage.removeItem(key)")
}

actual class LocalStore actual constructor() : KeyValueStore {

    actual override fun get(key: String): String? = storageGet(PREFIX + key)

    actual override fun put(key: String, value: String) = storageSet(PREFIX + key, value)

    actual override fun remove(key: String) = storageRemove(PREFIX + key)

    private companion object {
        const val PREFIX = "nomad_budget:"
    }
}
