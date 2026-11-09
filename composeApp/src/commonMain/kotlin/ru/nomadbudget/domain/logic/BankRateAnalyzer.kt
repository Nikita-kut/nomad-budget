package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.BankRateSnapshot
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class RateRange(val min: Double, val max: Double)

data class BankRatePoint(val takenAt: Instant, val bank: Double, val cbr: Double?)

object BankRateAnalyzer {

    const val RANGE_DAYS = 7

    fun spread(bank: Double, cbr: Double?): Double? = cbr?.takeIf { it > 0.0 }?.let { bank / it - 1.0 }

    fun range(history: List<BankRateSnapshot>, now: Instant, days: Int = RANGE_DAYS): RateRange? {
        val since = now - days.days
        val values = history.filter { it.takenAt >= since }.map { it.buy }
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
