package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Subcategory
import kotlin.test.Test
import kotlin.test.assertEquals

class PaceTest {

    private val housing = Category("housing", "Жильё", CategoryKind.EXPENSE, 10)
    private val rent = Subcategory("rent", "housing", "аренда")
    private val utilities = Subcategory("util", "housing", "коммуналка")

    private fun flexible(planned: Long, fact: Long) = CategoryBudget(housing, Money.rub(planned), Money.rub(fact), 1)

    @Test
    fun flexibleSpending_proratedByDay() {
        val budget = flexible(planned = 3_000_000L, fact = 1_500_000L)
        assertEquals(Money.rub(1_500_000L), budget.expectedByNow(0.5))
        assertEquals(Pace.ON_TRACK, budget.pace(0.5))
        assertEquals(Pace.AHEAD, flexible(planned = 3_000_000L, fact = 2_400_000L).pace(0.5))
    }

    @Test
    fun paidFixedItem_countsInFull_restProrated() {
        val budget = CategoryBudget(
            category = housing,
            planned = Money.rub(5_000_000L),
            fact = Money.rub(4_500_000L),
            transactionCount = 1,
            items = listOf(
                SubcategoryBudget(rent, Money.rub(4_500_000L), Money.rub(4_500_000L)),
                SubcategoryBudget(utilities, Money.rub(500_000L), Money.rub(0L)),
            ),
            plannedByItems = true,
        )
        assertEquals(Money.rub(4_750_000L), budget.expectedByNow(0.5))
        assertEquals(Pace.ON_TRACK, budget.pace(0.1))
    }

    @Test
    fun withoutPlan_alwaysOnTrack() {
        assertEquals(Pace.ON_TRACK, flexible(planned = 0L, fact = 100_000L).pace(0.5))
    }
}
