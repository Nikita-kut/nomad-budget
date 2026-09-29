package ru.nomadbudget.data.mapper

import ru.nomadbudget.data.dto.CategoryDto
import ru.nomadbudget.data.dto.SubcategoryDto
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Subcategory

object CategoryMapper {

    fun toDomain(dto: CategoryDto): Category = Category(
        id = dto.id,
        name = dto.name,
        kind = CategoryKind.valueOf(dto.kind.uppercase()),
        sortOrder = dto.sortOrder,
    )

    fun toDomain(dto: SubcategoryDto): Subcategory = Subcategory(
        id = dto.id,
        categoryId = dto.categoryId,
        name = dto.name,
    )
}
