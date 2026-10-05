package ru.nomadbudget.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.nomadbudget.domain.model.Draft
import ru.nomadbudget.domain.repository.DraftRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
private data class DraftDto(val id: String, val text: String, val createdAt: Long)

@OptIn(ExperimentalUuidApi::class)
class DraftsStore(private val store: KeyValueStore) : DraftRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(DraftDto.serializer())

    override fun all(): List<Draft> = read().map { Draft(it.id, it.text, Instant.fromEpochMilliseconds(it.createdAt)) }.sortedByDescending { it.createdAt }

    override fun add(text: String): Draft {
        val draft = DraftDto(Uuid.random().toString(), text.trim(), Clock.System.now().toEpochMilliseconds())
        write(read() + draft)
        return Draft(draft.id, draft.text, Instant.fromEpochMilliseconds(draft.createdAt))
    }

    override fun remove(id: String) = write(read().filterNot { it.id == id })

    override fun count(): Int = read().size

    private fun read(): List<DraftDto> = store.get(KEY)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun write(items: List<DraftDto>) {
        if (items.isEmpty()) store.remove(KEY) else store.put(KEY, json.encodeToString(serializer, items))
    }

    companion object {
        const val KEY = "drafts"
    }
}
