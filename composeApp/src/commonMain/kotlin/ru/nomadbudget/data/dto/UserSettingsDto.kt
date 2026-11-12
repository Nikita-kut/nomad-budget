package ru.nomadbudget.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSettingsDto(
    @SerialName("period_start_day") val periodStartDay: Int,
    @SerialName("income_days") val incomeDays: List<Int>,
    @SerialName("base_currency") val baseCurrency: String,
    @SerialName("cushion_months") val cushionMonths: Int,
    @SerialName("annual_return") val annualReturn: Double,
    @SerialName("onboarded_at") val onboardedAt: String?,
    val currencies: List<String> = emptyList(),
)

@Serializable
data class EnsureCurrencyParams(@SerialName("p_code") val code: String)

@Serializable
data class BaseChangeDto(
    @SerialName("from_currency") val fromCurrency: String,
    @SerialName("to_currency") val toCurrency: String,
    @SerialName("changed_at") val changedAt: String,
    @SerialName("reverted_at") val revertedAt: String? = null,
)

@Serializable
data class ChangeBaseParams(@SerialName("p_code") val code: String)
