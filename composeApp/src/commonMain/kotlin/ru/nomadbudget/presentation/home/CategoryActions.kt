package ru.nomadbudget.presentation.home

import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.repository.CategoryRepository

internal class CategoryActions(
    private val store: HomeStore,
    private val runner: ActionRunner,
    private val categoryRepository: CategoryRepository,
    private val onMerged: () -> Unit,
) {

    fun addCategory(name: String, kind: CategoryKind) = runner.run("Не удалось добавить категорию") {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Введи название" }
        val exists = store.value.activeCategories.any { it.kind == kind && it.name.equals(trimmed, ignoreCase = true) }
        require(!exists) { "Такая категория уже есть" }
        createCategory(trimmed, kind)
        store.message("Категория добавлена")
    }

    fun renameCategory(id: String, name: String) = runner.run("Не удалось переименовать") {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Введи название" }
        categoryRepository.renameCategory(id, trimmed)
        store.update { state -> state.copy(categories = state.categories.map { if (it.id == id) it.copy(name = trimmed) else it }) }
        store.message("Переименовано")
    }

    fun archiveCategory(id: String) = runner.run("Не удалось архивировать") {
        categoryRepository.archiveCategory(id)
        store.update { state -> state.copy(categories = state.categories.map { if (it.id == id) it.copy(isArchived = true) else it }) }
        store.message("Категория убрана в архив")
    }

    fun addSubcategory(categoryId: String, name: String) = runner.run("Не удалось добавить подкатегорию") {
        val id = resolveSubcategory(categoryId, name)
        require(id != null) { "Введи название" }
        store.message("Подкатегория добавлена")
    }

    fun renameSubcategory(id: String, name: String) = runner.run("Не удалось переименовать") {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Введи название" }
        categoryRepository.renameSubcategory(id, trimmed)
        store.update { state -> state.copy(subcategories = state.subcategories.map { if (it.id == id) it.copy(name = trimmed) else it }) }
        store.message("Переименовано")
    }

    fun deleteSubcategory(id: String) = runner.run("Не удалось удалить") {
        val current = store.value
        val used = current.transactions.any { it is Transaction.Expense && it.subcategoryId == id } ||
            current.allPlanLines.any { it.line.subcategoryId == id }
        if (used) {
            categoryRepository.archiveSubcategory(id, true)
            store.update { state -> state.copy(subcategories = state.subcategories.map { if (it.id == id) it.copy(isArchived = true) else it }) }
            store.message("Подкатегория убрана в архив, история сохранена")
        } else {
            categoryRepository.deleteSubcategory(id)
            store.update { state -> state.copy(subcategories = state.subcategories.filterNot { it.id == id }) }
            store.message("Подкатегория удалена")
        }
    }

    fun restoreSubcategory(id: String) = runner.run("Не удалось вернуть") {
        categoryRepository.archiveSubcategory(id, false)
        store.update { state -> state.copy(subcategories = state.subcategories.map { if (it.id == id) it.copy(isArchived = false) else it }) }
        store.message("Подкатегория возвращена")
    }

    fun mergeSubcategory(fromId: String, toId: String) = runner.run("Не удалось объединить", busy = true) {
        require(fromId != toId) { "Выбери другую подкатегорию" }
        categoryRepository.mergeSubcategory(fromId, toId)
        store.message("Подкатегории объединены")
        onMerged()
    }

    fun setCategorySystem(id: String, isSystem: Boolean) = runner.run("Не удалось изменить категорию") {
        categoryRepository.setSystem(id, isSystem)
        store.update { state -> state.copy(categories = state.categories.map { if (it.id == id) it.copy(isSystem = isSystem) else it }) }
        store.message(if (isSystem) "Категория стала служебной: в бюджете не учитывается" else "Категория снова учитывается в бюджете")
    }

    fun moveCategory(id: String, up: Boolean) = runner.run("Не удалось переставить категорию") {
        val current = store.value
        val target = current.categoriesById[id] ?: return@run
        val group = current.activeCategories.filter { it.kind == target.kind }.sortedBy { it.sortOrder }
        val index = group.indexOfFirst { it.id == id }
        val neighbourIndex = if (up) index - 1 else index + 1
        val neighbour = group.getOrNull(neighbourIndex) ?: return@run
        val reordered = group.toMutableList().apply {
            this[index] = neighbour
            this[neighbourIndex] = target
        }
        val orders = reordered.mapIndexed { position, category -> category.id to (position + 1) * HomeDefaults.SORT_STEP }.toMap()
        orders.forEach { (categoryId, order) -> categoryRepository.setCategorySortOrder(categoryId, order) }
        store.update { state -> state.copy(categories = state.categories.map { c -> orders[c.id]?.let { c.copy(sortOrder = it) } ?: c }) }
    }

    suspend fun ensureCategory(name: String, kind: CategoryKind): Category {
        val existing = store.value.categories.firstOrNull { it.kind == kind && it.name.equals(name, ignoreCase = true) && !it.isArchived }
        if (existing != null && !existing.isSystem) {
            categoryRepository.setSystem(existing.id, true)
            val system = existing.copy(isSystem = true)
            store.update { state -> state.copy(categories = state.categories.map { if (it.id == system.id) system else it }) }
            return system
        }
        return existing ?: createCategory(name, kind, sortOrder = HomeDefaults.CORRECTION_SORT_ORDER, isSystem = true)
    }

    suspend fun createCategory(name: String, kind: CategoryKind, sortOrder: Int? = null, isSystem: Boolean = false): Category {
        val order = sortOrder ?: ((store.value.categories.filter { it.kind == kind }.maxOfOrNull { it.sortOrder } ?: 0) + HomeDefaults.SORT_STEP)
        val created = categoryRepository.addCategory(name, kind, order, isSystem)
        store.update { it.copy(categories = it.categories + created) }
        return created
    }

    suspend fun resolveSubcategory(categoryId: String, name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val existing = store.value.subcategories.firstOrNull {
            it.categoryId == categoryId && it.name.equals(trimmed, ignoreCase = true)
        }
        if (existing != null) {
            if (existing.isArchived) {
                categoryRepository.archiveSubcategory(existing.id, false)
                store.update { state -> state.copy(subcategories = state.subcategories.map { if (it.id == existing.id) it.copy(isArchived = false) else it }) }
            }
            return existing.id
        }
        val created = categoryRepository.addSubcategory(categoryId, trimmed)
        store.update { it.copy(subcategories = it.subcategories + created) }
        return created.id
    }
}
