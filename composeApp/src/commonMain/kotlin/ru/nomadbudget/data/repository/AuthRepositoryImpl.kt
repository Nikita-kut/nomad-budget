package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.AuthState

class AuthRepositoryImpl(private val client: SupabaseClient) : AuthRepository {

    override val state: Flow<AuthState> = client.auth.sessionStatus.map(::toAuthState)

    override suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    private fun toAuthState(status: SessionStatus): AuthState = when (status) {
        is SessionStatus.Initializing -> AuthState.LOADING
        is SessionStatus.Authenticated -> AuthState.SIGNED_IN
        is SessionStatus.NotAuthenticated -> AuthState.SIGNED_OUT
        is SessionStatus.RefreshFailure -> AuthState.SIGNED_OUT
    }
}
