package ru.nomadbudget.data.local

interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

class InMemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun clear() {
        values.clear()
    }
}

expect class LocalStore() : KeyValueStore {
    override fun get(key: String): String?
    override fun put(key: String, value: String)
    override fun remove(key: String)
    override fun clear()
}
