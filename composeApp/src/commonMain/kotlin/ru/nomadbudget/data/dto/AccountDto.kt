package ru.nomadbudget.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val currency: String,
    val kind: String,
    @SerialName("is_savings") val isSavings: Boolean,
    @SerialName("opening_balance") val openingBalance: Long,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("is_fund") val isFund: Boolean = false,
    @SerialName("fund_monthly") val fundMonthly: Long? = null,
    @SerialName("fund_goal") val fundGoal: Long? = null,
    @SerialName("fund_day") val fundDay: Int? = null,
)

@Serializable
data class AccountInsertDto(
    val name: String,
    val currency: String,
    val kind: String,
    @SerialName("is_savings") val isSavings: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("is_fund") val isFund: Boolean = false,
)

@Serializable
data class AccountFundDto(
    @SerialName("fund_monthly") val fundMonthly: Long?,
    @SerialName("fund_goal") val fundGoal: Long?,
    @SerialName("fund_day") val fundDay: Int?,
)

@Serializable
data class AccountRoleDto(
    @SerialName("is_savings") val isSavings: Boolean,
    @SerialName("is_fund") val isFund: Boolean,
)
