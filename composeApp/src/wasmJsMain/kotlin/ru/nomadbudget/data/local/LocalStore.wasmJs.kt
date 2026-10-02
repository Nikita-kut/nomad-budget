package ru.nomadbudget.data.local

private fun storageGet(key: String): String? = js("localStorage.getItem(key)")

private fun storageSet(key: String, value: String) {
    js("localStorage.setItem(key, value)")
}

private fun storageRemove(key: String) {
    js("localStorage.removeItem(key)")
}

actual class LocalStore actual constructor() {

    actual fun get(key: String): String? = storageGet(PREFIX + key)

    actual fun put(key: String, value: String) = storageSet(PREFIX + key, value)

    actual fun remove(key: String) = storageRemove(PREFIX + key)

    private companion object {
        const val PREFIX = "nomad_budget:"
    }
}
