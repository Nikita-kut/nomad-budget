package ru.nomadbudget.domain.model

import kotlin.time.Instant

enum class BankRateCategory(val id: String) {
    EXCHANGE("DebitCardsTransfers"),
    CARD("DebitCardsOperations");

    companion object {
        fun fromId(id: String): BankRateCategory = entries.firstOrNull { it.id == id } ?: EXCHANGE
    }
}

data class BankRateWatch(
    val id: String,
    val provider: String,
    val from: Currency,
    val to: Currency,
    val category: BankRateCategory,
    val target: Double?,
    val goodPercentile: Int,
    val improvePercent: Double,
    val tradeOpen: Int,
    val tradeClose: Int,
    val digestAt: Int?,
    val quietFrom: Int,
    val quietTo: Int,
    val timezone: String,
    val enabled: Boolean,
    val linkCode: String,
    val lastRate: Double? = null,
    val lastCheckedAt: Instant? = null,
) {
    init {
        require(from != to) { "Валюты пары должны различаться" }
        require(target == null || target > 0.0) { "Цель должна быть больше нуля" }
        require(goodPercentile in PERCENTILES) { "Строгость: от 50 до 99" }
        require(improvePercent > 0.0) { "Повтор при улучшении должен быть больше нуля" }
        require(validTradeMinutes(tradeOpen, tradeClose)) { "Часы биржи: начало раньше конца, в пределах суток" }
        require(digestAt == null || digestAt in MINUTES) { "Время сводки: в пределах суток" }
        require(quietFrom in HOURS && quietTo in HOURS) { "Окно отправки: часы от 0 до 23" }
        require(digestAt == null || inSendWindow(digestAt, quietFrom, quietTo)) { "Время сводки должно быть внутри окна отправки" }
    }

    val sendsAllDay: Boolean get() = quietFrom == quietTo

    val sendFrom: Int get() = quietTo

    val sendTo: Int get() = quietFrom

    companion object {
        const val DEFAULT_PROVIDER = "tbank"
        const val DEFAULT_GOOD_PERCENTILE = 85
        const val DEFAULT_IMPROVE_PERCENT = 0.5
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
        const val TIME_STEP_MINUTES = 15
        const val DEFAULT_TRADE_OPEN = 10 * MINUTES_PER_HOUR + 30
        const val DEFAULT_TRADE_CLOSE = 18 * MINUTES_PER_HOUR + 30
        const val DEFAULT_DIGEST_AT = DEFAULT_TRADE_OPEN
        const val DEFAULT_QUIET_FROM = 18
        const val DEFAULT_QUIET_TO = 10
        const val DEFAULT_TIMEZONE = "Europe/Moscow"
        val HOURS: IntRange = 0..23
        val MINUTES: IntRange = 0 until MINUTES_PER_DAY
        val TIME_STEPS: List<Int> = MINUTES.step(TIME_STEP_MINUTES).toList()
        val CLOSE_STEPS: List<Int> = (TIME_STEP_MINUTES..MINUTES_PER_DAY step TIME_STEP_MINUTES).toList()
        val PERCENTILES: IntRange = 50..99

        fun validTradeMinutes(open: Int, close: Int): Boolean = open in MINUTES && close in 1..MINUTES_PER_DAY && open < close

        fun inSendWindow(minute: Int, quietFrom: Int, quietTo: Int): Boolean {
            val start = quietTo * MINUTES_PER_HOUR
            val end = quietFrom * MINUTES_PER_HOUR
            return when {
                quietFrom == quietTo -> true
                quietTo < quietFrom -> minute >= start && minute < end
                else -> minute >= start || minute < end
            }
        }

        fun legacyHour(minute: Int): Int = minute / MINUTES_PER_HOUR

        fun legacyEndHour(minute: Int): Int = (minute + MINUTES_PER_HOUR - 1) / MINUTES_PER_HOUR
    }
}

data class BankRateWatchDraft(
    val from: Currency,
    val to: Currency,
    val category: BankRateCategory = BankRateCategory.EXCHANGE,
    val target: Double? = null,
    val goodPercentile: Int = BankRateWatch.DEFAULT_GOOD_PERCENTILE,
    val improvePercent: Double = BankRateWatch.DEFAULT_IMPROVE_PERCENT,
    val tradeOpen: Int = BankRateWatch.DEFAULT_TRADE_OPEN,
    val tradeClose: Int = BankRateWatch.DEFAULT_TRADE_CLOSE,
    val digestAt: Int? = BankRateWatch.DEFAULT_DIGEST_AT,
    val quietFrom: Int = BankRateWatch.DEFAULT_QUIET_FROM,
    val quietTo: Int = BankRateWatch.DEFAULT_QUIET_TO,
    val timezone: String = BankRateWatch.DEFAULT_TIMEZONE,
    val provider: String = BankRateWatch.DEFAULT_PROVIDER,
)

data class BankRateSnapshot(
    val category: BankRateCategory,
    val buy: Double,
    val sell: Double?,
    val cbr: Double?,
    val takenAt: Instant,
    val inHours: Boolean = true,
) {
    init {
        require(buy > 0.0) { "Курс должен быть положительным" }
    }
}

data class TelegramSubscriber(val id: String, val title: String)

data class BankWatchData(
    val watch: BankRateWatch? = null,
    val history: List<BankRateSnapshot> = emptyList(),
    val week: List<BankRateSnapshot> = emptyList(),
    val latest: Map<BankRateCategory, BankRateSnapshot> = emptyMap(),
    val subscribers: List<TelegramSubscriber> = emptyList(),
    val botUsername: String? = null,
)
