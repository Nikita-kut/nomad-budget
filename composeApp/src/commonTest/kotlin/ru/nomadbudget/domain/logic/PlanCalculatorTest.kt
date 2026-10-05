package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Subcategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanCalculatorTest {

    private val period = SalaryCycle.periodContaining(LocalDate(2026, 10, 5))
    private val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 10)
    private val food = Category("food", "Еда", CategoryKind.EXPENSE, 20)
    private val housing = Category("housing", "Жильё", CategoryKind.EXPENSE, 30)
    private val gifts = Category("gifts", "Подарки", CategoryKind.EXPENSE, 40)
    private val rent = Subcategory("rent", "housing", "аренда")
    private val utilities = Subcategory("utilities", "housing", "коммуналка")
    private val categories = listOf(salary, food, housing, gifts)

    private fun budgets(lines: List<BudgetLine>) =
        BudgetCalculator.categoryBudgets(categories, lines, emptyList(), period, listOf(rent, utilities))

    @Test
    fun summary_totalsFreeAndShares() {
        val lines = listOf(
            BudgetLine("salary", Money.rub(20_000_000L)),
            BudgetLine("food", Money.rub(5_000_000L)),
            BudgetLine("housing", Money.rub(9_000_000L)),
            BudgetLine("housing", Money.rub(6_000_000L), "rent"),
            BudgetLine("housing", Money.rub(1_000_000L), "utilities"),
        )
        val summary = PlanCalculator.summary(budgets(lines))
        assertEquals(Money.rub(20_000_000L), summary.incomePlanned)
        assertEquals(Money.rub(14_000_000L), summary.expensePlanned)
        assertEquals(Money.rub(6_000_000L), summary.free)
        assertEquals(Money.rub(7_000_000L), summary.itemized)
        assertEquals(Money.rub(2_000_000L), summary.unallocatedInLimits)
        assertEquals(0.7, summary.allocatedShare)
        assertEquals(2, summary.plannedCategories)
        assertEquals(3, summary.expenseCategories)
        assertEquals(listOf("housing", "food"), summary.shares.map { it.category.id })
        assertEquals(0.45, summary.shares.first().share)
        assertTrue(summary.overLimitCategories.isEmpty())
    }

    @Test
    fun summary_overPlannedIncome_freeIsNegative_andOverLimitListed() {
        val lines = listOf(
            BudgetLine("salary", Money.rub(10_000_000L)),
            BudgetLine("housing", Money.rub(5_000_000L)),
            BudgetLine("housing", Money.rub(8_000_000L), "rent"),
            BudgetLine("gifts", Money.rub(3_000_000L)),
        )
        val summary = PlanCalculator.summary(budgets(lines))
        assertEquals(Money.rub(11_000_000L), summary.expensePlanned)
        assertTrue(summary.free.isNegative)
        assertEquals(listOf(housing), summary.overLimitCategories)
    }

    @Test
    fun summary_withoutIncomePlan_sharesOfExpenses() {
        val lines = listOf(BudgetLine("food", Money.rub(3_000_000L)), BudgetLine("gifts", Money.rub(1_000_000L)))
        val summary = PlanCalculator.summary(budgets(lines))
        assertNull(summary.allocatedShare)
        assertEquals(0.75, summary.shares.first().share)
    }

    @Test
    fun summary_empty() {
        val summary = PlanCalculator.summary(budgets(emptyList()))
        assertTrue(summary.isEmpty)
        assertTrue(summary.shares.isEmpty())
        assertEquals(0, summary.plannedCategories)
    }
}
