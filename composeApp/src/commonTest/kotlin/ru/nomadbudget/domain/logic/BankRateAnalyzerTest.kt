package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.BankRateCategory
import ru.nomadbudget.domain.model.BankRateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class BankRateAnalyzerTest {

    private val now = Instant.parse("2026-11-10T12:00:00Z")

    private fun snapshot(buy: Double, ago: kotlin.time.Duration, cbr: Double? = null) =
        BankRateSnapshot(BankRateCategory.EXCHANGE, buy, null, cbr, now - ago)

    @Test
    fun spread_isBankOverCbrMinusOne() {
        assertEquals(-0.05, BankRateAnalyzer.spread(95.0, 100.0)!!, absoluteTolerance = 1e-12)
        assertEquals(0.1, BankRateAnalyzer.spread(1.1, 1.0)!!, absoluteTolerance = 1e-12)
        assertNull(BankRateAnalyzer.spread(95.0, null))
        assertNull(BankRateAnalyzer.spread(95.0, 0.0))
    }

    @Test
    fun range_takesOnlyLastSevenDays() {
        val history = listOf(snapshot(90.0, 8.days), snapshot(95.0, 6.days), snapshot(100.0, 1.hours), snapshot(97.0, 3.days))
        assertEquals(RateRange(95.0, 100.0), BankRateAnalyzer.range(history, now))
        assertNull(BankRateAnalyzer.range(listOf(snapshot(90.0, 8.days)), now))
        assertNull(BankRateAnalyzer.range(emptyList(), now))
    }

    @Test
    fun suggestedTarget_isSevenDayMax() {
        val history = listOf(snapshot(95.0, 6.days), snapshot(100.0, 1.hours), snapshot(120.0, 9.days))
        assertEquals(100.0, BankRateAnalyzer.suggestedTarget(history, now))
        assertNull(BankRateAnalyzer.suggestedTarget(emptyList(), now))
    }

    @Test
    fun progress_goesFromWeekMinToTarget() {
        val range = RateRange(90.0, 100.0)
        assertEquals(0.5f, BankRateAnalyzer.progress(95.0, 100.0, range))
        assertEquals(1f, BankRateAnalyzer.progress(100.0, 100.0, range))
        assertEquals(1f, BankRateAnalyzer.progress(101.0, 100.0, range))
        assertEquals(0f, BankRateAnalyzer.progress(90.0, 100.0, range))
        assertEquals(1f, BankRateAnalyzer.progress(95.0, 80.0, range))
        assertEquals(0f, BankRateAnalyzer.progress(85.0, 100.0, range))
        assertEquals(0f, BankRateAnalyzer.progress(95.0, 100.0, null))
        assertNull(BankRateAnalyzer.progress(95.0, null, range))
    }

    @Test
    fun points_sortedByTime_andCbrGapsFilledWithLastKnown() {
        val history = listOf(snapshot(100.0, 1.hours, cbr = null), snapshot(90.0, 3.hours, cbr = 95.0), snapshot(92.0, 2.hours, cbr = null))
        val points = BankRateAnalyzer.points(history)
        assertEquals(listOf(90.0, 92.0, 100.0), points.map { it.bank })
        assertEquals(listOf(95.0, 95.0, 95.0), BankRateAnalyzer.lastKnownCbr(points))
        assertNull(BankRateAnalyzer.lastKnownCbr(listOf(BankRatePoint(now, 1.0, null))))
    }

    @Test
    fun lastKnownCbr_startsFromFirstAvailableValue() {
        val points = listOf(BankRatePoint(now - 2.hours, 1.0, null), BankRatePoint(now - 1.hours, 1.0, 2.0), BankRatePoint(now, 1.0, 3.0))
        assertEquals(listOf(2.0, 2.0, 3.0), BankRateAnalyzer.lastKnownCbr(points))
    }
}
