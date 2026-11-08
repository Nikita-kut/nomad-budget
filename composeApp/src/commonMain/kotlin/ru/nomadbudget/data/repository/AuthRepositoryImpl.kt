package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.AuthState
import ru.nomadbudget.domain.repository.SessionCheck
import kotlin.time.Clock

class AuthRepositoryImpl(private val client: SupabaseClient) : AuthRepository {

    private val sessionExpired = MutableStateFlow(false)

    override val state: Flow<AuthState> = combine(client.auth.sessionStatus, sessionExpired, ::toAuthState)

    override val currentUserId: String? get() = client.auth.currentUserOrNull()?.id

    override suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        sessionExpired.value = false
    }

    override suspend fun signOut() {
        sessionExpired.value = false
        dropSession()
    }

    override suspend fun ensureSession(): SessionCheck {
        val session = client.auth.currentSessionOrNull() ?: return SessionCheck.EXPIRED
        val stale = client.auth.sessionStatus.value is SessionStatus.RefreshFailure || session.expiresAt <= Clock.System.now()
        if (!stale) return SessionCheck.FRESH
        return try {
            client.auth.refreshCurrentSession()
            SessionCheck.FRESH
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            sessionExpired.value = true
            dropSession()
            SessionCheck.EXPIRED
        } catch (e: Exception) {
            SessionCheck.OFFLINE
        }
    }

    override suspend fun changePassword(newPassword: String) {
        client.auth.updateUser { password = newPassword }
    }

    override suspend fun deleteAccount() {
        client.postgrest.rpc(DELETE_ACCOUNT)
        sessionExpired.value = false
        dropSession()
    }

    private suspend fun dropSession() {
        runCatching { client.auth.signOut() }
        if (client.auth.currentSessionOrNull() != null) client.auth.clearSession()
    }

    private fun toAuthState(status: SessionStatus, expired: Boolean): AuthState = when (status) {
        is SessionStatus.Initializing -> AuthState.Loading
        is SessionStatus.Authenticated -> status.session.user?.id?.let(AuthState::SignedIn) ?: AuthState.SignedOut(expired)
        is SessionStatus.NotAuthenticated -> AuthState.SignedOut(expired)
        is SessionStatus.RefreshFailure -> currentUserId?.let(AuthState::SignedIn) ?: AuthState.SignedOut(expired)
    }

    private companion object {
        const val DELETE_ACCOUNT = "delete_own_account"
    }
}
