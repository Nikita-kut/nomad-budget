package ru.nomadbudget.domain.repository

import kotlin.time.Instant

data class OwnerAction(val action: String, val at: Instant)

data class InviteRow(
    val id: String,
    val email: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val revokedAt: Instant?,
)

data class AccountRow(
    val id: String,
    val email: String?,
    val createdAt: Instant?,
    val lastSignInAt: Instant?,
    val blocked: Boolean,
)

class AccessException(message: String) : Exception(message)

interface AccessRepository {
    suspend fun redeemInvite(code: String, email: String, password: String)
    suspend fun openResetLink(tokenHash: String)
    suspend fun isAdmin(): Boolean
    suspend fun ownerActions(): List<OwnerAction>
    suspend fun createInvite(email: String): String
    suspend fun invites(): List<InviteRow>
    suspend fun revokeInvite(id: String)
    suspend fun accounts(): List<AccountRow>
    suspend fun resetLink(userId: String): String
    suspend fun setBlocked(userId: String, blocked: Boolean)
}
