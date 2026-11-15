package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nomadbudget.core.config.AppConfig
import ru.nomadbudget.domain.repository.AccessException
import ru.nomadbudget.domain.repository.Ticket
import ru.nomadbudget.domain.repository.TicketDraft
import ru.nomadbudget.domain.repository.TicketKind
import ru.nomadbudget.domain.repository.TicketRepository
import ru.nomadbudget.domain.repository.TicketStatus
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class TicketRepositoryImpl(private val client: SupabaseClient) : TicketRepository {

    private val http = HttpClient()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun create(draft: TicketDraft): Long {
        val user = client.auth.currentUserOrNull() ?: throw AccessException("Войди снова")
        val token = client.auth.currentAccessTokenOrNull() ?: throw AccessException("Войди снова")
        val path = draft.screenshotPng?.let { png ->
            val path = "${user.id}/${Uuid.random()}.png"
            val response = http.post("${AppConfig.SUPABASE_URL}/storage/v1/object/$BUCKET/$path") {
                header("apikey", AppConfig.SUPABASE_KEY)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Image.PNG)
                setBody(png)
            }
            if (!response.status.isSuccess()) throw AccessException("Скриншот не загрузился, попробуй отправить без него")
            path
        }
        val row = client.from(TABLE).insert(
            TicketInsertDto(draft.kind.id, draft.body.trim(), draft.screen, draft.markX, draft.markY, draft.tech, path),
        ) { select(Columns.list("number")) }.decodeSingle<TicketNumberDto>()
        return row.number
    }

    override suspend fun mine(): List<Ticket> =
        client.from(TABLE)
            .select(Columns.list("id", "number", "created_at", "kind", "body", "screen", "status", "status_at")) { order("created_at", Order.DESCENDING) }
            .decodeList<TicketDto>()
            .map { it.toTicket() }

    @Serializable
    private data class TicketInsertDto(
        val kind: String,
        val body: String,
        val screen: String?,
        @SerialName("mark_x") val markX: Float?,
        @SerialName("mark_y") val markY: Float?,
        val tech: Map<String, String>,
        @SerialName("screenshot_path") val screenshotPath: String?,
    )

    @Serializable
    private data class TicketNumberDto(val number: Long)

    @Serializable
    private data class TicketDto(
        val id: String,
        val number: Long,
        @SerialName("created_at") val createdAt: String,
        val kind: String,
        val body: String,
        val screen: String? = null,
        val status: String,
        @SerialName("status_at") val statusAt: String,
    ) {
        fun toTicket() = Ticket(id, number, Instant.parse(createdAt), TicketKind.of(kind), body, screen, TicketStatus.of(status), Instant.parse(statusAt))
    }

    private companion object {
        const val TABLE = "tickets"
        const val BUCKET = "tickets"
    }
}
