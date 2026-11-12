package ru.nomadbudget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankRateWatchTest {

    private fun watch(
        digestAt: Int? = 630,
        quietFrom: Int = 18,
        quietTo: Int = 10,
        goodPercentile: Int = 85,
        improvePercent: Double = 0.5,
        tradeOpen: Int = 630,
        tradeClose: Int = 1110,
    ) = BankRateWatch(
        id = "w", provider = "tbank", from = Currency.RUB, to = Currency.USD, category = BankRateCategory.EXCHANGE,
        target = null, goodPercentile = goodPercentile, improvePercent = improvePercent, tradeOpen = tradeOpen, tradeClose = tradeClose,
        digestAt = digestAt, quietFrom = quietFrom, quietTo = quietTo,
        timezone = BankRateWatch.DEFAULT_TIMEZONE, enabled = true, linkCode = "code",
    )

    @Test
    fun defaultWindow_isTenToEighteenInDefaultZone() {
        val w = watch()
        assertEquals(10, w.sendFrom)
        assertEquals(18, w.sendTo)
        assertEquals("Europe/Moscow", w.timezone)
        assertFalse(w.sendsAllDay)
        assertTrue(watch(digestAt = null, quietFrom = 9, quietTo = 9).sendsAllDay)
    }

    @Test
    fun inSendWindow_matchesWorkerQuietHours() {
        assertTrue(BankRateWatch.inSendWindow(600, quietFrom = 18, quietTo = 10))
        assertTrue(BankRateWatch.inSendWindow(1079, quietFrom = 18, quietTo = 10))
        assertFalse(BankRateWatch.inSendWindow(1080, quietFrom = 18, quietTo = 10))
        assertFalse(BankRateWatch.inSendWindow(599, quietFrom = 18, quietTo = 10))
        assertFalse(BankRateWatch.inSendWindow(0, quietFrom = 18, quietTo = 10))
        assertTrue(BankRateWatch.inSendWindow(1380, quietFrom = 8, quietTo = 22))
        assertFalse(BankRateWatch.inSendWindow(720, quietFrom = 8, quietTo = 22))
        assertTrue(BankRateWatch.inSendWindow(180, quietFrom = 9, quietTo = 9))
    }

    @Test
    fun digestAt_mustBeInsideSendWindow() {
        watch(digestAt = 1065)
        assertFailsWith<IllegalArgumentException> { watch(digestAt = 585) }
        assertFailsWith<IllegalArgumentException> { watch(digestAt = 1080) }
        assertFailsWith<IllegalArgumentException> { watch(digestAt = 1440) }
        watch(digestAt = null)
    }

    @Test
    fun defaults_matchWorker() {
        val draft = BankRateWatchDraft(Currency.RUB, Currency.USD)
        assertEquals(85, draft.goodPercentile)
        assertEquals(0.5, draft.improvePercent)
        assertEquals(630, draft.tradeOpen)
        assertEquals(1110, draft.tradeClose)
        assertEquals(630, draft.digestAt)
        assertEquals(null, draft.target)
    }

    @Test
    fun tradeHours_startBeforeEndWithinDay() {
        watch(tradeOpen = 0, tradeClose = 1440)
        assertTrue(BankRateWatch.validTradeMinutes(630, 1110))
        assertFalse(BankRateWatch.validTradeMinutes(1110, 1110))
        assertFalse(BankRateWatch.validTradeMinutes(1200, 600))
        assertFalse(BankRateWatch.validTradeMinutes(600, 1441))
        assertFailsWith<IllegalArgumentException> { watch(tradeOpen = 1110, tradeClose = 630) }
    }

    @Test
    fun legacyHours_coverTheMinuteWindow() {
        assertEquals(10, BankRateWatch.legacyHour(630))
        assertEquals(19, BankRateWatch.legacyEndHour(1110))
        assertEquals(18, BankRateWatch.legacyEndHour(1080))
    }

    @Test
    fun strictnessAndRepeat_areValidated() {
        watch(goodPercentile = 75)
        watch(goodPercentile = 90)
        assertFailsWith<IllegalArgumentException> { watch(goodPercentile = 49) }
        assertFailsWith<IllegalArgumentException> { watch(goodPercentile = 100) }
        assertFailsWith<IllegalArgumentException> { watch(improvePercent = 0.0) }
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
