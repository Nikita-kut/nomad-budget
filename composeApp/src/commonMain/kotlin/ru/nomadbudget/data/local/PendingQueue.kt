package ru.nomadbudget.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.nomadbudget.data.dto.TransactionInsertDto

@Serializable
data class PendingTransaction(val id: String, val dto: TransactionInsertDto)

class PendingQueue(private val store: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PendingTransaction.serializer())

    fun all(): List<PendingTransaction> = store.get(KEY)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    fun add(item: PendingTransaction) = save(all().filterNot { it.id == item.id } + item)

    fun remove(id: String) = save(all().filterNot { it.id == id })

    fun contains(id: String): Boolean = all().any { it.id == id }

    val size: Int get() = all().size

    private fun save(items: List<PendingTransaction>) {
        if (items.isEmpty()) store.remove(KEY) else store.put(KEY, json.encodeToString(serializer, items))
    }

    private companion object {
        const val KEY = "pending_transactions"
    }
}
