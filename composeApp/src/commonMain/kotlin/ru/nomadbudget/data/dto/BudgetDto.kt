package ru.nomadbudget.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PeriodDto(
    val id: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val title: String,
    @SerialName("savings_target") val savingsTarget: Long? = null,
)

@Serializable
data class PeriodInsertDto(
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val title: String,
)

@Serializable
data class BudgetLineDto(
    val id: String,
    @SerialName("period_id") val periodId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("subcategory_id") val subcategoryId: String? = null,
    @SerialName("planned_base") val plannedBase: Long,
)

@Serializable
data class BudgetLineUpsertDto(
    @SerialName("period_id") val periodId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("subcategory_id") val subcategoryId: String?,
    @SerialName("planned_base") val plannedBase: Long,
)

@Serializable
data class ExchangeRateDto(
    @SerialName("rate_date") val rateDate: String,
    val base: String,
    val quote: String,
    val rate: Double,
    val source: String,
    @SerialName("fetched_at") val fetchedAt: String? = null,
)

@Serializable
data class BalanceCheckDto(
    val id: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("check_date") val checkDate: String,
    @SerialName("actual_balance") val actualBalance: Long,
    @SerialName("computed_balance") val computedBalance: Long,
    val note: String? = null,
)

@Serializable
data class BalanceCheckInsertDto(
    @SerialName("account_id") val accountId: String,
    @SerialName("check_date") val checkDate: String,
    @SerialName("actual_balance") val actualBalance: Long,
    @SerialName("computed_balance") val computedBalance: Long,
    val note: String? = null,
)

@Serializable
data class PeriodStartDto(
    @SerialName("start_date") val startDate: String,
)

@Serializable
data class BudgetLineWithPeriodDto(
    @SerialName("category_id") val categoryId: String,
    @SerialName("subcategory_id") val subcategoryId: String? = null,
    @SerialName("planned_base") val plannedBase: Long,
    val periods: PeriodStartDto,
)
