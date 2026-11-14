package ru.nomadbudget.presentation.access

import ru.nomadbudget.domain.repository.InviteRow
import ru.nomadbudget.presentation.auth.ownerContactText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AccessLogicTest {
    private val now = Instant.parse("2026-11-14T10:00:00Z")
    private fun invite(used: Instant? = null, revoked: Instant? = null, expires: Instant = Instant.parse("2026-11-20T10:00:00Z")) =
        InviteRow("i", "x@example.test", Instant.parse("2026-11-13T10:00:00Z"), expires, used, revoked)

    @Test
    fun passwordRulesMatchTheServer() {
        assertTrue(passwordProblem("short", "") != null)
        assertNull(passwordProblem("long-enough", ""))
        assertNull(passwordProblem("long-enough", "long-enough"))
        assertEquals("Пароли не совпадают", passwordProblem("long-enough", "long-enouGh"))
        assertTrue(passwordProblem("я".repeat(40), "") != null)
        assertNull(passwordProblem("я".repeat(36), ""))
    }

    @Test
    fun inviteStatusCoversEveryState() {
        assertTrue(inviteStatus(invite(), now).startsWith("ждёт до"))
        assertTrue(inviteStatus(invite(used = now), now).startsWith("использовано"))
        assertEquals("отозвано", inviteStatus(invite(revoked = now), now))
        assertEquals("истекло", inviteStatus(invite(expires = Instant.parse("2026-11-14T09:00:00Z")), now))
    }

    @Test
    fun everyOwnerActionHasReadableText() {
        listOf("invite_redeemed", "reset_link_created", "blocked", "unblocked").forEach {
            assertTrue(ownerActionText(it) != "Действие владельца", it)
        }
    }

    @Test
    fun privacyTextIsHonestAboutOwnerAccess() {
        listOf("резервным копиям", "30 дней", "ссылку для входа", "удалить твои данные — нет").forEach {
            assertTrue(PRIVACY_TEXT.contains(it), it)
        }
    }

    @Test
    fun contactLineWithAndWithoutContact() {
        assertTrue(ownerContactText("").endsWith("напиши владельцу приложения."))
        assertTrue(ownerContactText("@owner").endsWith("напиши владельцу: @owner."))
    }
}
