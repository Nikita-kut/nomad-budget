package ru.nomadbudget.presentation.access

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.nomadbudget.domain.repository.AccessException
import ru.nomadbudget.domain.repository.AccessRepository
import ru.nomadbudget.domain.repository.AccountRow
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.AuthState
import ru.nomadbudget.domain.repository.InviteRow
import ru.nomadbudget.domain.repository.OwnerAction
import ru.nomadbudget.domain.repository.SessionCheck
import ru.nomadbudget.presentation.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class AccessScreensUiTest {

    private class FakeAuth : AuthRepository {
        val calls = mutableListOf<String>()
        override val state: Flow<AuthState> = emptyFlow()
        override val currentUserId: String? = null
        override suspend fun signIn(email: String, password: String) { calls += "signIn:$email" }
        override suspend fun signOut() = Unit
        override suspend fun ensureSession() = SessionCheck.FRESH
        override suspend fun changePassword(newPassword: String) { calls += "password" }
        override suspend fun deleteAccount() = Unit
    }

    private class FakeAccess(private val redeemError: String? = null, private val admin: Boolean = false) : AccessRepository {
        val calls = mutableListOf<String>()
        override suspend fun redeemInvite(code: String, email: String, password: String) {
            calls += "redeem:$code:$email"
            redeemError?.let { throw AccessException(it) }
        }
        override suspend fun openResetLink(tokenHash: String) { calls += "open:$tokenHash" }
        override suspend fun isAdmin() = admin
        override suspend fun ownerActions() = listOf(OwnerAction("reset_link_created", Instant.parse("2026-11-14T10:00:00Z")))
        override suspend fun createInvite(email: String) = "u"
        override suspend fun invites() = emptyList<InviteRow>()
        override suspend fun revokeInvite(id: String) = Unit
        override suspend fun accounts() = emptyList<AccountRow>()
        override suspend fun resetLink(userId: String) = "u"
        override suspend fun setBlocked(userId: String, blocked: Boolean) = Unit
    }

    @Test
    fun registrationNeedsMatchingLongPassword() = runComposeUiTest {
        val access = FakeAccess()
        setContent { AppTheme { RegisterScreen("CODE", access, FakeAuth(), onCancel = {}) } }
        onNodeWithTag(AccessTags.REGISTER_EMAIL).performTextInput("new@example.test")
        onNodeWithTag(AccessTags.REGISTER_PASSWORD).performTextInput("short")
        onNodeWithTag(AccessTags.REGISTER_REPEAT).performTextInput("short")
        onNodeWithTag(AccessTags.REGISTER_SUBMIT).assertIsNotEnabled()
        onNodeWithTag(AccessTags.REGISTER_PASSWORD).performTextInput("-now-long")
        onNodeWithTag(AccessTags.REGISTER_SUBMIT).assertIsNotEnabled()
        onNodeWithTag(AccessTags.REGISTER_REPEAT).performTextInput("-now-long")
        onNodeWithTag(AccessTags.REGISTER_SUBMIT).assertIsEnabled()
        assertEquals(emptyList(), access.calls)
    }

    @Test
    fun registrationSuccessRedeemsThenSignsIn() = runComposeUiTest {
        val access = FakeAccess()
        val auth = FakeAuth()
        setContent { AppTheme { RegisterScreen("CODE", access, auth, onCancel = {}) } }
        onNodeWithTag(AccessTags.REGISTER_EMAIL).performTextInput(" new@example.test ")
        onNodeWithTag(AccessTags.REGISTER_PASSWORD).performTextInput("long-enough-1")
        onNodeWithTag(AccessTags.REGISTER_REPEAT).performTextInput("long-enough-1")
        onNodeWithTag(AccessTags.REGISTER_SUBMIT).performClick()
        waitForIdle()
        assertEquals(listOf("redeem:CODE:new@example.test"), access.calls)
        assertEquals(listOf("signIn:new@example.test"), auth.calls)
    }

    @Test
    fun registrationErrorFromServerIsShownAndNoSignIn() = runComposeUiTest {
        val auth = FakeAuth()
        setContent { AppTheme { RegisterScreen("CODE", FakeAccess(redeemError = "Приглашение не подошло"), auth, onCancel = {}) } }
        onNodeWithTag(AccessTags.REGISTER_EMAIL).performTextInput("new@example.test")
        onNodeWithTag(AccessTags.REGISTER_PASSWORD).performTextInput("long-enough-1")
        onNodeWithTag(AccessTags.REGISTER_REPEAT).performTextInput("long-enough-1")
        onNodeWithTag(AccessTags.REGISTER_SUBMIT).performClick()
        waitForIdle()
        onNodeWithTag(AccessTags.ERROR).assertTextEquals("Приглашение не подошло")
        assertEquals(emptyList(), auth.calls)
    }

    @Test
    fun resetLinkIsUsedOnlyAfterTheButtonThenSetsPassword() = runComposeUiTest {
        val access = FakeAccess()
        val auth = FakeAuth()
        var done = false
        setContent { AppTheme { ResetPasswordScreen("TOKEN", access, auth, onDone = { done = true }) } }
        waitForIdle()
        assertEquals(emptyList(), access.calls)
        onNodeWithTag(AccessTags.RESET_OPEN).performClick()
        waitForIdle()
        assertEquals(listOf("open:TOKEN"), access.calls)
        onNodeWithTag(AccessTags.RESET_PASSWORD).performTextInput("new-password-1")
        onNodeWithTag(AccessTags.RESET_REPEAT).performTextInput("new-password-1")
        onNodeWithTag(AccessTags.RESET_SUBMIT).performClick()
        waitForIdle()
        assertEquals(listOf("password"), auth.calls)
        assertEquals(true, done)
    }

    @Test
    fun ownerActionsVisibleAndNoAdminLinkForUser() = runComposeUiTest {
        setContent { AppTheme { DataAndAccessCard(FakeAccess(admin = false), onOpenAdmin = {}) } }
        waitForIdle()
        onNodeWithText(ownerActionText("reset_link_created"), substring = true).assertExists()
        onNodeWithText("Открыть админку").assertDoesNotExist()
    }

    @Test
    fun adminSeesTheAdminLink() = runComposeUiTest {
        setContent { AppTheme { DataAndAccessCard(FakeAccess(admin = true), onOpenAdmin = {}) } }
        waitForIdle()
        onNodeWithText("Открыть админку").assertExists()
    }
}
