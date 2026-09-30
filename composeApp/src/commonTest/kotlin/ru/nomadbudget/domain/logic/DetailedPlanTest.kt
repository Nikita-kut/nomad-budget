package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailedPlanTest {

    private val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 5))
    private val debt = Category("debt", "Долг", CategoryKind.EXPENSE, 120)
    private val bankA = Subcategory("sub_a", "debt", "банк А")
    private val bankB = Subcategory("sub_b", "debt", "банк Б")
    private val subcategories = listOf(bankA, bankB)

    private fun expense(id: String, sub: String?, rub: Long) = Transaction.Expense(
        id = id, date = LocalDate(2026, 9, 6), accountId = "card", amount = Money.rub(rub), categoryId = "debt",
        subcategoryId = sub, amountBase = Money.rub(rub), rateSource = RateSource.API,
    )

    @Test
    fun planFromItems_overridesCategoryLine() {
        val lines = listOf(
            BudgetLine("debt", Money.rub(999_999L)),
            BudgetLine("debt", Money.rub(2_000_000L), "sub_a"),
            BudgetLine("debt", Money.rub(3_000_000L), "sub_b"),
        )
        val budget = BudgetCalculator.categoryBudgets(listOf(debt), lines, emptyList(), period, subcategories).single()
        assertTrue(budget.plannedByItems)
        assertEquals(Money.rub(5_000_000L), budget.planned)
        assertEquals(2, budget.items.size)
    }

    @Test
    fun items_showPlanAndFactPerSubcategory_andUnplannedFact() {
        val lines = listOf(BudgetLine("debt", Money.rub(2_000_000L), "sub_a"))
        val tx = listOf(expense("e1", "sub_a", 1_500_000L), expense("e2", "sub_b", 700_000L), expense("e3", null, 100_000L))
        val budget = BudgetCalculator.categoryBudgets(listOf(debt), lines, tx, period, subcategories).single()
        assertEquals(Money.rub(2_000_000L), budget.planned)
        assertEquals(Money.rub(2_300_000L), budget.fact)
        val a = budget.items.first { it.subcategory?.id == "sub_a" }
        assertEquals(Money.rub(500_000L), a.remaining)
        val b = budget.items.first { it.subcategory?.id == "sub_b" }
        assertNull(b.planned)
        assertEquals(Money.rub(700_000L), b.fact)
        val noSub = budget.items.last()
        assertNull(noSub.subcategory)
        assertEquals(Money.rub(100_000L), noSub.fact)
    }

    @Test
    fun withoutItemLines_categoryLineIsUsed() {
        val lines = listOf(BudgetLine("debt", Money.rub(4_000_000L)))
        val budget = BudgetCalculator.categoryBudgets(listOf(debt), lines, emptyList(), period, subcategories).single()
        assertFalse(budget.plannedByItems)
        assertEquals(Money.rub(4_000_000L), budget.planned)
        assertTrue(budget.items.isEmpty())
    }
}
