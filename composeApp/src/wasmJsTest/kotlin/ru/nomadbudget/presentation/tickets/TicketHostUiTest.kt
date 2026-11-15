package ru.nomadbudget.presentation.tickets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import ru.nomadbudget.domain.repository.KeyValueStore
import ru.nomadbudget.domain.repository.Ticket
import ru.nomadbudget.domain.repository.TicketDraft
import ru.nomadbudget.domain.repository.TicketRepository
import ru.nomadbudget.presentation.format.AmountMask
import ru.nomadbudget.presentation.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TicketHostUiTest {

    private class FakeTickets : TicketRepository {
        val drafts = mutableListOf<TicketDraft>()
        override suspend fun create(draft: TicketDraft): Long {
            drafts += draft
            return 42
        }
        override suspend fun mine() = emptyList<Ticket>()
    }

    private class MemoryStore : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys(prefix: String) = map.keys.filter { it.startsWith(prefix) }
        override fun clear() = map.clear()
    }

    private val seenMask = mutableListOf<Boolean>()

    private fun host(tickets: FakeTickets, enabled: Boolean = true): @androidx.compose.runtime.Composable () -> Unit = {
        AppTheme {
            TicketHost(
                screen = "Month",
                repository = tickets,
                store = MemoryStore(),
                launcher = TicketLauncher(),
                enabled = enabled,
                buttonPadding = PaddingValues(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    seenMask += AmountMask.hidden
                    Text(if (AmountMask.hidden) "•••" else "12 345")
                }
            }
        }
    }

    @Test
    fun markedTicketIsSentWithScreenshotMarkAndScreenAndAmountsHiddenOnlyWhileCapturing() = runComposeUiTest {
        AmountMask.hidden = false
        val tickets = FakeTickets()
        setContent(host(tickets))
        onNodeWithTag(TicketTags.BUTTON).performClick()
        waitForIdle()
        seenMask.clear()
        onNodeWithTag(TicketTags.MARK_LAYER).performTouchInput { click(Offset(width * 0.25f, height * 0.5f)) }
        waitForIdle()
        assertTrue(seenMask.contains(true), "суммы не скрывались на время снимка")
        assertFalse(AmountMask.hidden, "скрытие сумм осталось после снимка")
        onNodeWithTag(TicketTags.SEND).assertIsNotEnabled()
        onNodeWithTag(TicketTags.BODY).performTextInput("Не сходится остаток")
        onNodeWithTag(TicketTags.SEND).performClick()
        waitForIdle()
        val draft = tickets.drafts.single()
        assertEquals("Не сходится остаток", draft.body)
        assertEquals("Month", draft.screen)
        assertEquals(0.25f, draft.markX!!, 0.02f)
        assertEquals(0.5f, draft.markY!!, 0.02f)
        assertEquals("Month", draft.tech["screen"])
        val png = assertNotNull(draft.screenshotPng)
        assertTrue(png.size > 8 && png[1] == 'P'.code.toByte() && png[2] == 'N'.code.toByte(), "скриншот не PNG")
        onNodeWithTag(TicketTags.RESULT).assertExists()
    }

    @Test
    fun ticketWithoutMarkAndWithoutScreenshot() = runComposeUiTest {
        val tickets = FakeTickets()
        setContent(host(tickets))
        onNodeWithTag(TicketTags.BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(TicketTags.NO_MARK).performClick()
        waitForIdle()
        onNodeWithTag(TicketTags.ATTACH).performClick()
        onNodeWithTag(TicketTags.BODY).performTextInput("Идея")
        onNodeWithTag(TicketTags.SEND).performClick()
        waitForIdle()
        val draft = tickets.drafts.single()
        assertNull(draft.markX)
        assertNull(draft.screenshotPng)
    }

    @Test
    fun disabledHostHasNoButton() = runComposeUiTest {
        setContent(host(FakeTickets(), enabled = false))
        waitForIdle()
        onNodeWithTag(TicketTags.BUTTON).assertDoesNotExist()
    }
}
