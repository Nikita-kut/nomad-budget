package ru.nomadbudget.domain.repository

import kotlin.time.Instant

enum class TicketKind(val id: String, val title: String) {
    BUG("bug", "Ошибка"),
    IDEA("idea", "Идея"),
    QUESTION("question", "Вопрос");

    companion object {
        fun of(id: String): TicketKind = entries.firstOrNull { it.id == id } ?: BUG
    }
}

enum class TicketStatus(val id: String, val title: String) {
    NEW("new", "получено"),
    IN_PROGRESS("in_progress", "в работе"),
    DONE("done", "сделано"),
    REJECTED("rejected", "не будет сделано");

    companion object {
        fun of(id: String): TicketStatus = entries.firstOrNull { it.id == id } ?: NEW
    }
}

data class TicketDraft(
    val kind: TicketKind,
    val body: String,
    val screen: String?,
    val markX: Float?,
    val markY: Float?,
    val tech: Map<String, String>,
    val screenshotPng: ByteArray?,
)

data class Ticket(
    val id: String,
    val number: Long,
    val createdAt: Instant,
    val kind: TicketKind,
    val body: String,
    val screen: String?,
    val status: TicketStatus,
    val statusAt: Instant,
    val email: String? = null,
    val tech: Map<String, String> = emptyMap(),
    val markX: Float? = null,
    val markY: Float? = null,
    val screenshotUrl: String? = null,
)

interface TicketRepository {
    suspend fun create(draft: TicketDraft): Long
    suspend fun mine(): List<Ticket>
}
