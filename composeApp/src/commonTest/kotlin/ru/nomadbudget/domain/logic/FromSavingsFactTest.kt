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
import kotlin.test.assertTrue

class FromSavingsFactTest {

    private val period = SalaryCycle.periodContaining(LocalDate(2026, 10, 10))
    private val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 10)
    private val life = Category("life", "Жизнь", CategoryKind.EXPENSE, 20)
    private val family = Category("family", "Семья", CategoryKind.EXPENSE, 30)
    private val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.rub(0L))
    private val cushion = Account("cushion", "Подушка", Currency.RUB, AccountKind.SAVINGS, true, Money.rub(0L))
    private val accounts = listOf(card, cushion)
    private val savingsIds = setOf("cushion")

    private fun expense(id: String, account: String, category: String, rub: Long) = Transaction.Expense(
        id = id, date = LocalDate(2026, 10, 12), accountId = account, amount = Money.rub(rub), categoryId = category,
        subcategoryId = null, amountBase = Money.rub(rub), rateSource = RateSource.API,
    )

    private fun salaryIncome(rub: Long) = Transaction.Income("i", LocalDate(2026, 10, 5), "card", Money.rub(rub), "salary", Money.rub(rub), RateSource.API)

    @Test
    fun spendFromSavings_doesNotBreakRemaining_orRedCategory() {
        val tx = listOf(salaryIncome(40_000_000L), expense("a", "card", "life", 25_000_000L), expense("b", "cushion", "family", 8_000_000L))
        val lines = listOf(BudgetLine("salary", Money.rub(40_000_000L)), BudgetLine("life", Money.rub(30_000_000L)))
        val budgets = BudgetCalculator.categoryBudgets(listOf(salary, life, family), lines, tx, period, savingsAccountIds = savingsIds)
        val summary = BudgetCalculator.monthSummary(budgets, tx, accounts, period)
        assertEquals(Money.rub(15_000_000L), summary.remaining)
        assertEquals(Money.rub(8_000_000L), summary.expenseFromSavings)
        assertEquals(Money.rub(8_000_000L), summary.unplannedFromSavings)
        val familyBudget = budgets.first { it.category.id == "family" }
        assertEquals(BudgetStatus.OK, familyBudget.status)
        assertTrue(familyBudget.incomeFact.isZero)
    }

    @Test
    fun plannedSavingsSpend_isNotUnplanned() {
        val tx = listOf(expense("b", "cushion", "family", 8_000_000L))
        val lines = listOf(BudgetLine("family", Money.rub(8_000_000L), fromSavings = true))
        val budgets = BudgetCalculator.categoryBudgets(listOf(salary, life, family), lines, tx, period, savingsAccountIds = savingsIds)
        val summary = BudgetCalculator.monthSummary(budgets, tx, accounts, period)
        assertTrue(summary.unplannedFromSavings.isZero)
        assertEquals(Money.rub(8_000_000L), summary.plannedFromSavings)
        assertEquals(BudgetStatus.OK, budgets.first { it.category.id == "family" }.status)
    }

    @Test
    fun plannedFromSavings_butPaidFromCard_isOverIncome() {
        val tx = listOf(expense("b", "card", "family", 8_000_000L))
        val lines = listOf(BudgetLine("family", Money.rub(8_000_000L), fromSavings = true))
        val budgets = BudgetCalculator.categoryBudgets(listOf(salary, life, family), lines, tx, period, savingsAccountIds = savingsIds)
        assertEquals(BudgetStatus.OVER, budgets.first { it.category.id == "family" }.status)
    }

    @Test
    fun withoutSavingsAccounts_behavesAsBefore() {
        val tx = listOf(salaryIncome(40_000_000L), expense("a", "card", "life", 25_000_000L))
        val lines = listOf(BudgetLine("salary", Money.rub(40_000_000L)), BudgetLine("life", Money.rub(30_000_000L)))
        val budgets = BudgetCalculator.categoryBudgets(listOf(salary, life, family), lines, tx, period)
        val summary = BudgetCalculator.monthSummary(budgets, tx, accounts, period)
        assertEquals(Money.rub(15_000_000L), summary.remaining)
        assertTrue(!summary.hasSavingsSpending)
    }
}
