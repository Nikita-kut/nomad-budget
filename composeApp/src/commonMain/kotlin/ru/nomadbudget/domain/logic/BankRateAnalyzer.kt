package ru.nomadbudget.domain.logic

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
import ru.nomadbudget.domain.model.BankRateSnapshot
import ru.nomadbudget.domain.model.BankRateWatch
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class RateRange(val min: Double, val max: Double)

data class BankRatePoint(val takenAt: Instant, val bank: Double, val cbr: Double?)

enum class RateVerdict { GOOD, NORMAL, BAD, WARMUP, CLOSED }

data class RateAssessment(
    val verdict: RateVerdict,
    val samples: Int,
    val below: Double,
    val above: Double,
    val spread: Double?,
    val medianSpread: Double?,
)

object BankRateAnalyzer {

    const val RANGE_DAYS = 7
    const val WARMUP_TRADING_DAYS = 2
    private const val SHARE_EPSILON = 1e-9
    private val EXCHANGE_ZONE = FixedOffsetTimeZone(UtcOffset(hours = 3))

    fun spread(bank: Double, cbr: Double?): Double? = cbr?.takeIf { it > 0.0 }?.let { bank / it - 1.0 }

    fun inHoursWeek(snapshots: List<BankRateSnapshot>, now: Instant, days: Int = RANGE_DAYS): List<BankRateSnapshot> {
        val since = now - days.days
        return snapshots.filter { it.inHours && it.takenAt >= since }
    }

    fun range(history: List<BankRateSnapshot>, now: Instant, days: Int = RANGE_DAYS): RateRange? {
        val values = inHoursWeek(history, now, days).map { it.buy }
        if (values.isEmpty()) return null
        return RateRange(values.min(), values.max())
    }

    fun suggestedTarget(history: List<BankRateSnapshot>, now: Instant): Double? = range(history, now)?.max

    fun progress(current: Double, target: Double?, range: RateRange?): Float? {
        if (target == null) return null
        val low = range?.min?.coerceAtMost(current) ?: current
        if (target <= low) return 1f
        return ((current - low) / (target - low)).toFloat().coerceIn(0f, 1f)
    }

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    fun shareBelow(values: List<Double>, rate: Double): Double = if (values.isEmpty()) 0.0 else values.count { it < rate }.toDouble() / values.size

    fun shareAbove(values: List<Double>, rate: Double): Double = if (values.isEmpty()) 0.0 else values.count { it > rate }.toDouble() / values.size

    fun medianSpread(week: List<BankRateSnapshot>): Double? = median(week.mapNotNull { spread(it.buy, it.cbr) })

    fun warmupSamples(tradeOpen: Int, tradeClose: Int): Int =
        WARMUP_TRADING_DAYS * ((tradeClose - tradeOpen) / BankRateWatch.TIME_STEP_MINUTES).coerceAtLeast(1)

    fun assess(
        current: BankRateSnapshot,
        week: List<BankRateSnapshot>,
        now: Instant,
        goodPercentile: Int = BankRateWatch.DEFAULT_GOOD_PERCENTILE,
        tradeOpen: Int = BankRateWatch.DEFAULT_TRADE_OPEN,
        tradeClose: Int = BankRateWatch.DEFAULT_TRADE_CLOSE,
    ): RateAssessment {
        val samples = inHoursWeek(week, now)
        val values = samples.map { it.buy }
        val below = shareBelow(values, current.buy)
        val above = shareAbove(values, current.buy)
        val weekSpread = medianSpread(samples)
        val nowSpread = spread(current.buy, current.cbr)
        val threshold = goodPercentile / 100.0
        val spreadOk = weekSpread == null || nowSpread == null || nowSpread > weekSpread
        val verdict = when {
            !current.inHours -> RateVerdict.CLOSED
            values.size < warmupSamples(tradeOpen, tradeClose) -> RateVerdict.WARMUP
            below >= threshold - SHARE_EPSILON && spreadOk -> RateVerdict.GOOD
            above >= threshold - SHARE_EPSILON -> RateVerdict.BAD
            else -> RateVerdict.NORMAL
        }
        return RateAssessment(verdict, values.size, below, above, nowSpread, weekSpread)
    }

    fun inStandardHours(at: Instant, tradeOpen: Int = BankRateWatch.DEFAULT_TRADE_OPEN, tradeClose: Int = BankRateWatch.DEFAULT_TRADE_CLOSE): Boolean {
        val local = at.toLocalDateTime(EXCHANGE_ZONE)
        val weekday = local.dayOfWeek != DayOfWeek.SATURDAY && local.dayOfWeek != DayOfWeek.SUNDAY
        val minute = local.hour * BankRateWatch.MINUTES_PER_HOUR + local.minute
        return weekday && minute >= tradeOpen && minute < tradeClose
    }

    fun points(history: List<BankRateSnapshot>): List<BankRatePoint> =
        history.sortedBy { it.takenAt }.map { BankRatePoint(it.takenAt, it.buy, it.cbr) }

    fun lastKnownCbr(points: List<BankRatePoint>): List<Double>? {
        if (points.none { it.cbr != null }) return null
        var last = points.firstNotNullOf { it.cbr }
        return points.map { point ->
            point.cbr?.let { last = it }
            last
        }
    }
}
