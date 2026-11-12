package ru.nomadbudget.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BankRateWatchDto(
    val id: String,
    val provider: String,
    @SerialName("from_currency") val fromCurrency: String,
    @SerialName("to_currency") val toCurrency: String,
    val category: String,
    val target: Double?,
    @SerialName("good_percentile") val goodPercentile: Int = 85,
    @SerialName("improve_percent") val improvePercent: Double = 0.5,
    @SerialName("trade_open") val tradeOpen: Int = 630,
    @SerialName("trade_close") val tradeClose: Int = 1110,
    @SerialName("digest_at") val digestAt: Int? = null,
    @SerialName("quiet_from") val quietFrom: Int,
    @SerialName("quiet_to") val quietTo: Int,
    val timezone: String,
    val enabled: Boolean,
    @SerialName("link_code") val linkCode: String,
    val state: JsonObject? = null,
)

@Serializable
data class BankRateWatchUpsertDto(
    val provider: String,
    @SerialName("from_currency") val fromCurrency: String,
    @SerialName("to_currency") val toCurrency: String,
    val category: String,
    val target: Double?,
    @SerialName("good_percentile") val goodPercentile: Int,
    @SerialName("improve_percent") val improvePercent: Double,
    @SerialName("trade_open") val tradeOpen: Int,
    @SerialName("trade_close") val tradeClose: Int,
    @SerialName("digest_at") val digestAt: Int?,
    @SerialName("trade_from") val legacyTradeFrom: Int,
    @SerialName("trade_to") val legacyTradeTo: Int,
    @SerialName("digest_hour") val legacyDigestHour: Int?,
    @SerialName("quiet_from") val quietFrom: Int,
    @SerialName("quiet_to") val quietTo: Int,
    val timezone: String,
)

@Serializable
data class BankRateSnapshotDto(
    val category: String,
    val buy: Double,
    val sell: Double?,
    val cbr: Double?,
    @SerialName("taken_at") val takenAt: String,
    @SerialName("in_hours") val inHours: Boolean = false,
)

@Serializable
data class TelegramSubscriberDto(
    val id: String,
    @SerialName("watch_id") val watchId: String,
    @SerialName("chat_id") val chatId: Long,
    val title: String?,
)

@Serializable
data class BotInfoDto(val username: String?)
