package ru.nomadbudget.data.local

import io.github.jan.supabase.exceptions.RestException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

class OfflineCache(private val store: LocalStore) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var servedFromCache: Boolean = false
        private set

    var oldestCachedAt: Instant? = null
        private set

    fun beginLoad() {
        servedFromCache = false
        oldestCachedAt = null
    }

    suspend fun <T> through(key: String, serializer: KSerializer<T>, fetch: suspend () -> T): T {
        return try {
            val value = fetch()
            store.put(key, json.encodeToString(serializer, value))
            store.put(timeKey(key), Clock.System.now().toEpochMilliseconds().toString())
            value
        } catch (e: Exception) {
            if (!isOffline(e)) throw e
            val cached = store.get(key) ?: throw e
            markServedFromCache(key)
            json.decodeFromString(serializer, cached)
        }
    }

    suspend fun <T> throughOrDefault(key: String, serializer: KSerializer<T>, default: T, fetch: suspend () -> T): T {
        return try {
            through(key, serializer, fetch)
        } catch (e: Exception) {
            if (!isOffline(e)) throw e
            markServedFromCache(key)
            default
        }
    }

    private fun markServedFromCache(key: String) {
        servedFromCache = true
        val time = store.get(timeKey(key))?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
        if (time != null && (oldestCachedAt == null || time < oldestCachedAt!!)) oldestCachedAt = time
    }

    private fun timeKey(key: String) = "$key:t"

    companion object {
        fun isOffline(e: Throwable): Boolean = e !is RestException
    }
}
