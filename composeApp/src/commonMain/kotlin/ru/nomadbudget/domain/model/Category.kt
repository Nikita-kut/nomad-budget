package ru.nomadbudget.domain.model

enum class CategoryKind { EXPENSE, INCOME }

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val sortOrder: Int = 0,
)

data class Subcategory(
    val id: String,
    val categoryId: String,
    val name: String,
)
