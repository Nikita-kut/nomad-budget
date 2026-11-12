package ru.nomadbudget.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DebtDto(
    val id: String,
    val name: String,
    val currency: String,
    @SerialName("principal_remaining") val principalRemaining: Long,
    @SerialName("monthly_payment") val monthlyPayment: Long,
    @SerialName("rate_percent") val ratePercent: Double? = null,
    @SerialName("pay_day") val payDay: Int? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("extra_payment") val extraPayment: Long = 0L,
    @SerialName("opened_on") val openedOn: String? = null,
    @SerialName("close_reason") val closeReason: String? = null,
    @SerialName("written_off") val writtenOff: Long = 0L,
)

@Serializable
data class CloseDebtParams(
    @SerialName("p_id") val id: String,
    @SerialName("p_reason") val reason: String,
    @SerialName("p_on") val on: String,
    @SerialName("p_write_off") val writeOff: Boolean,
)

@Serializable
data class ReopenDebtParams(@SerialName("p_id") val id: String)

@Serializable
data class DebtInsertDto(
    val name: String,
    val currency: String,
    @SerialName("principal_remaining") val principalRemaining: Long,
    @SerialName("monthly_payment") val monthlyPayment: Long,
    @SerialName("rate_percent") val ratePercent: Double?,
    @SerialName("pay_day") val payDay: Int?,
    @SerialName("extra_payment") val extraPayment: Long,
)
