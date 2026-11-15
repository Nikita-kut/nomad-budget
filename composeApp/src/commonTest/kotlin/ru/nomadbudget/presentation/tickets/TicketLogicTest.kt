package ru.nomadbudget.presentation.tickets

import androidx.compose.ui.geometry.Offset
import ru.nomadbudget.presentation.home.ticketScreenName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TicketLogicTest {
    @Test
    fun keptAreaStaysInsideTheImage() {
        listOf(Offset(0f, 0f), Offset(1f, 1f), Offset(0.5f, 0.5f), Offset(0.99f, 0.01f)).forEach { mark ->
            val area = keptArea(mark, 1000, 800)
            assertTrue(area.left >= 0f && area.top >= 0f && area.right <= 1000f && area.bottom <= 800f, "$mark → $area")
        }
    }

    @Test
    fun screenNameIsShortAndSafe() {
        assertEquals("Month", ticketScreenName("ru.nomadbudget.presentation.home.MonthRoute"))
        assertEquals("Settings", ticketScreenName("ru.nomadbudget.presentation.home.SettingsRoute"))
        assertNull(ticketScreenName(null))
    }

    @Test
    fun techInfoHasNoMoney() {
        val tech = techInfo("Month", androidx.compose.ui.unit.IntSize(1280, 800))
        assertEquals(setOf("platform", "version", "env", "screen", "window"), tech.keys)
        assertEquals("1280x800", tech["window"])
    }

    @Test
    fun maskedInputHidesDigitsWithValidOffsets() {
        val t = ru.nomadbudget.presentation.format.MaskedInput.filter(androidx.compose.ui.text.AnnotatedString("123456,78"))
        assertEquals("•••", t.text.text)
        (0..9).forEach { o -> assertTrue(t.offsetMapping.originalToTransformed(o) in 0..3) }
        (0..3).forEach { o -> assertTrue(t.offsetMapping.transformedToOriginal(o) in 0..9) }
    }
}
