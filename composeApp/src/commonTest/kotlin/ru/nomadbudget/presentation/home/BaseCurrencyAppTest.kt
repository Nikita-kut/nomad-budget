package ru.nomadbudget.presentation.home

import kotlinx.datetime.LocalDate
import ru.nomadbudget.demo.DemoDatabase
import ru.nomadbudget.domain.logic.CsvExporter
import ru.nomadbudget.domain.logic.HistoryCalculator
import ru.nomadbudget.domain.logic.PeriodBudgetLine
import ru.nomadbudget.domain.logic.RateRebase
import ru.nomadbudget.domain.logic.Scenario
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.DebtHistory
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BaseCurrencyAppTest {

    private val today = LocalDate(2026, 10, 22)

    private fun stateFor(db: DemoDatabase): HomeState {
        val base = db.currencies.first { it.code == db.settings.baseCurrencyCode }
        val period = SalaryCycle.periodContaining(db.today, db.settings.periodStartDay)
        val id = db.periodId(period)
        return HomeState(
            today = db.today,
            period = period,
            periodId = id,
            loading = false,
            currencies = db.currencies.toList(),
            accounts = db.accounts.toList(),
            categories = db.categories.toList(),
            subcategories = db.subcategories.toList(),
            rawTransactions = db.transactions.toList(),
            budgetLines = db.budgetLines[id].orEmpty().toList(),
            savingsTarget = db.savingsTargets[id],
            allPlanLines = db.budgetLines.flatMap { (periodId, lines) -> lines.map { PeriodBudgetLine(LocalDate.parse(periodId.removePrefix("demo:")), it) } },
            rates = RateRebase.table(db.ratesOn(db.today), base),
            balanceChecks = db.balanceChecks.toList(),
            debts = db.debts.toList(),
            rateHistory = RateRebase.history(db.rateHistory.sortedBy { it.date }, Currency.RATE_PIVOT, base),
            capitalGoals = db.capitalGoals.toList(),
            settings = db.settings,
        )
    }

    private fun everyAmount(state: HomeState): List<Money> {
        val month = state.month
        val capital = state.capital
        val history = state.monthlyHistory
        val amounts = mutableListOf(
            state.totalBase, state.operationalBase, state.fundsBalance, state.savingsBalance,
            month.incomePlanned, month.incomeFact, month.flexiblePlanned, month.flexibleFact, month.canSpend, month.debtsPlanned,
            month.fundsPlanned, month.debtsLeft, month.cushionNet, month.saved, month.savingsLeft, month.available, month.unallocated,
            capital.assets, capital.netWorth, capital.debt, capital.monthly(Scenario.FACT), capital.principalPace,
            HistoryCalculator.totals(history, state.base).income,
        )
        amounts += month.envelopes.flatMap { listOf(it.planned, it.fact) }
        amounts += capital.months.flatMap { listOf(it.capitalGrowth, it.saved, it.principalPaid) }
        amounts += capital.capitalPath(Scenario.FACT_WITH_RETURN, 24)
        amounts += capital.goals.map { capital.goalProgress(it).requiredMonthly }
        amounts += state.debtMonths.flatMap { listOf(it.obligatory, it.debtAtEnd, it.income, it.regularPaid, it.earlyPaid) }
        amounts += history.flatMap { listOf(it.income, it.expense, it.netSaved) }
        amounts += state.categoryHistory.values.flatMap { listOf(it.average, it.previous) }
        amounts += HistoryCalculator.plannedExpenseByPeriod(state.allPlanLines, state.categories, state.base).values
        amounts += HistoryCalculator.expensesByCategory(state.transactions, state.categories, state.period.start, state.period.endExclusive, state.base).map { it.total }
        amounts += HistoryCalculator.netWorth(listOf(state.period), state.accounts, state.transactions, state.base) { currency, date -> state.marketRateOn(currency, date) }.map { it.second }
        amounts += state.transactions.map { it.amountBase }
        return amounts
    }

    private fun assertWholeAppIn(base: Currency, state: HomeState) {
        assertEquals(base, state.base)
        assertTrue(state.currenciesWithoutRate.isEmpty(), state.currenciesWithoutRate.joinToString { it.code })
        val wrong = everyAmount(state).filter { it.currency != base }
        assertTrue(wrong.isEmpty(), "суммы не в ${base.code}: ${wrong.take(5)}")
    }

    @Test
    fun wholeApp_inEveryBase() {
        listOf("RUB", "USD", DemoDatabase.TEST_CURRENCY.code).forEach { code ->
            val db = DemoDatabase(today)
            db.changeBase(code)
            val base = db.currencies.first { it.code == code }
            assertWholeAppIn(base, stateFor(db))
            val csv = CsvExporter.export(db.transactions, db.accounts, db.categories, db.subcategories)
            assertTrue(csv.lines().drop(1).filter { it.isNotBlank() }.all { ",$code," in it }, code)
        }
    }

    @Test
    fun netWorth_isTheSameValue_inAnyBase() {
        val rub = stateFor(DemoDatabase(today)).totalBase
        listOf("USD", DemoDatabase.TEST_CURRENCY.code).forEach { code ->
            val db = DemoDatabase(today)
            db.changeBase(code)
            val state = stateFor(db)
            val rubPerBase = 1.0 / requireNotNull(state.rates.rateFor(Currency.RUB)).basePerUnit
            val converted = state.totalBase.toMajor() * rubPerBase
            assertTrue(abs(converted - rub.toMajor()) / rub.toMajor() < 0.001, "$code: $converted vs ${rub.toMajor()}")
        }
    }

    @Test
    fun accountsInNewBase_keepExactAmounts() {
        val db = DemoDatabase(today)
        db.changeBase("USD")
        val usdAccounts = db.accounts.filter { it.currency == Currency.USD }.map { it.id }.toSet()
        val spends = db.transactions.filterIsInstance<Transaction.Expense>().filter { it.accountId in usdAccounts }
        assertTrue(spends.isNotEmpty())
        assertTrue(spends.all { it.amountBase == it.amount })
        val exchanges = db.transactions.filterIsInstance<Transaction.Exchange>().filter { it.received.currency == Currency.USD }
        assertTrue(exchanges.isNotEmpty() && exchanges.all { it.amountBase == it.received })
    }

    @Test
    fun revert_restoresEverything_andIsBlockedAfterNewOperations() {
        val original = DemoDatabase(today)
        val db = DemoDatabase(today)
        db.changeBase("USD")
        assertEquals(listOf("RUB", DemoDatabase.TEST_CURRENCY.code), db.settings.currencyCodes)
        db.revertBase()
        assertEquals(original.transactions, db.transactions)
        assertEquals(original.budgetLines, db.budgetLines)
        assertEquals(original.capitalGoals, db.capitalGoals)
        assertEquals("RUB", db.settings.baseCurrencyCode)
        assertNotNull(db.baseChange?.takeIf { it.reverted })

        db.changeBase("USD")
        db.transactions.removeAt(0)
        assertFailsWith<IllegalStateException> { db.revertBase() }
    }

    @Test
    fun closedDebtInterest_inDebtCurrency_afterBaseChange() {
        val db = DemoDatabase(today)
        db.changeBase("USD")
        val debt = db.debts.first()
        val totals = DebtHistory.totals(debt, db.transactions.filterIsInstance<Transaction.Expense>())
        assertEquals(debt.currency, totals.paid.currency)
    }
}
