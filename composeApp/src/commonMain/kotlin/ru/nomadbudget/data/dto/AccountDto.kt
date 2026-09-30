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
)

@Serializable
data class AccountInsertDto(
    val name: String,
    val currency: String,
    val kind: String,
    @SerialName("is_savings") val isSavings: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
)
