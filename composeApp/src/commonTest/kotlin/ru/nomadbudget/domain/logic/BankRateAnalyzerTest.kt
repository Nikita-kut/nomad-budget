package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.BankRateCategory
import ru.nomadbudget.domain.model.BankRateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class BankRateAnalyzerTest {

    private val now = Instant.parse("2026-11-10T12:00:00Z")

    private fun snapshot(buy: Double, ago: kotlin.time.Duration, cbr: Double? = null, inHours: Boolean = true) =
        BankRateSnapshot(BankRateCategory.EXCHANGE, buy, null, cbr, now - ago, inHours)

    private fun week(count: Int = 100, cbr: (Double) -> Double? = { null }) =
        (1..count).map { snapshot(it.toDouble(), (it * 15).minutes, cbr(it.toDouble())) }

    private fun verdict(buy: Double, week: List<BankRateSnapshot>, cbr: Double? = null, inHours: Boolean = true, percentile: Int = 85) =
        BankRateAnalyzer.assess(snapshot(buy, 0.hours, cbr, inHours), week, now, percentile).verdict

    @Test
    fun verdict_goodIsTopFifteenPercent_badIsBottom() {
        assertEquals(RateVerdict.GOOD, verdict(85.5, week()))
        assertEquals(RateVerdict.NORMAL, verdict(84.5, week()))
        assertEquals(RateVerdict.BAD, verdict(15.5, week()))
        assertEquals(RateVerdict.NORMAL, verdict(16.5, week()))
        assertEquals(RateVerdict.GOOD, verdict(75.5, week(), percentile = 75))
        assertEquals(RateVerdict.NORMAL, verdict(85.5, week(), percentile = 90))
    }

    @Test
    fun verdict_flatWeekIsNormal() {
        val flat = (1..100).map { snapshot(5.0, (it * 15).minutes) }
        assertEquals(RateVerdict.NORMAL, verdict(5.0, flat))
    }

    @Test
    fun verdict_goodNeedsSpreadNarrowerThanWeekMedian() {
        val history = week { it / 0.97 }
        assertEquals(-0.03, BankRateAnalyzer.medianSpread(history)!!, absoluteTolerance = 1e-9)
        assertEquals(RateVerdict.NORMAL, verdict(95.0, history, cbr = 95.0 / 0.95))
        assertEquals(RateVerdict.GOOD, verdict(95.0, history, cbr = 95.0 / 0.98))
        assertEquals(RateVerdict.GOOD, verdict(95.0, history, cbr = null))
        assertEquals(RateVerdict.BAD, verdict(5.0, history, cbr = 5.0 / 0.99))
    }

    @Test
    fun verdict_usesOnlyInHoursSnapshotsOfLastSevenDays() {
        val night = (1..100).map { snapshot(1000.0 + it, (it * 15).minutes, inHours = false) }
        val old = (1..100).map { snapshot(1000.0 + it, 8.days + it.minutes) }
        val assessment = BankRateAnalyzer.assess(snapshot(95.0, 0.hours), week() + night + old, now)
        assertEquals(100, assessment.samples)
        assertEquals(RateVerdict.GOOD, assessment.verdict)
        assertEquals(0.94, assessment.below, absoluteTolerance = 1e-9)
        assertEquals(0.05, assessment.above, absoluteTolerance = 1e-9)
    }

    @Test
    fun verdict_warmupUntilTwoTradingDaysOfChecks() {
        assertEquals(72, BankRateAnalyzer.warmupSamples(600, 1140))
        assertEquals(64, BankRateAnalyzer.warmupSamples(630, 1110))
        assertEquals(RateVerdict.WARMUP, verdict(99.0, week(63)))
        assertEquals(RateVerdict.GOOD, verdict(99.0, week(64)))
        assertEquals(71, BankRateAnalyzer.assess(snapshot(99.0, 0.hours), week(71), now).samples)
    }

    @Test
    fun verdict_closedWhenLatestSnapshotIsOutsideTradingHours() {
        assertEquals(RateVerdict.CLOSED, verdict(99.0, week(), inHours = false))
        assertEquals(RateVerdict.CLOSED, verdict(1.0, week(10), inHours = false))
    }

    @Test
    fun median_andShares() {
        assertEquals(2.0, BankRateAnalyzer.median(listOf(3.0, 1.0, 2.0)))
        assertEquals(2.5, BankRateAnalyzer.median(listOf(4.0, 1.0, 2.0, 3.0)))
        assertNull(BankRateAnalyzer.median(emptyList()))
        assertEquals(0.5, BankRateAnalyzer.shareBelow(listOf(1.0, 2.0, 3.0, 4.0), 2.5))
        assertEquals(0.25, BankRateAnalyzer.shareAbove(listOf(1.0, 2.0, 3.0, 4.0), 3.0))
        assertEquals(0.0, BankRateAnalyzer.shareBelow(emptyList(), 3.0))
    }

    @Test
    fun inStandardHours_weekdaysHalfPastTenToHalfPastSixExchangeTime() {
        assertTrue(BankRateAnalyzer.inStandardHours(Instant.parse("2026-11-10T07:30:00Z")))
        assertFalse(BankRateAnalyzer.inStandardHours(Instant.parse("2026-11-10T07:15:00Z")))
        assertTrue(BankRateAnalyzer.inStandardHours(Instant.parse("2026-11-10T15:29:00Z")))
        assertFalse(BankRateAnalyzer.inStandardHours(Instant.parse("2026-11-10T15:30:00Z")))
        assertFalse(BankRateAnalyzer.inStandardHours(Instant.parse("2026-11-14T09:00:00Z")))
        assertFalse(BankRateAnalyzer.inStandardHours(Instant.parse("2026-11-15T09:00:00Z")))
    }

    @Test
    fun range_ignoresSnapshotsOutsideTradingHours() {
        val history = listOf(snapshot(95.0, 6.days), snapshot(100.0, 1.hours), snapshot(80.0, 2.hours, inHours = false), snapshot(120.0, 3.hours, inHours = false))
        assertEquals(RateRange(95.0, 100.0), BankRateAnalyzer.range(history, now))
    }

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
