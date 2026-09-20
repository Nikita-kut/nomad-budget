package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetCalculatorTest {

    private val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 5))

    private val food = Category("food", "Еда", CategoryKind.EXPENSE, 10)
    private val debt = Category("debt", "Долг", CategoryKind.EXPENSE, 120)
    private val comm = Category("comm", "Связь", CategoryKind.EXPENSE, 60)
    private val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 10)
    private val categories = listOf(debt, food, comm, salary)

    private val lines = listOf(
        BudgetLine("food", Money.rub(3_000_000L)),
        BudgetLine("debt", Money.rub(10_359_400L)),
        BudgetLine("comm", Money.rub(352_000L)),
        BudgetLine("salary", Money.rub(30_000_000L)),
    )

    private val ruCard = Account("ru_card", "RU карта", Currency.RUB, AccountKind.CARD, false, Money.zero(Currency.RUB))
    private val invest = Account("ru_invest", "Инвесткопилка", Currency.RUB, AccountKind.INVESTMENT, true, Money.zero(Currency.RUB))

    private fun expense(id: String, day: Int, categoryId: String, rub: Long) = Transaction.Expense(
        id = id, date = LocalDate(2026, 9, day), accountId = "ru_card", amount = Money.rub(rub),
        categoryId = categoryId, subcategoryId = null, amountBase = Money.rub(rub), rateSource = RateSource.API,
    )

    private val transactions = listOf(
        Transaction.Income("i1", LocalDate(2026, 9, 5), "ru_card", Money.rub(26_348_000L), "salary", Money.rub(26_348_000L), RateSource.API),
        Transaction.Transfer("t1", LocalDate(2026, 9, 5), "ru_card", "ru_invest", Money.rub(12_000_000L), Money.rub(12_000_000L)),
        expense("e1", 5, "debt", 8_217_900L),
        expense("e2", 6, "debt", 2_180_000L),
        expense("e3", 5, "comm", 722_000L),
        expense("e4", 8, "food", 126_442L),
        expense("old", 4, "food", 999_999L),
    )

    @Test
    fun categoryBudgets_sortedByOrder_andFactOnlyInsidePeriod() {
        val budgets = BudgetCalculator.categoryBudgets(categories, lines, transactions, period)
        assertEquals(listOf("food", "salary", "comm", "debt"), budgets.map { it.category.id })
        val foodBudget = budgets.first { it.category.id == "food" }
        assertEquals(Money.rub(126_442L), foodBudget.fact)
        assertEquals(1, foodBudget.transactionCount)
    }

    @Test
    fun status_ok_warning_over() {
        val budgets = BudgetCalculator.categoryBudgets(categories, lines, transactions, period).associateBy { it.category.id }
        assertEquals(BudgetStatus.OK, budgets.getValue("food").status)
        assertEquals(BudgetStatus.OVER, budgets.getValue("debt").status)
        assertEquals(BudgetStatus.OVER, budgets.getValue("comm").status)
        assertEquals(Money.rub(-38_500L), budgets.getValue("debt").remaining)
    }

    @Test
    fun status_warning_atEightyFivePercent() {
        val warnLines = listOf(BudgetLine("food", Money.rub(100_000L)))
        val tx = listOf(expense("w", 10, "food", 85_000L))
        val budget = BudgetCalculator.categoryBudgets(listOf(food), warnLines, tx, period).single()
        assertEquals(BudgetStatus.WARNING, budget.status)
        assertEquals(0.85, budget.progress, absoluteTolerance = 0.0001)
    }

    @Test
    fun categoryWithoutLine_hasZeroPlan_andOverIfSpent() {
        val budget = BudgetCalculator.categoryBudgets(listOf(food), emptyList(), listOf(expense("z", 9, "food", 1L)), period).single()
        assertEquals(Money.zero(Currency.RUB), budget.planned)
        assertEquals(BudgetStatus.OVER, budget.status)
        assertEquals(1.0, budget.progress)
    }

    @Test
    fun monthSummary_separatesSavingsFromExpenses() {
        val budgets = BudgetCalculator.categoryBudgets(categories, lines, transactions, period)
        val summary = BudgetCalculator.monthSummary(budgets, transactions, listOf(ruCard, invest), period)
        assertEquals(Money.rub(26_348_000L), summary.incomeFact)
        assertEquals(Money.rub(30_000_000L), summary.incomePlanned)
        assertEquals(Money.rub(11_246_342L), summary.expenseFact)
        assertEquals(Money.rub(12_000_000L), summary.savedToSavings)
        assertEquals(Money.rub(3_101_658L), summary.remaining)
    }
}
