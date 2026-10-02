package ru.nomadbudget.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("tx_date") val txDate: String,
    val type: String,
    @SerialName("account_id") val accountId: String,
    val amount: Long,
    @SerialName("counter_account_id") val counterAccountId: String? = null,
    @SerialName("counter_amount") val counterAmount: Long? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("subcategory_id") val subcategoryId: String? = null,
    @SerialName("debt_id") val debtId: String? = null,
    val note: String? = null,
    @SerialName("rate_to_base") val rateToBase: Double,
    @SerialName("amount_base") val amountBase: Long,
    @SerialName("rate_source") val rateSource: String,
    val source: String,
)

@Serializable
data class TransactionInsertDto(
    val id: String? = null,
    @SerialName("tx_date") val txDate: String,
    val type: String,
    @SerialName("account_id") val accountId: String,
    val amount: Long,
    @SerialName("counter_account_id") val counterAccountId: String? = null,
    @SerialName("counter_amount") val counterAmount: Long? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("subcategory_id") val subcategoryId: String? = null,
    @SerialName("debt_id") val debtId: String? = null,
    val note: String? = null,
    @SerialName("rate_to_base") val rateToBase: Double,
    @SerialName("amount_base") val amountBase: Long,
    @SerialName("rate_source") val rateSource: String,
)
