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
    val tradeFrom: Int,
    val tradeTo: Int,
    val digestHour: Int?,
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
        require(validTradeHours(tradeFrom, tradeTo)) { "Часы биржи: начало раньше конца, в пределах суток" }
        require(digestHour == null || digestHour in HOURS) { "Час сводки: от 0 до 23" }
        require(quietFrom in HOURS && quietTo in HOURS) { "Окно отправки: часы от 0 до 23" }
        require(digestHour == null || inSendWindow(digestHour, quietFrom, quietTo)) { "Час сводки должен быть внутри окна отправки" }
    }

    val sendsAllDay: Boolean get() = quietFrom == quietTo

    val sendFrom: Int get() = quietTo

    val sendTo: Int get() = quietFrom

    companion object {
        const val DEFAULT_PROVIDER = "tbank"
        const val DEFAULT_GOOD_PERCENTILE = 85
        const val DEFAULT_IMPROVE_PERCENT = 0.5
        const val DEFAULT_TRADE_FROM = 10
        const val DEFAULT_TRADE_TO = 19
        const val DEFAULT_DIGEST_HOUR = 10
        const val DEFAULT_QUIET_FROM = 18
        const val DEFAULT_QUIET_TO = 10
        const val DEFAULT_TIMEZONE = "Europe/Moscow"
        val HOURS: IntRange = 0..23
        val TRADE_END_HOURS: IntRange = 1..24
        val PERCENTILES: IntRange = 50..99

        fun validTradeHours(from: Int, to: Int): Boolean = from in HOURS && to in TRADE_END_HOURS && from < to

        fun inSendWindow(hour: Int, quietFrom: Int, quietTo: Int): Boolean = when {
            quietFrom == quietTo -> true
            quietTo < quietFrom -> hour >= quietTo && hour < quietFrom
            else -> hour >= quietTo || hour < quietFrom
        }
    }
}

data class BankRateWatchDraft(
    val from: Currency,
    val to: Currency,
    val category: BankRateCategory = BankRateCategory.EXCHANGE,
    val target: Double? = null,
    val goodPercentile: Int = BankRateWatch.DEFAULT_GOOD_PERCENTILE,
    val improvePercent: Double = BankRateWatch.DEFAULT_IMPROVE_PERCENT,
    val tradeFrom: Int = BankRateWatch.DEFAULT_TRADE_FROM,
    val tradeTo: Int = BankRateWatch.DEFAULT_TRADE_TO,
    val digestHour: Int? = BankRateWatch.DEFAULT_DIGEST_HOUR,
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
