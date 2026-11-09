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
    val jumpPercent: Double,
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
        require(jumpPercent > 0.0) { "Скачок должен быть больше нуля" }
        require(digestHour == null || digestHour in HOURS) { "Час сводки: от 0 до 23" }
        require(quietFrom in HOURS && quietTo in HOURS) { "Окно отправки: часы от 0 до 23" }
        require(digestHour == null || inSendWindow(digestHour, quietFrom, quietTo)) { "Час сводки должен быть внутри окна отправки" }
    }

    val sendsAllDay: Boolean get() = quietFrom == quietTo

    val sendFrom: Int get() = quietTo

    val sendTo: Int get() = quietFrom

    companion object {
        const val DEFAULT_PROVIDER = "tbank"
        const val DEFAULT_JUMP_PERCENT = 1.5
        const val DEFAULT_DIGEST_HOUR = 10
        const val DEFAULT_QUIET_FROM = 18
        const val DEFAULT_QUIET_TO = 10
        const val DEFAULT_TIMEZONE = "Europe/Moscow"
        val HOURS: IntRange = 0..23

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
    val jumpPercent: Double = BankRateWatch.DEFAULT_JUMP_PERCENT,
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
) {
    init {
        require(buy > 0.0) { "Курс должен быть положительным" }
    }
}

data class TelegramSubscriber(val id: String, val title: String)

data class BankWatchData(
    val watch: BankRateWatch? = null,
    val history: List<BankRateSnapshot> = emptyList(),
    val latest: Map<BankRateCategory, BankRateSnapshot> = emptyMap(),
    val subscribers: List<TelegramSubscriber> = emptyList(),
    val botUsername: String? = null,
)
