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
    @SerialName("jump_percent") val jumpPercent: Double,
    @SerialName("digest_hour") val digestHour: Int?,
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
    @SerialName("jump_percent") val jumpPercent: Double,
    @SerialName("digest_hour") val digestHour: Int?,
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
