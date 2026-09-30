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
)

@Serializable
data class DebtInsertDto(
    val name: String,
    val currency: String,
    @SerialName("principal_remaining") val principalRemaining: Long,
    @SerialName("monthly_payment") val monthlyPayment: Long,
    @SerialName("rate_percent") val ratePercent: Double?,
    @SerialName("pay_day") val payDay: Int?,
)
