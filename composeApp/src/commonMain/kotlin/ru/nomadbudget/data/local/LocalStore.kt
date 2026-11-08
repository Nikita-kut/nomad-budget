package ru.nomadbudget.data.local

interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(prefix: String): List<String>
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

    override fun keys(prefix: String): List<String> = values.keys.filter { it.startsWith(prefix) }

    override fun clear() {
        values.clear()
    }
}

class UserScopedStore(private val base: KeyValueStore, private val userId: () -> String?) : KeyValueStore {

    private val prefix: String get() = "$SCOPE${userId() ?: ANONYMOUS}$SEPARATOR"

    override fun get(key: String): String? = base.get(prefix + key)

    override fun put(key: String, value: String) = base.put(prefix + key, value)

    override fun remove(key: String) = base.remove(prefix + key)

    override fun keys(prefix: String): List<String> {
        val scope = this.prefix
        return base.keys(scope + prefix).map { it.removePrefix(scope) }
    }

    override fun clear() {
        val scope = prefix
        base.keys(scope).forEach(base::remove)
    }

    companion object {
        const val SCOPE = "u:"
        const val SEPARATOR = ":"
        const val ANONYMOUS = "anon"
    }
}

expect class LocalStore() : KeyValueStore {
    override fun get(key: String): String?
    override fun put(key: String, value: String)
    override fun remove(key: String)
    override fun keys(prefix: String): List<String>
    override fun clear()
}
