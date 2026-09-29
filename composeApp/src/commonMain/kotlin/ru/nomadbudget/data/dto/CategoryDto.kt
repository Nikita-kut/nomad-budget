package ru.nomadbudget.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val kind: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("archived_at") val archivedAt: String? = null,
)

@Serializable
data class CategoryInsertDto(
    val name: String,
    val kind: String,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
data class SubcategoryDto(
    val id: String,
    @SerialName("category_id") val categoryId: String,
    val name: String,
)

@Serializable
data class SubcategoryInsertDto(
    @SerialName("category_id") val categoryId: String,
    val name: String,
)
