package ru.nomadbudget.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CapitalGoalDto(
    val id: String,
    val name: String,
    @SerialName("amount_base") val amountBase: Long,
    @SerialName("target_date") val targetDate: String,
)

@Serializable
data class CapitalGoalInsertDto(
    val name: String,
    @SerialName("amount_base") val amountBase: Long,
    @SerialName("target_date") val targetDate: String,
)
