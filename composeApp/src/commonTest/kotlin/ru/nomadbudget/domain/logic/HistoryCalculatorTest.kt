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

class HistoryCalculatorTest {

    private val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.zero(Currency.RUB))
    private val savings = Account("sav", "Копилка", Currency.RUB, AccountKind.SAVINGS, true, Money.zero(Currency.RUB))
    private val food = Category("food", "Еда", CategoryKind.EXPENSE, 10)
    private val rent = Category("rent", "Жильё", CategoryKind.EXPENSE, 30)
    private val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 10)

    private fun income(id: String, date: LocalDate, rub: Long) =
        Transaction.Income(id, date, "card", Money.rub(rub), "salary", Money.rub(rub), RateSource.API)

    private fun expense(id: String, date: LocalDate, cat: String, rub: Long) =
        Transaction.Expense(id, date, "card", Money.rub(rub), cat, null, Money.rub(rub), RateSource.API)

    private val tx = listOf(
        income("i1", LocalDate(2026, 7, 5), 10_000_000L),
        expense("e1", LocalDate(2026, 7, 10), "food", 3_000_000L),
        Transaction.Transfer("t1", LocalDate(2026, 7, 6), "card", "sav", Money.rub(2_000_000L), Money.rub(2_000_000L)),
        income("i2", LocalDate(2026, 8, 5), 12_000_000L),
        expense("e2", LocalDate(2026, 8, 11), "rent", 5_000_000L),
        Transaction.Transfer("t2", LocalDate(2026, 8, 20), "sav", "card", Money.rub(500_000L), Money.rub(500_000L)),
    )

    @Test
    fun monthly_buildsContinuousSeries_upToGivenPeriod() {
        val upTo = SalaryCycle.periodContaining(LocalDate(2026, 9, 20))
        val points = HistoryCalculator.monthly(tx, listOf(card, savings), upTo)
        assertEquals(3, points.size)
        assertEquals(LocalDate(2026, 7, 5), points[0].period.start)
        assertEquals(Money.rub(10_000_000L), points[0].income)
        assertEquals(Money.rub(3_000_000L), points[0].expense)
        assertEquals(Money.rub(2_000_000L), points[0].netSaved)
        assertEquals(Money.rub(-500_000L), points[1].netSaved)
        assertEquals(Money.zero(Currency.RUB), points[2].income)
    }

    @Test
    fun expensesByCategory_sortedWithShares() {
        val totals = HistoryCalculator.expensesByCategory(tx, listOf(food, rent, salary), LocalDate(2026, 7, 1), LocalDate(2026, 9, 1))
        assertEquals(listOf("rent", "food"), totals.map { it.category.id })
        assertEquals(0.625, totals[0].share, absoluteTolerance = 0.0001)
    }

    @Test
    fun cumulative_runningTotal() {
        val upTo = SalaryCycle.periodContaining(LocalDate(2026, 8, 20))
        val points = HistoryCalculator.monthly(tx, listOf(card, savings), upTo)
        assertEquals(listOf(Money.rub(2_000_000L), Money.rub(1_500_000L)), HistoryCalculator.cumulative(points) { it.netSaved })
    }
}
