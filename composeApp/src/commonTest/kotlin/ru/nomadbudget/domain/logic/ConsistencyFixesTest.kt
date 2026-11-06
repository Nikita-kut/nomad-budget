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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsistencyFixesTest {

    private val period = SalaryCycle.periodContaining(LocalDate(2026, 11, 10))
    private val day = LocalDate(2026, 11, 12)
    private val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.rub(0L))
    private val cushion = Account("cushion", "Подушка", Currency.RUB, AccountKind.SAVINGS, true, Money.rub(0L))
    private val usdCushion = Account("usd", "Доллары", Currency.USD, AccountKind.SAVINGS, true, Money.zero(Currency.USD))
    private val accounts = listOf(card, cushion, usdCushion)

    private fun cat(id: String, kind: CategoryKind = CategoryKind.EXPENSE, order: Int = 10) = Category(id, id, kind, order)

    private fun expense(id: String, category: String, minor: Long, account: String = "card") = Transaction.Expense(
        id, day, account, Money.rub(minor), category, null, Money.rub(minor), RateSource.API,
    )

    @Test
    fun verdict_usesSameRoundingAsEnvelopes() {
        val categories = listOf(cat("a"), cat("b"), cat("c"))
        val lines = categories.map { BudgetLine(it.id, Money.rub(10_000L)) }
        val tx = categories.map { expense("t${it.id}", it.id, 10_050L) }
        val budgets = BudgetCalculator.categoryBudgets(categories, lines, tx, period)
        assertTrue(budgets.all { it.status == BudgetStatus.DONE })
        val verdict = MonthVerdict.evaluate(BudgetCalculator.monthSummary(budgets, tx, accounts, period), null, false)
        assertTrue(verdict?.incomeSpendOk == true)
    }

    @Test
    fun exchangeIntoSavings_countsAsSaved() {
        val exchange = Transaction.Exchange("x", day, "card", "usd", Money.rub(9_000_000L), Money(100_000L, Currency.USD), Money.rub(9_000_000L))
        val summary = BudgetCalculator.monthSummary(emptyList(), listOf(exchange), accounts, period)
        assertEquals(Money.rub(9_000_000L), summary.netSaved)
    }

    @Test
    fun incomeDirectlyToSavings_countsAsSaved() {
        val income = Transaction.Income("i", day, "cushion", Money.rub(5_000_000L), "salary", Money.rub(5_000_000L), RateSource.API)
        val summary = BudgetCalculator.monthSummary(emptyList(), listOf(income), accounts, period)
        assertEquals(Money.rub(5_000_000L), summary.netSaved)
    }

    @Test
    fun planShare_isFromIncomeOnly_andAgreesWithFree() {
        val categories = listOf(cat("salary", CategoryKind.INCOME), cat("rent"), cat("food"))
        val lines = listOf(
            BudgetLine("salary", Money.rub(38_000_000L)),
            BudgetLine("rent", Money.rub(2_600_000L), fromSavings = true),
            BudgetLine("food", Money.rub(27_838_400L)),
        )
        val budgets = BudgetCalculator.categoryBudgets(categories, lines, emptyList(), period)
        val summary = PlanCalculator.summary(budgets, Money.rub(9_000_000L))
        assertTrue(!summary.free.isNegative)
        assertTrue((summary.allocatedShare ?: 0.0) <= 1.0)
    }

    @Test
    fun archivedCategoryPlanLine_isIgnored() {
        val archived = Category("old", "old", CategoryKind.EXPENSE, 5, isArchived = true)
        val budgets = BudgetCalculator.categoryBudgets(listOf(archived), listOf(BudgetLine("old", Money.rub(1_500_000L))), listOf(expense("e", "old", 100L)), period)
        assertEquals(Money.zero(Currency.BASE), budgets.single().planned)
    }

    @Test
    fun forecast_spendingFromSavingsLowersPace_andHugeValuesDoNotOverflow() {
        val points = listOf(MonthPoint(period, Money.rub(0L), Money.rub(0L), Money.rub(1_000_000L)))
        val ok = HistoryCalculator.savingsForecast(points, false, Money.rub(0L), Money(Long.MAX_VALUE / 10, Currency.BASE))
        assertNull(ok?.byTargetInYear)
        val tx = listOf(
            Transaction.Transfer("t", day, "card", "cushion", Money.rub(5_000_000L), Money.rub(5_000_000L)),
            expense("e", "rent", 4_000_000L, account = "cushion"),
        )
        val monthly = HistoryCalculator.monthly(tx, accounts, period)
        assertEquals(Money.rub(1_000_000L), monthly.last().netSaved)
    }
}
