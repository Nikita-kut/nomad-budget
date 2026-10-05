package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanHistoryTest {

    private val current = SalaryCycle.periodContaining(LocalDate(2026, 10, 10))
    private val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 10)
    private val food = Category("food", "Еда", CategoryKind.EXPENSE, 20)

    private fun expense(id: String, date: LocalDate, rub: Long) = Transaction.Expense(
        id = id, date = date, accountId = "card", amount = Money.rub(rub), categoryId = "food",
        subcategoryId = null, amountBase = Money.rub(rub), rateSource = RateSource.API,
    )

    @Test
    fun savingsTarget_reducesFree_andCountsInAllocated() {
        val budgets = BudgetCalculator.categoryBudgets(
            listOf(salary, food),
            listOf(BudgetLine("salary", Money.rub(20_000_000L)), BudgetLine("food", Money.rub(10_000_000L))),
            emptyList(),
            current,
        )
        val summary = PlanCalculator.summary(budgets, savingsTarget = Money.rub(4_000_000L))
        assertEquals(Money.rub(6_000_000L), summary.free)
        assertEquals(0.7, summary.allocatedShare)
        assertEquals(0.2, summary.savingsShare)
    }

    @Test
    fun history_averagesOverActiveMonths_andKeepsPrevious() {
        val tx = listOf(
            expense("a", LocalDate(2026, 9, 10), 3_000_000L),
            expense("b", LocalDate(2026, 8, 10), 5_000_000L),
            expense("c", LocalDate(2026, 10, 10), 9_000_000L),
        )
        val history = PlanCalculator.history(tx, current).getValue("food")
        assertEquals(2, history.months)
        assertEquals(Money.rub(4_000_000L), history.average)
        assertEquals(Money.rub(3_000_000L), history.previous)
    }

    @Test
    fun history_emptyWithoutPastData() {
        assertTrue(PlanCalculator.history(listOf(expense("c", LocalDate(2026, 10, 10), 100L)), current).isEmpty())
    }
}
