package ru.nomadbudget.data.local

import ru.nomadbudget.domain.repository.KeyValueStore
import ru.nomadbudget.domain.repository.OfflineStatus

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

class OfflineCache(private val store: KeyValueStore) : OfflineStatus {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override var servedFromCache: Boolean = false
        private set

    override var oldestCachedAt: Instant? = null
        private set

    override fun beginLoad() {
        servedFromCache = false
        oldestCachedAt = null
    }

    fun <T> peek(key: String, serializer: KSerializer<T>): T? =
        store.get(key)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    fun <T> put(key: String, serializer: KSerializer<T>, value: T) {
        store.put(key, json.encodeToString(serializer, value))
        store.put(timeKey(key), Clock.System.now().toEpochMilliseconds().toString())
    }

    suspend fun <T> through(key: String, serializer: KSerializer<T>, fetch: suspend () -> T): T {
        return try {
            val value = fetch()
            put(key, serializer, value)
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
        val oldest = oldestCachedAt
        if (time != null && (oldest == null || time < oldest)) oldestCachedAt = time
    }

    private fun timeKey(key: String) = "$key:t"

    companion object {
        fun isOffline(e: Throwable): Boolean = when (e) {
            is UnauthorizedRestException -> true
            is RestException, is CancellationException, is SerializationException, is IllegalArgumentException -> false
            else -> true
        }
    }
}
