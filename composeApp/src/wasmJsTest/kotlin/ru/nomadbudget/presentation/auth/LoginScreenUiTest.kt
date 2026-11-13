package ru.nomadbudget.presentation.auth

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.AuthState
import ru.nomadbudget.domain.repository.SessionCheck
import ru.nomadbudget.presentation.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LoginScreenUiTest {

    private class FakeAuth(private val failure: String? = null) : AuthRepository {
        val attempts = mutableListOf<Pair<String, String>>()
        override val state: Flow<AuthState> = emptyFlow()
        override val currentUserId: String? = null
        override suspend fun signIn(email: String, password: String) {
            attempts += email to password
            failure?.let { throw IllegalStateException(it) }
        }
        override suspend fun signOut() = Unit
        override suspend fun ensureSession() = SessionCheck.FRESH
        override suspend fun changePassword(newPassword: String) = Unit
        override suspend fun deleteAccount() = Unit
    }

    @Test
    fun submitIsDisabledUntilBothFieldsAreFilled() = runComposeUiTest {
        setContent { AppTheme { LoginScreen(FakeAuth()) } }
        onNodeWithTag(LoginTags.SUBMIT).assertIsNotEnabled()
        onNodeWithTag(LoginTags.EMAIL).performTextInput("a@example.test")
        onNodeWithTag(LoginTags.SUBMIT).assertIsNotEnabled()
        onNodeWithTag(LoginTags.PASSWORD).performTextInput("secret")
        onNodeWithTag(LoginTags.SUBMIT).assertIsEnabled()
    }

    @Test
    fun failedSignInShowsTheErrorAndPassesTrimmedEmail() = runComposeUiTest {
        val auth = FakeAuth(failure = "Неверный пароль")
        setContent { AppTheme { LoginScreen(auth) } }
        onNodeWithTag(LoginTags.EMAIL).performTextInput(" a@example.test ")
        onNodeWithTag(LoginTags.PASSWORD).performTextInput("secret")
        onNodeWithTag(LoginTags.SUBMIT).performClick()
        waitForIdle()
        onNodeWithTag(LoginTags.ERROR).assertTextEquals("Неверный пароль")
        assertEquals(listOf("a@example.test" to "secret"), auth.attempts)
    }
}
