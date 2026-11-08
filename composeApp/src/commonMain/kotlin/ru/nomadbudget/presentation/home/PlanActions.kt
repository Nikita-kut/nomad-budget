package ru.nomadbudget.presentation.home

import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.plural

internal class PlanActions(
    private val store: HomeStore,
    private val runner: ActionRunner,
    private val periodRepository: PeriodRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val categories: CategoryActions,
) {

    fun setPlanned(categoryId: String, planned: Money) = setItemPlanned(categoryId, null, planned)

    fun setSavingsTarget(target: Money?) = runner.run("Не удалось сохранить план «Себе»") {
        val periodId = currentPeriodId()
        val value = target?.takeIf { it.minor > 0L }
        budgetRepository.setSavingsTarget(periodId, value)
        store.update { it.copy(periodId = periodId, savingsTarget = value) }
        store.message(if (value == null) "План «Себе» убран" else "План «Себе»: ${MoneyFormat.format(value, false)}")
    }

    fun setItemPlanned(categoryId: String, subcategoryId: String?, planned: Money) = runner.run("Не удалось сохранить план") {
        val periodId = currentPeriodId()
        val line = BudgetLine(categoryId, planned, subcategoryId)
        budgetRepository.setPlanned(periodId, line)
        store.update { state ->
            state.copy(
                periodId = periodId,
                budgetLines = state.budgetLines.filterNot { it.categoryId == categoryId && it.subcategoryId == subcategoryId } + line,
            )
        }
        store.message("План обновлён")
    }

    fun saveCategoryPlan(draft: CategoryPlanDraft) = runner.run("Не удалось сохранить план", busy = true) {
        val current = store.value
        val periodId = current.periodId ?: periodRepository.ensure(current.period)
        val categoryId = draft.categoryId
        val limitLine = draft.free?.takeIf { it.minor > 0L }?.let { BudgetLine(categoryId, it, null) }
        val hadLimit = current.budgetLines.any { it.categoryId == categoryId && it.subcategoryId == null }
        when {
            limitLine != null -> budgetRepository.setPlanned(periodId, limitLine)
            hadLimit -> budgetRepository.deleteLine(periodId, categoryId, null)
        }
        draft.removedSubcategoryIds.forEach { budgetRepository.deleteLine(periodId, categoryId, it) }
        val itemLines = draft.items.map { item ->
            val existing = item.subcategoryId?.let { id -> current.subcategoriesById[id] }
            val subcategoryId = if (existing != null) {
                val name = item.name.trim()
                if (existing.name != name && name.isNotBlank()) {
                    categoryRepository.renameSubcategory(existing.id, name)
                    store.update { state -> state.copy(subcategories = state.subcategories.map { if (it.id == existing.id) it.copy(name = name) else it }) }
                }
                existing.id
            } else {
                requireNotNull(categories.resolveSubcategory(categoryId, item.name)) { "Введи название подкатегории" }
            }
            BudgetLine(categoryId, item.planned, subcategoryId)
        }
        itemLines.forEach { budgetRepository.setPlanned(periodId, it) }
        val touched: Set<String?> = setOf<String?>(null) + draft.removedSubcategoryIds + itemLines.map { it.subcategoryId }
        store.update { state ->
            state.copy(
                periodId = periodId,
                budgetLines = state.budgetLines.filterNot { it.categoryId == categoryId && it.subcategoryId in touched } +
                    listOfNotNull(limitLine) + itemLines,
            )
        }
        store.message("План «${current.categoryName(categoryId)}» сохранён")
    }

    fun setPlannedForSubcategoryName(categoryId: String, subcategoryName: String, planned: Money) = runner.run("Не удалось сохранить план") {
        val subcategoryId = categories.resolveSubcategory(categoryId, subcategoryName)
        require(subcategoryId != null) { "Введи название подкатегории" }
        setItemPlanned(categoryId, subcategoryId, planned)
    }

    fun addSubcategoryWithPlan(categoryId: String, name: String, planned: Money?) = runner.run("Не удалось добавить подкатегорию") {
        val id = categories.resolveSubcategory(categoryId, name)
        require(id != null) { "Введи название" }
        if (planned != null && planned.minor > 0L) setItemPlanned(categoryId, id, planned) else store.message("Подкатегория добавлена")
    }

    fun removePlanLine(categoryId: String, subcategoryId: String?) = runner.run("Не удалось удалить строку") {
        val periodId = currentPeriodId()
        budgetRepository.deleteLine(periodId, categoryId, subcategoryId)
        store.update { state ->
            state.copy(
                periodId = periodId,
                budgetLines = state.budgetLines.filterNot { it.categoryId == categoryId && it.subcategoryId == subcategoryId },
            )
        }
        store.message("Строка плана удалена")
    }

    fun copyPlanFromPreviousPeriod() = runner.run("Не удалось скопировать план", busy = true) {
        val current = store.value
        val periodId = current.periodId ?: periodRepository.ensure(current.period)
        val previousId = periodRepository.ensure(SalaryCycle.previous(current.period, current.periodStartDay))
        val previousLines = budgetRepository.getLines(previousId).filter { it.planned.minor > 0L }
        val previousTarget = budgetRepository.getSavingsTarget(previousId)
        require(previousLines.isNotEmpty() || previousTarget != null) { "В прошлом месяце план не заполнен" }
        previousTarget?.let { budgetRepository.setSavingsTarget(periodId, it) }
        val activeIds = current.activeCategories.map { it.id }.toSet()
        val toCopy = previousLines.filter { it.categoryId in activeIds }
        toCopy.forEach { budgetRepository.setPlanned(periodId, it) }
        store.update { state ->
            val copiedKeys = toCopy.map { it.categoryId to it.subcategoryId }.toSet()
            state.copy(
                periodId = periodId,
                budgetLines = state.budgetLines.filterNot { (it.categoryId to it.subcategoryId) in copiedKeys } + toCopy,
                savingsTarget = previousTarget ?: state.savingsTarget,
            )
        }
        store.message("План скопирован: ${plural(toCopy.size, "категория", "категории", "категорий")}")
    }

    private suspend fun currentPeriodId(): String = store.value.periodId ?: periodRepository.ensure(store.value.period)
}
