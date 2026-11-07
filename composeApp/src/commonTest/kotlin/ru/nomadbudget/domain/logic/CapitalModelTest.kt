package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DefaultRates
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CapitalModelTest {

    private val today = LocalDate(2026, 11, 7)
    private val rates = DefaultRates.table()
    private val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.rub(10_000_000L))
    private val cushion = Account("cushion", "Подушка", Currency.RUB, AccountKind.SAVINGS, true, Money.rub(0L))
    private val accounts = listOf(card, cushion)
    private val food = Category("food", "Еда", CategoryKind.EXPENSE, 10)

    private fun transfer(id: String, date: LocalDate, from: String, to: String, rub: Long) =
        Transaction.Transfer(id, date, from, to, Money.rub(rub), Money.rub(rub))

    private fun expense(id: String, date: LocalDate, rub: Long, account: String = "card") =
        Transaction.Expense(id, date, account, Money.rub(rub), "food", null, Money.rub(rub), RateSource.API)

    private fun build(tx: List<Transaction>, debts: List<Debt> = emptyList(), settings: CapitalSettings = CapitalSettings(), goals: List<CapitalGoal> = emptyList()) =
        CapitalModel.build(
            today = today,
            accounts = accounts,
            balances = accounts.associate { it.id to BalanceCalculator.balance(it, tx) },
            transactions = tx,
            debts = debts,
            rates = rates,
            systemCategoryIds = emptySet(),
            planMonthly = Money.rub(9_000_000L),
            settings = settings,
            goals = goals,
        )

    private val history = listOf(
        transfer("a", LocalDate(2026, 8, 6), "card", "cushion", 8_000_000L),
        transfer("b", LocalDate(2026, 9, 6), "card", "cushion", 7_564_932L),
        transfer("c", LocalDate(2026, 10, 6), "card", "cushion", 11_949_243L),
        transfer("d", LocalDate(2026, 10, 20), "cushion", "card", 10_225_000L),
        expense("e", LocalDate(2026, 10, 21), 8_000_000L, account = "cushion"),
        expense("f", LocalDate(2026, 10, 22), 20_000_000L),
    )

    @Test
    fun pace_explainsMedianAndAverage_byClosedMonths() {
        val c = build(history)
        val closed = c.closedMonths.takeLast(3)
        assertEquals(listOf(8_000_000L, 7_564_932L, -6_275_757L), closed.map { it.saved.minor })
        assertEquals(Money.rub(3_096_391L), c.paceAverage)
        assertEquals(Money.rub(7_564_932L), c.paceMedian)
    }

    @Test
    fun cushionPath_planAndReturn() {
        val c = build(history, settings = CapitalSettings(annualReturn = 12.0))
        val plan = c.cushionPath(Scenario.PLAN, 12)
        assertEquals(c.cushion + Money.rub(9_000_000L * 12), plan.last())
        val withReturn = c.cushionPath(Scenario.FACT_WITH_RETURN, 12).last()
        val noReturn = c.cushionPath(Scenario.FACT, 12).last()
        assertTrue(withReturn > noReturn)
    }

    @Test
    fun debtSchedule_goesToZero_andCapitalCountsDebt() {
        val debt = Debt("d", "Кредит", Currency.RUB, Money.rub(12_000_000L), Money.rub(1_000_000L), null, 10)
        val c = build(history, debts = listOf(debt))
        assertEquals(Money.rub(12_000_000L), c.debt)
        assertEquals(12, c.debtFreeInMonths)
        assertEquals(c.assets - c.debt, c.netWorth)
        assertEquals(c.capitalPath(Scenario.FACT, 0).first(), c.netWorth)
    }

    @Test
    fun goals_requiredMonthly_andCushionTarget() {
        val goal = CapitalGoal("g", "Поездка", Money.rub(50_000_000L), LocalDate(2027, 11, 7))
        val c = build(history, goals = listOf(goal))
        val progress = c.goalProgress(goal)
        assertEquals(12, progress.monthsLeft)
        assertEquals(Money((50_000_000L - c.cushion.minor) / 12, Currency.BASE), progress.requiredMonthly)
        assertNotNull(c.cushionTarget)
        assertEquals(0, c.monthsToReach(Money.rub(0L), Scenario.FACT))
    }

    @Test
    fun monthsBetween_countsWholeMonths() {
        assertEquals(12, CapitalModel.monthsBetween(LocalDate(2026, 11, 7), LocalDate(2027, 11, 7)))
        assertEquals(11, CapitalModel.monthsBetween(LocalDate(2026, 11, 7), LocalDate(2027, 11, 6)))
    }
}
