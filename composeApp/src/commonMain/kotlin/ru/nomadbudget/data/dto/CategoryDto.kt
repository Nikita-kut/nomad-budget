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
    @SerialName("is_system") val isSystem: Boolean = false,
)

@Serializable
data class CategoryInsertDto(
    val name: String,
    val kind: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("is_system") val isSystem: Boolean = false,
)

@Serializable
data class SubcategoryDto(
    val id: String,
    @SerialName("category_id") val categoryId: String,
    val name: String,
    @SerialName("archived_at") val archivedAt: String? = null,
)

@Serializable
data class SubcategoryInsertDto(
    @SerialName("category_id") val categoryId: String,
    val name: String,
)
