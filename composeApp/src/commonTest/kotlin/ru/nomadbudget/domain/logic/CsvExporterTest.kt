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
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvExporterTest {

    private val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.rub(0L))
    private val usd = Account("usd", "Доллары", Currency.USD, AccountKind.CASH, false, Money(0L, Currency.USD))
    private val food = Category("food", "Еда", CategoryKind.EXPENSE)
    private val cafe = Subcategory("cafe", "food", "кафе")

    private fun expense(note: String) = Transaction.Expense(
        id = "e", date = LocalDate(2026, 10, 6), accountId = "card", amount = Money.rub(123_456L), categoryId = "food",
        subcategoryId = "cafe", amountBase = Money.rub(123_456L), rateSource = RateSource.API, note = note,
    )

    @Test
    fun plain_formatsMinorUnits() {
        assertEquals("1234.56", CsvExporter.plain(Money.rub(123_456L)))
        assertEquals("-0.05", CsvExporter.plain(Money.rub(-5L)))
        assertEquals("5000", CsvExporter.plain(Money(5_000L, ru.nomadbudget.domain.model.TestCurrencies.JPY)))
    }

    @Test
    fun export_hasHeaderAndEscapesNotes() {
        val csv = CsvExporter.export(listOf(expense("обед, \"бизнес\"")), listOf(card), listOf(food), listOf(cafe))
        val lines = csv.trimEnd().split("\r\n")
        assertTrue(lines[0].startsWith("date,type,account"))
        assertEquals("2026-10-06,expense,Карта,1234.56,RUB,Еда,кафе,,,,1234.56,\"обед, \"\"бизнес\"\"\"", lines[1])
    }

    @Test
    fun export_neutralizesSpreadsheetFormulas() {
        val csv = CsvExporter.export(listOf(expense("=1+1")), listOf(card), listOf(food), listOf(cafe))
        assertTrue(csv.contains(",'=1+1"))
    }

    @Test
    fun export_exchangeKeepsBothSides() {
        val exchange = Transaction.Exchange(
            id = "x", date = LocalDate(2026, 10, 6), fromAccountId = "card", toAccountId = "usd",
            given = Money.rub(9_280_000L), received = Money(100_000L, Currency.USD), amountBase = Money.rub(9_280_000L),
        )
        val line = CsvExporter.export(listOf(exchange), listOf(card, usd), emptyList(), emptyList()).trimEnd().split("\r\n")[1]
        assertEquals("2026-10-06,exchange,Карта,92800.00,RUB,,,Доллары,1000.00,USD,92800.00,", line)
    }

    @Test
    fun dailyAllowance_dividesRemainingByDaysLeft() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 10, 10))
        val budget = CategoryBudget(food, Money.rub(3_100_000L), Money.rub(1_000_000L), 1)
        assertEquals(Money.rub(100_000L), PlanCalculator.dailyAllowance(listOf(budget), 21))
        assertEquals(null, PlanCalculator.dailyAllowance(listOf(budget), 0))
        assertEquals(31, period.lengthDays)
    }
}
