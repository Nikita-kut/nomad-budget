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
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.nomadbudget.domain.model.TEST_START_DAY

class MonthModelTest {

    private val period: Period = SalaryCycle.periodContaining(LocalDate(2026, 11, 10), TEST_START_DAY)
    private val today = LocalDate(2026, 11, 20)
    private val rates = DefaultRates.table()

    private val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.rub(0L))
    private val cushion = Account("cushion", "Подушка", Currency.RUB, AccountKind.SAVINGS, true, Money.rub(0L))
    private val usdCushion = Account("usd", "Доллары", Currency.USD, AccountKind.SAVINGS, true, Money.zero(Currency.USD))
    private val rentFund = Account(
        "rent", "На аренду", Currency.RUB, AccountKind.ACCOUNT, false, Money.rub(0L),
        isFund = true, fundMonthly = Money.rub(2_300_000L), fundGoal = Money.rub(4_800_000L), fundDay = 23,
    )
    private val accounts = listOf(card, cushion, usdCushion, rentFund)

    private val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 10)
    private val food = Category("food", "Еда", CategoryKind.EXPENSE, 20)
    private val housing = Category("housing", "Жильё", CategoryKind.EXPENSE, 30)
    private val loans = Category("loans", "Долг", CategoryKind.EXPENSE, 40)
    private val correction = Category("corr", "Корректировка", CategoryKind.EXPENSE, 900, isSystem = true)
    private val correctionIn = Category("corr_in", "Корректировка", CategoryKind.INCOME, 900, isSystem = true)
    private val categories = listOf(salary, food, housing, loans, correction, correctionIn)

    private val loan = Debt("loan", "Кредит", Currency.RUB, Money.rub(100_000_000L), Money.rub(2_000_000L), 12.0, payDay = 22)
    private val earlyLoan = Debt("early", "Рассрочка", Currency.RUB, Money.rub(5_000_000L), Money.rub(500_000L), null, payDay = 1)

    private fun expense(id: String, category: String, rub: Long, account: String = "card", date: LocalDate = LocalDate(2026, 11, 12), debtId: String? = null) =
        Transaction.Expense(id, date, account, Money.rub(rub), category, null, Money.rub(rub), RateSource.API, debtId = debtId)

    private fun income(id: String, rub: Long, account: String = "card", category: String = "salary") =
        Transaction.Income(id, LocalDate(2026, 11, 5), account, Money.rub(rub), category, Money.rub(rub), RateSource.API)

    private fun transfer(id: String, from: String, to: String, rub: Long) =
        Transaction.Transfer(id, LocalDate(2026, 11, 6), from, to, Money.rub(rub), Money.rub(rub))

    private fun build(
        lines: List<BudgetLine> = emptyList(),
        tx: List<Transaction> = emptyList(),
        debts: List<Debt> = emptyList(),
        target: Money? = null,
        at: LocalDate = today,
        accs: List<Account> = accounts,
    ): MonthModel = MonthModel.build(
        period = period,
        today = at,
        categories = categories,
        subcategories = emptyList(),
        lines = lines,
        transactions = tx,
        accounts = accs,
        debts = debts,
        rates = rates,
        balances = accs.associate { it.id to BalanceCalculator.balance(it, tx) },
        savingsTarget = target,
    )

    @Test
    fun envelopes_ignoreFundCushionDebtAndSystemSpends() {
        val tx = listOf(
            expense("a", "food", 1_000_000L),
            expense("b", "housing", 4_800_000L, account = "rent"),
            expense("c", "food", 300_000L, account = "cushion"),
            expense("d", "loans", 2_000_000L, debtId = "loan"),
            expense("e", "corr", 50_000L),
        )
        val m = build(listOf(BudgetLine("food", Money.rub(3_000_000L))), tx, listOf(loan))
        assertEquals(Money.rub(1_000_000L), m.flexibleFact)
        assertEquals(Money.rub(2_000_000L), m.canSpend)
        assertEquals(Money.rub(-50_000L), m.corrections)
        assertTrue(m.envelopes.none { it.category.isSystem })
        assertEquals(Money.rub(4_800_000L), m.funds.single().spent)
        assertEquals(Money.rub(300_000L), m.spentFromCushion)
    }

    @Test
    fun earlyRepaymentFromCushion_isNotSpendingFromSavings() {
        val early = Transaction.Expense(
            "early", LocalDate(2026, 11, 12), "cushion", Money.rub(7_037_200L), "loans", null, Money.rub(7_037_200L), RateSource.API,
            debtId = "loan", debtEarly = true,
        )
        val tx = listOf(transfer("t1", "card", "cushion", 6_000_000L), early)
        val m = build(tx = tx, debts = listOf(loan), target = Money.rub(9_000_000L))
        assertEquals(Money.rub(-1_037_200L), m.cushionNet)
        assertEquals(Money.rub(7_037_200L), m.cushionToDebts)
        assertEquals(Money.rub(6_000_000L), m.saved)
        assertEquals(Money.rub(3_000_000L), m.savingsLeft)
        assertEquals(SavingsProgress.PENDING, m.savingsProgress)
    }

    @Test
    fun earlyRepayment_doesNotMarkRegularPaymentPaid() {
        val early = Transaction.Expense(
            "early", LocalDate(2026, 11, 12), "card", Money.rub(5_000_000L), "loans", null, Money.rub(5_000_000L), RateSource.API,
            debtId = "loan", debtEarly = true,
        )
        val obligation = build(tx = listOf(early), debts = listOf(loan)).debts.single()
        assertEquals(Money.rub(5_000_000L), obligation.earlyPaid)
        assertTrue(obligation.regularPaid.isZero)
        assertTrue(obligation.status != ObligationStatus.PAID)
        val regular = expense("r", "loans", 2_000_000L, debtId = "loan")
        assertEquals(ObligationStatus.PAID, build(tx = listOf(early, regular), debts = listOf(loan)).debts.single().status)
    }

    @Test
    fun earlyRepaymentLeavingLessThanOnePayment_isPaidOff() {
        val almostDone = loan.copy(principalRemaining = Money.rub(2_200L))
        val early = Transaction.Expense(
            "early", LocalDate(2026, 11, 12), "card", Money.rub(2_166_800L), "loans", null, Money.rub(2_166_800L), RateSource.API,
            debtId = "loan", debtEarly = true,
        )
        val obligation = build(tx = listOf(early), debts = listOf(almostDone)).debts.single()
        assertTrue(obligation.paidOff)
        assertEquals(ObligationStatus.PAID, obligation.status)
    }

    @Test
    fun closedDebt_staysInMonthsWhenItWasOpen() {
        val closed = loan.copy(isClosed = true, closedOn = LocalDate(2026, 11, 12), openedOn = LocalDate(2026, 1, 10))
        val obligation = build(debts = listOf(closed)).debts.single()
        assertTrue(obligation.paidOff)
        assertEquals(ObligationStatus.PAID, obligation.status)
        assertTrue(obligation.planned.isZero)
        val next = SalaryCycle.next(period, TEST_START_DAY)
        val later = MonthModel.build(
            period = next, today = next.start, categories = categories, subcategories = emptyList(), lines = emptyList(),
            transactions = emptyList(), accounts = accounts, debts = listOf(closed), rates = rates, balances = emptyMap(), savingsTarget = null,
        )
        assertTrue(later.debts.isEmpty())
        val notYetOpened = loan.copy(openedOn = LocalDate(2027, 1, 1))
        assertTrue(build(debts = listOf(notYetOpened)).debts.isEmpty())
    }

    @Test
    fun perDay_dividesWholeRemainderByDaysLeft() {
        val m = build(listOf(BudgetLine("food", Money.rub(3_000_000L))), listOf(expense("a", "food", 1_000_000L)))
        assertEquals(period.lengthDays - period.dayNumber(today) + 1, m.daysLeft)
        val perDay = m.perDay
        assertTrue(perDay != null && perDay.minor * m.daysLeft <= m.canSpend.minor)
    }

    @Test
    fun overspend_hasNoPerDay() {
        val m = build(listOf(BudgetLine("food", Money.rub(1_000_000L))), listOf(expense("a", "food", 1_500_000L)))
        assertTrue(m.canSpend.isNegative)
        assertNull(m.perDay)
    }

    @Test
    fun saved_countsTransfersExchangesIncome_andSubtractsCushionSpending() {
        val exchange = Transaction.Exchange("x", LocalDate(2026, 11, 7), "card", "usd", Money.rub(900_000L), Money(10_000L, Currency.USD), Money.rub(900_000L))
        val tx = listOf(
            transfer("t1", "card", "cushion", 5_000_000L),
            transfer("t2", "cushion", "card", 1_000_000L),
            exchange,
            income("i", 2_000_000L, account = "cushion"),
            expense("s", "food", 400_000L, account = "cushion"),
        )
        val m = build(tx = tx, target = Money.rub(7_000_000L))
        assertEquals(Money.rub(5_000_000L + 900_000L + 2_000_000L), m.savedIn)
        assertEquals(Money.rub(1_000_000L), m.savedOut)
        assertEquals(Money.rub(6_500_000L), m.saved)
        assertEquals(SavingsProgress.PENDING, m.savingsProgress)
        assertEquals(Money.rub(1_400_000L), m.takenFromCushion)
    }

    @Test
    fun savings_missedOnlyAfterPeriodEnds() {
        val tx = listOf(transfer("t", "card", "cushion", 1_000_000L))
        assertEquals(SavingsProgress.PENDING, build(tx = tx, target = Money.rub(9_000_000L)).savingsProgress)
        assertEquals(SavingsProgress.MISSED, build(tx = tx, target = Money.rub(9_000_000L), at = period.endExclusive).savingsProgress)
        assertEquals(SavingsProgress.DONE, build(tx = tx, target = Money.rub(1_000_000L)).savingsProgress)
    }

    @Test
    fun debtDueDates_crossMonthBoundary_andClampToMonthLength() {
        assertEquals(LocalDate(2026, 11, 22), MonthModel.dueDate(period, 22))
        assertEquals(LocalDate(2026, 12, 1), MonthModel.dueDate(period, 1))
        assertEquals(LocalDate(2026, 11, 5), MonthModel.dueDate(period, 5))
        assertEquals(LocalDate(2026, 11, 30), MonthModel.dueDate(period, 31))
        assertNull(MonthModel.dueDate(period, null))
    }

    @Test
    fun debtStatuses_paidSoonOverdueUpcoming() {
        val paid = listOf(expense("p", "loans", 2_000_000L, debtId = "loan"))
        assertEquals(ObligationStatus.PAID, build(tx = paid, debts = listOf(loan)).debts.single().status)
        assertEquals(ObligationStatus.SOON, build(debts = listOf(loan)).debts.single().status)
        assertEquals(ObligationStatus.OVERDUE, build(debts = listOf(loan), at = LocalDate(2026, 11, 25)).debts.single().status)
        assertEquals(ObligationStatus.UPCOMING, build(debts = listOf(earlyLoan)).debts.single().status)
        val m = build(debts = listOf(loan), at = LocalDate(2026, 11, 25))
        assertTrue(m.debtsOverdue)
        assertEquals(Money.rub(2_000_000L), m.debtsLeft)
        assertEquals(0, m.debtsPaid)
        assertEquals(1, build(tx = paid, debts = listOf(loan)).debtsPaid)
    }

    @Test
    fun fund_contributionAndEnoughForGoal() {
        val tx = listOf(transfer("c", "card", "rent", 2_300_000L))
        val start = listOf(transfer("s", "card", "rent", 2_500_000L)).map { it.copy(date = LocalDate(2026, 10, 10)) }
        val m = build(tx = start + tx)
        val fund = m.funds.single()
        assertEquals(Money.rub(2_300_000L), fund.contributed)
        assertTrue(fund.contributionLeft.isZero)
        assertEquals(ObligationStatus.PAID, fund.status)
        assertEquals(true, fund.isEnough)
        val short = build(tx = listOf(transfer("c", "card", "rent", 1_000_000L)))
        assertEquals(Money.rub(1_300_000L), short.funds.single().contributionLeft)
        assertEquals(false, short.funds.single().isEnough)
    }

    @Test
    fun carryOver_lifeAccountsAtPeriodStart_addsToAvailable() {
        val before = Transaction.Income("old", LocalDate(2026, 10, 20), "card", Money.rub(5_000_000L), "salary", Money.rub(5_000_000L), RateSource.API)
        val toCushion = Transaction.Transfer("oldsave", LocalDate(2026, 10, 21), "card", "cushion", Money.rub(1_000_000L), Money.rub(1_000_000L))
        val m = build(lines = listOf(BudgetLine("salary", Money.rub(38_000_000L)), BudgetLine("food", Money.rub(30_000_000L))), tx = listOf(before, toCushion, income("i", 38_000_000L)))
        assertEquals(Money.rub(4_000_000L), m.carryOver)
        assertEquals(Money.rub(42_000_000L), m.available)
        assertEquals(Money.rub(42_000_000L - 30_000_000L - 2_300_000L), m.unallocated)
    }

    @Test
    fun carryOver_backdatedExpenseInPreviousMonth_reducesIt() {
        val before = Transaction.Income("old", LocalDate(2026, 10, 20), "card", Money.rub(5_000_000L), "salary", Money.rub(5_000_000L), RateSource.API)
        val lateFix = expense("fix", "food", 1_500_000L, date = LocalDate(2026, 10, 30))
        val plain = build(tx = listOf(before))
        val fixed = build(tx = listOf(before, lateFix))
        assertEquals(Money.rub(5_000_000L), plain.carryOver)
        assertEquals(Money.rub(3_500_000L), fixed.carryOver)
        assertEquals(plain.unallocated - Money.rub(1_500_000L), fixed.unallocated)
    }

    @Test
    fun unallocated_balancesIncomeObligationsFlexibleSavings() {
        val lines = listOf(BudgetLine("salary", Money.rub(38_000_000L)), BudgetLine("food", Money.rub(16_320_000L)))
        val m = build(lines, debts = listOf(loan, earlyLoan), target = Money.rub(9_000_000L))
        assertEquals(Money.rub(2_500_000L + 2_300_000L), m.obligationsPlanned)
        assertEquals(Money.rub(38_000_000L - 4_800_000L - 16_320_000L - 9_000_000L), m.unallocated)
        assertTrue((m.allocatedShare ?: 2.0) <= 1.0)
    }

    @Test
    fun incomeOnSystemCategory_isNotIncome() {
        val m = build(tx = listOf(income("i", 1_000_000L), income("c", 77_000L, category = "corr_in")))
        assertEquals(Money.rub(1_000_000L), m.incomeFact)
        assertEquals(Money.rub(77_000L), m.corrections)
    }

    @Test
    fun archivedCategoryPlan_isIgnored_butSpendStillShown() {
        val old = Category("old", "Старое", CategoryKind.EXPENSE, 50, isArchived = true)
        val model = MonthModel.build(
            period, today, categories + old, emptyList(), listOf(BudgetLine("old", Money.rub(1_500_000L))),
            listOf(expense("x", "old", 10_000L)), accounts, emptyList(), rates, emptyMap(), null,
        )
        val budget = model.envelopes.single { it.category.id == "old" }
        assertTrue(budget.planned.isZero)
        assertEquals(Money.rub(10_000L), budget.fact)
    }

    @Test
    fun savingsProgress_doneWhenTargetReached() {
        val tx = listOf(expense("a", "food", 500_000L), transfer("t", "card", "cushion", 1_000_000L))
        val m = build(listOf(BudgetLine("food", Money.rub(1_000_000L))), tx, target = Money.rub(1_000_000L))
        assertEquals(SavingsProgress.DONE, m.savingsProgress)
        assertFalse(m.debtsOverdue)
    }
}
