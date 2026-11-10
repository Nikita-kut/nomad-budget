package ru.nomadbudget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankRateWatchTest {

    private fun watch(
        digestHour: Int? = 10,
        quietFrom: Int = 18,
        quietTo: Int = 10,
        goodPercentile: Int = 85,
        improvePercent: Double = 0.5,
        tradeFrom: Int = 10,
        tradeTo: Int = 19,
    ) = BankRateWatch(
        id = "w", provider = "tbank", from = Currency.RUB, to = Currency.USD, category = BankRateCategory.EXCHANGE,
        target = null, goodPercentile = goodPercentile, improvePercent = improvePercent, tradeFrom = tradeFrom, tradeTo = tradeTo,
        digestHour = digestHour, quietFrom = quietFrom, quietTo = quietTo,
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
    fun defaults_matchWorker() {
        val draft = BankRateWatchDraft(Currency.RUB, Currency.USD)
        assertEquals(85, draft.goodPercentile)
        assertEquals(0.5, draft.improvePercent)
        assertEquals(10, draft.tradeFrom)
        assertEquals(19, draft.tradeTo)
        assertEquals(null, draft.target)
    }

    @Test
    fun tradeHours_startBeforeEndWithinDay() {
        watch(tradeFrom = 0, tradeTo = 24)
        assertTrue(BankRateWatch.validTradeHours(10, 19))
        assertFalse(BankRateWatch.validTradeHours(19, 19))
        assertFalse(BankRateWatch.validTradeHours(20, 10))
        assertFalse(BankRateWatch.validTradeHours(10, 25))
        assertFailsWith<IllegalArgumentException> { watch(tradeFrom = 19, tradeTo = 10) }
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
