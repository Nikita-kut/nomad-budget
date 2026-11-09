package ru.nomadbudget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankRateWatchTest {

    private fun watch(digestHour: Int? = 10, quietFrom: Int = 18, quietTo: Int = 10) = BankRateWatch(
        id = "w", provider = "tbank", from = Currency.RUB, to = Currency.USD, category = BankRateCategory.EXCHANGE,
        target = null, jumpPercent = 1.5, digestHour = digestHour, quietFrom = quietFrom, quietTo = quietTo,
        timezone = BankRateWatch.DEFAULT_TIMEZONE, enabled = true, linkCode = "code",
    )

    @Test
    fun defaultWindow_isTenToEighteenInDefaultZone() {
        val w = watch()
        assertEquals(10, w.sendFrom)
        assertEquals(18, w.sendTo)
        assertEquals("Europe/Moscow", w.timezone)
        assertFalse(w.sendsAllDay)
        assertTrue(watch(digestHour = null, quietFrom = 9, quietTo = 9).sendsAllDay)
    }

    @Test
    fun inSendWindow_matchesWorkerQuietHours() {
        assertTrue(BankRateWatch.inSendWindow(10, quietFrom = 18, quietTo = 10))
        assertTrue(BankRateWatch.inSendWindow(17, quietFrom = 18, quietTo = 10))
        assertFalse(BankRateWatch.inSendWindow(18, quietFrom = 18, quietTo = 10))
        assertFalse(BankRateWatch.inSendWindow(9, quietFrom = 18, quietTo = 10))
        assertFalse(BankRateWatch.inSendWindow(0, quietFrom = 18, quietTo = 10))
        assertTrue(BankRateWatch.inSendWindow(23, quietFrom = 8, quietTo = 22))
        assertFalse(BankRateWatch.inSendWindow(12, quietFrom = 8, quietTo = 22))
        assertTrue(BankRateWatch.inSendWindow(3, quietFrom = 9, quietTo = 9))
    }

    @Test
    fun digestHour_mustBeInsideSendWindow() {
        watch(digestHour = 17)
        assertFailsWith<IllegalArgumentException> { watch(digestHour = 9) }
        assertFailsWith<IllegalArgumentException> { watch(digestHour = 18) }
        watch(digestHour = null)
    }

    @Test
    fun pair_mustDiffer() {
        assertFailsWith<IllegalArgumentException> { watch().copy(to = Currency.RUB) }
    }

    @Test
    fun categoryFromId_fallsBackToExchange() {
        assertEquals(BankRateCategory.CARD, BankRateCategory.fromId("DebitCardsOperations"))
        assertEquals(BankRateCategory.EXCHANGE, BankRateCategory.fromId("Unknown"))
    }
}
