package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.nomadbudget.core.config.AppConfig
import ru.nomadbudget.domain.repository.AccessException
import ru.nomadbudget.domain.repository.AccessRepository
import ru.nomadbudget.domain.repository.AccountRow
import ru.nomadbudget.domain.repository.InviteRow
import ru.nomadbudget.domain.repository.OwnerAction
import kotlin.time.Instant

class AccessRepositoryImpl(private val client: SupabaseClient) : AccessRepository {

    private val http = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun redeemInvite(code: String, email: String, password: String) {
        val body = buildJsonObject {
            put("code", code)
            put("email", email)
            put("password", password)
        }
        call(REDEEM, body, token = null)
    }

    override suspend fun openResetLink(tokenHash: String) {
        client.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, tokenHash = tokenHash)
    }

    override suspend fun isAdmin(): Boolean =
        runCatching { client.postgrest.rpc(IS_ADMIN).decodeAs<Boolean>() }.getOrDefault(false)

    override suspend fun ownerActions(): List<OwnerAction> =
        client.postgrest.from(OWNER_ACTIONS)
            .select(Columns.list("action", "created_at")) { order("created_at", Order.DESCENDING) }
            .decodeList<OwnerActionDto>()
            .map { OwnerAction(it.action, Instant.parse(it.createdAt)) }

    override suspend fun createInvite(email: String): String =
        admin("invite_create") { put("email", email) }.jsonObject["url"]?.jsonPrimitive?.content
            ?: throw AccessException(GENERIC_ERROR)

    override suspend fun invites(): List<InviteRow> =
        json.decodeFromJsonElement<List<InviteDto>>(admin("invite_list") {}).map {
            InviteRow(it.id, it.email, Instant.parse(it.createdAt), Instant.parse(it.expiresAt), it.usedAt?.let(Instant::parse), it.revokedAt?.let(Instant::parse))
        }

    override suspend fun revokeInvite(id: String) {
        admin("invite_revoke") { put("id", id) }
    }

    override suspend fun accounts(): List<AccountRow> =
        json.decodeFromJsonElement<List<AccountDto>>(admin("users_list") {}).map {
            AccountRow(it.id, it.email, it.createdAt?.let(Instant::parse), it.lastSignInAt?.let(Instant::parse), it.blocked)
        }

    override suspend fun resetLink(userId: String): String =
        admin("reset_link") { put("user_id", userId) }.jsonObject["url"]?.jsonPrimitive?.content
            ?: throw AccessException(GENERIC_ERROR)

    override suspend fun setBlocked(userId: String, blocked: Boolean) {
        admin(if (blocked) "block" else "unblock") { put("user_id", userId) }
    }

    private suspend fun admin(action: String, fill: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonElement {
        val token = client.auth.currentAccessTokenOrNull() ?: throw AccessException("Войди снова")
        val body = buildJsonObject {
            put("action", action)
            fill()
        }
        return call(ADMIN, body, token)
    }

    private suspend fun call(function: String, body: JsonObject, token: String?): JsonElement {
        val response = http.post("${AppConfig.SUPABASE_URL}/functions/v1/$function") {
            header("apikey", AppConfig.SUPABASE_KEY)
            header("Authorization", "Bearer ${token ?: AppConfig.SUPABASE_KEY}")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull()
        if (!response.status.isSuccess()) {
            val message = (parsed as? JsonObject)?.get("error")?.let { (it as? JsonPrimitive)?.content }
            throw AccessException(message?.takeIf(::isHumanText) ?: GENERIC_ERROR)
        }
        return parsed ?: JsonObject(emptyMap())
    }

    private fun isHumanText(text: String): Boolean = text.any { it in 'а'..'я' || it in 'А'..'Я' }

    @Serializable
    private data class OwnerActionDto(val action: String, @SerialName("created_at") val createdAt: String)

    @Serializable
    private data class InviteDto(
        val id: String,
        val email: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("expires_at") val expiresAt: String,
        @SerialName("used_at") val usedAt: String? = null,
        @SerialName("revoked_at") val revokedAt: String? = null,
    )

    @Serializable
    private data class AccountDto(
        val id: String,
        val email: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("last_sign_in_at") val lastSignInAt: String? = null,
        val blocked: Boolean = false,
    )

    private companion object {
        const val REDEEM = "redeem-invite"
        const val ADMIN = "admin"
        const val IS_ADMIN = "is_admin"
        const val OWNER_ACTIONS = "owner_actions"
        const val GENERIC_ERROR = "Не получилось, попробуй ещё раз"
    }
}
