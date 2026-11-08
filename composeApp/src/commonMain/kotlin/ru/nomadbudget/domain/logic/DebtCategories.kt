package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction

object DebtCategories {

    fun categoryIds(openDebts: List<Debt>, subcategories: List<Subcategory>, transactions: List<Transaction>): Set<String> {
        val debtNames = openDebts.map { it.name.trim().lowercase() }.toSet()
        val bySubcategory = subcategories.filter { it.name.trim().lowercase() in debtNames }.map { it.categoryId }
        val byPayments = transactions.filterIsInstance<Transaction.Expense>().filter { it.debtId != null }.map { it.categoryId }
        return (bySubcategory + byPayments).toSet()
    }

    fun isDebtCategory(categoryId: String?, openDebts: List<Debt>, debtCategoryIds: Set<String>): Boolean =
        openDebts.isNotEmpty() && categoryId != null && (debtCategoryIds.isEmpty() || categoryId in debtCategoryIds)

    fun isDebtOnlyEnvelope(budget: CategoryBudget, debtCategoryIds: Set<String>): Boolean =
        budget.category.id in debtCategoryIds && !budget.hasPlan && budget.fact.isZero

    fun byName(openDebts: List<Debt>, name: String): Debt? =
        openDebts.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
}
