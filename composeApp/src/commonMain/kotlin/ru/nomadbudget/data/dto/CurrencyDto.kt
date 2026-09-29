package ru.nomadbudget.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrencyDto(
    val code: String,
    @SerialName("minor_units") val minorUnits: Int,
    val symbol: String,
)
