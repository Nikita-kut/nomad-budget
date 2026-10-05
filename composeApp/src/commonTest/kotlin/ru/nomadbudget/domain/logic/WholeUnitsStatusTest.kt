package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Subcategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WholeUnitsStatusTest {

    private val loans = Category("loans", "Долг", CategoryKind.EXPENSE, 10)

    private fun budget(planned: Long, fact: Long) = CategoryBudget(
        category = loans,
        planned = Money.rub(planned),
        fact = Money.rub(fact),
        transactionCount = 1,
    )

    @Test
    fun kopecksAbovePlan_isDone_notOver() {
        val b = budget(planned = 120_600L, fact = 120_606L)
        assertEquals(BudgetStatus.DONE, b.status)
        assertTrue(b.remainingWhole.isZero)
    }

    @Test
    fun wholeRubleAbovePlan_isSlightOverrun_bigOverrun_isOver() {
        assertEquals(BudgetStatus.OVER_SLIGHTLY, budget(planned = 120_600L, fact = 120_700L).status)
        assertEquals(BudgetStatus.OVER, budget(planned = 100_000L, fact = 103_000L).status)
    }

    @Test
    fun exactPlan_isDone_belowThreshold_isOk_nearPlan_isWarning() {
        assertEquals(BudgetStatus.DONE, budget(planned = 100_000L, fact = 100_000L).status)
        assertEquals(BudgetStatus.OK, budget(planned = 100_000L, fact = 50_000L).status)
        assertEquals(BudgetStatus.WARNING, budget(planned = 100_000L, fact = 90_000L).status)
    }

    @Test
    fun noPlan_noFact_isOk_factWithoutPlan_isOver() {
        assertEquals(BudgetStatus.OK, budget(planned = 0L, fact = 0L).status)
        assertEquals(BudgetStatus.OVER, budget(planned = 0L, fact = 100L).status)
    }

    @Test
    fun subcategoryRemaining_ignoresKopecks() {
        val item = SubcategoryBudget(Subcategory("s", "loans", "банк"), planned = Money.rub(120_600L), fact = Money.rub(120_606L))
        assertTrue(item.remainingWhole?.isZero == true)
    }
}
