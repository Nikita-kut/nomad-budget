package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JournalCalculatorTest {

    private val d1 = LocalDate(2026, 10, 3)
    private val d2 = LocalDate(2026, 10, 5)

    private fun expense(id: String, date: LocalDate, rub: Long, note: String = "") =
        Transaction.Expense(id, date, "card", Money.rub(rub), "food", null, Money.rub(rub), RateSource.API, note = note)

    private fun income(id: String, date: LocalDate, rub: Long) =
        Transaction.Income(id, date, "card", Money.rub(rub), "salary", Money.rub(rub), RateSource.API)

    private val usdExpense = Transaction.Expense("u", d2, "usd", Money(12_000L, Currency.USD), "food", null, Money.rub(1_104_000L), RateSource.API)

    @Test
    fun summary_groupsByDayNewestFirst_andSumsBaseAmounts() {
        val summary = JournalCalculator.summary(listOf(expense("a", d1, 10_000L), income("i", d2, 500_000L), expense("b", d2, 20_000L)))
        assertEquals(listOf(d2, d1), summary.days.map { it.date })
        assertEquals(Money.rub(20_000L), summary.days.first().spentBase)
        assertEquals(3, summary.count)
        assertEquals(Money.rub(30_000L), summary.spent)
        assertEquals(Money.rub(500_000L), summary.earned)
    }

    @Test
    fun matchesQuery_byLabelOrNote_caseInsensitive() {
        val tx = expense("a", d1, 45_000L, note = "Такси домой")
        assertTrue(JournalCalculator.matchesQuery(tx, "такси", emptyList()))
        assertTrue(JournalCalculator.matchesQuery(tx, "ЕДА", listOf("Еда")))
        assertFalse(JournalCalculator.matchesQuery(tx, "аптека", listOf("Еда")))
    }

    @Test
    fun matchesQuery_byDigits_inBaseOrOwnAmount() {
        assertTrue(JournalCalculator.matchesQuery(usdExpense, "120", emptyList()))
        assertTrue(JournalCalculator.matchesQuery(usdExpense, "11 040", emptyList()))
        assertFalse(JournalCalculator.matchesQuery(usdExpense, "999", emptyList()))
    }

    @Test
    fun matchesQuery_blankQuery_matchesEverything() {
        assertTrue(JournalCalculator.matchesQuery(usdExpense, "  ", emptyList()))
    }
}
