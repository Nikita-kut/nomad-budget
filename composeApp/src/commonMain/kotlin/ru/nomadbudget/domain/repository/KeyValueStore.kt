package ru.nomadbudget.domain.repository

import kotlin.time.Instant

interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(prefix: String): List<String>
    fun clear()
}

interface OfflineStatus {
    val servedFromCache: Boolean
    val oldestCachedAt: Instant?
    fun beginLoad()
}
