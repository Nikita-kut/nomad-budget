package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Subcategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FromSavingsPlanTest {

    private val period = SalaryCycle.periodContaining(LocalDate(2026, 10, 10))
    private val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 10)
    private val life = Category("life", "Жизнь", CategoryKind.EXPENSE, 20)
    private val housing = Category("housing", "Жильё", CategoryKind.EXPENSE, 30)
    private val family = Category("family", "Семья", CategoryKind.EXPENSE, 40)
    private val rent = Subcategory("rent", "housing", "аренда")
    private val categories = listOf(salary, life, housing, family)

    private fun summary(lines: List<BudgetLine>, target: Long = 0L) =
        PlanCalculator.summary(BudgetCalculator.categoryBudgets(categories, lines, emptyList(), period, listOf(rent)), Money.rub(target))

    @Test
    fun userScenario_balanceConverges() {
        val lines = listOf(
            BudgetLine("salary", Money.rub(40_000_000L)),
            BudgetLine("life", Money.rub(30_000_000L)),
            BudgetLine("housing", Money.rub(5_000_000L), "rent", fromSavings = true),
            BudgetLine("family", Money.rub(8_000_000L), fromSavings = true),
        )
        val s = summary(lines, target = 10_000_000L)
        assertEquals(Money.rub(43_000_000L), s.expensePlanned)
        assertEquals(Money.rub(13_000_000L), s.fromSavingsPlanned)
        assertEquals(Money.rub(30_000_000L), s.fromIncomePlanned)
        assertTrue(s.free.isZero)
    }

    @Test
    fun withoutSavingsLines_behavesAsBefore() {
        val s = summary(listOf(BudgetLine("salary", Money.rub(10_000_000L)), BudgetLine("life", Money.rub(4_000_000L))), target = 1_000_000L)
        assertEquals(Money.rub(5_000_000L), s.free)
        assertTrue(s.fromSavingsPlanned.isZero)
    }

    @Test
    fun categoryBudget_exposesFlags() {
        val budget = BudgetCalculator.categoryBudgets(
            categories,
            listOf(BudgetLine("housing", Money.rub(1_000_000L), fromSavings = true), BudgetLine("housing", Money.rub(2_000_000L), "rent")),
            emptyList(),
            period,
            listOf(rent),
        ).first { it.category.id == "housing" }
        assertTrue(budget.freeFromSavings)
        assertEquals(Money.rub(1_000_000L), budget.fromSavingsPlanned)
        assertTrue(budget.items.none { it.fromSavings })
    }
}
