package ru.nomadbudget.scenario

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.logic.MonthModel
import ru.nomadbudget.domain.logic.ObligationStatus
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegressionTest {

    private val nov = LocalDate(2026, 11, 5)
    private val oct = LocalDate(2026, 10, 5)

    private fun ledgerWithLoan(): Ledger = Ledger().apply {
        planMonth(oct, income = 30_000_000L, food = 10_000_000L)
        planMonth(nov, income = 30_000_000L, food = 10_000_000L, savings = 9_000_000L)
        income(oct, 30_000_000L)
        income(nov, 30_000_000L)
        move(oct, 20_000_000L, card, cushion)
        openDebt("small", oct, principal = 2_000_000L, payment = 130_000L, payDay = 16, rate = null)
        openDebt("big", oct, principal = 60_000_000L, payment = 2_000_000L, payDay = 1)
    }

    @Test
    fun d2026_11_13_closedDebt_earlyFromCushion_keepsUnallocated() {
        val l = ledgerWithLoan()
        l.pay("small", LocalDate(2026, 10, 5), 129_000L)
        l.pay("small", LocalDate(2026, 11, 6), 129_000L)
        l.pay("big", LocalDate(2026, 11, 6), 2_000_000L)
        val before = l.state(LocalDate(2026, 11, 11)).month.unallocated
        l.pay("small", LocalDate(2026, 11, 12), 1_871_000L, from = l.cushion, early = true)
        l.close("small", LocalDate(2026, 11, 12))
        val after = l.state(LocalDate(2026, 11, 13)).month
        assertEquals(before + Money.rub(130_000L - 129_000L), after.unallocated, "закрытый кредит: план = обычный платёж с жизни")
        assertEquals(Money.rub(129_000L), after.debts.first { it.debt.id == "small" }.budgetPlanned)
    }

    @Test
    fun d2026_11_13_closedMidMonth_leavesCurrentObligatory_andPastMonth() {
        val l = ledgerWithLoan()
        l.pay("small", LocalDate(2026, 11, 6), 129_000L)
        l.pay("small", LocalDate(2026, 11, 12), 1_871_000L, from = l.cushion, early = true)
        l.close("small", LocalDate(2026, 11, 12))
        val now = l.state(LocalDate(2026, 11, 13))
        assertEquals(Money.rub(2_000_000L), now.obligatoryMonthly)
        assertEquals(Money.rub(2_000_000L), now.debtMonths.last().obligatory)
        val later = l.state(LocalDate(2026, 12, 10))
        assertEquals(Money.rub(2_000_000L), later.debtMonths.first { it.period.start == nov }.obligatory, "прошлый месяц с закрытием — без закрытого кредита")
    }

    @Test
    fun d2026_11_10_earlyFromCushion_doesNotTurnSavingsRed() {
        val l = ledgerWithLoan()
        l.move(LocalDate(2026, 11, 6), 9_000_000L, l.card, l.cushion)
        l.pay("big", LocalDate(2026, 11, 12), 5_000_000L, from = l.cushion, early = true)
        val month = l.state(LocalDate(2026, 11, 13)).month
        assertEquals(Money.rub(9_000_000L), month.saved)
        assertEquals(Money.rub(4_000_000L), month.cushionNet)
    }

    @Test
    fun review1_foreignLoanPaidFromDollarAccount_isPaid() {
        val l = Ledger()
        val loan = Debt("usdloan", "Кредит USD", Currency.USD, Money(1_000_000L, Currency.USD), Money(50_000L, Currency.USD), null, payDay = 10, openedOn = oct)
        l.debts += loan
        val paidAtCost = Money.rub(4_500_000L)
        l.transactions += Transaction.Expense("p", LocalDate(2026, 11, 10), l.usd.id, Money(50_000L, Currency.USD), l.loans.id, null, paidAtCost, RateSource.API, debtId = loan.id)
        val obligation = l.state(LocalDate(2026, 11, 20)).month.debts.single()
        assertEquals(ObligationStatus.PAID, obligation.status)
        assertTrue(obligation.left.isZero)
        assertTrue(obligation.regularLeft.isZero)
    }

    @Test
    fun review1_dollarFund_contributionByExchange_isDone() {
        val l = Ledger()
        val usdFund = Account("usdfund", "Копилка USD", Currency.USD, AccountKind.ACCOUNT, false, Money(0L, Currency.USD), isFund = true, fundMonthly = Money(10_000L, Currency.USD))
        l.accounts += usdFund
        l.exchange(LocalDate(2026, 11, 6), 800_000L, 10_000L, to = usdFund)
        val fund = l.state(LocalDate(2026, 11, 20)).month.funds.first { it.account.id == usdFund.id }
        assertTrue(fund.contributionLeft.isZero)
        assertEquals(ObligationStatus.PAID, fund.status)
    }

    @Test
    fun review2_archivingLifeAccountKeepsCarryOver() {
        val l = Ledger()
        l.income(LocalDate(2026, 10, 10), 2_000_000L, l.cash)
        l.income(LocalDate(2026, 10, 10), 3_000_000L)
        l.move(LocalDate(2026, 11, 6), 2_000_000L, l.cash, l.card)
        val before = l.state(LocalDate(2026, 11, 13)).month.carryOver
        l.archive(l.cash, LocalDate(2026, 11, 13))
        assertEquals(before, l.state(LocalDate(2026, 11, 13)).month.carryOver)
        assertEquals(Money.rub(5_000_000L), before)
    }

    @Test
    fun realData2026_11_13_longArchivedAccountWithBalanceStaysOutOfCarryOver() {
        val l = Ledger()
        l.income(LocalDate(2026, 8, 10), 40_000_000L, l.cash)
        l.archive(l.cash, LocalDate(2026, 9, 30))
        l.income(LocalDate(2026, 10, 10), 3_000_000L)
        assertEquals(Money.rub(3_000_000L), l.state(LocalDate(2026, 11, 13)).month.carryOver)
        assertEquals(Money.rub(40_000_000L), l.state(LocalDate(2026, 11, 13)).let { it.copy(period = l.periodOf(LocalDate(2026, 9, 10))) }.month.carryOver)
    }

    @Test
    fun review3_futureMonthCarryOverIsProjection() {
        val l = ledgerWithLoan()
        val today = LocalDate(2026, 11, 25)
        val current = l.state(today)
        val next = current.copy(period = l.periodOf(LocalDate(2026, 12, 5)), budgetLines = emptyList(), savingsTarget = null)
        val projection = requireNotNull(next.carryOverProjection)
        assertEquals(projection.lifeNow - projection.stillToPay, next.month.carryOver)
        assertTrue(projection.stillToPay >= current.month.debtsLeft)
        assertNull(current.carryOverProjection)
    }

    @Test
    fun review4_reducedPaymentFromNextMonth_currentMonthStillOwesOld() {
        val l = ledgerWithLoan()
        l.pay("big", LocalDate(2026, 11, 3), 5_000_000L, early = true)
        l.changePayment("big", LocalDate(2026, 12, 5), 1_500_000L)
        val state = l.state(LocalDate(2026, 11, 13))
        val dom = state.month.debts.first { it.debt.id == "big" }
        assertEquals(Money.rub(2_000_000L), dom.regularLeft, "в ноябре ещё старый платёж")
    }

    @Test
    fun review5_loanAddedAfterPaymentDay_isNotOverdueThisMonth() {
        val l = Ledger()
        l.planMonth(nov, income = 10_000_000L, food = 1_000_000L)
        val without = l.state(LocalDate(2026, 11, 14)).month.unallocated
        l.openDebt("late", LocalDate(2026, 11, 13), principal = 10_000_000L, payment = 3_000_000L, payDay = 6)
        val state = l.state(LocalDate(2026, 11, 14))
        assertTrue(state.month.debts.none { it.debt.id == "late" })
        assertEquals(without, state.month.unallocated)
        assertEquals(Money.rub(0L), state.debtMonths.last().obligatory)
    }

    @Test
    fun review8_planFactChartUsesEnvelopeSpendOnly() {
        val l = ledgerWithLoan()
        l.spend(LocalDate(2026, 11, 7), 500_000L)
        l.pay("big", LocalDate(2026, 11, 6), 2_000_000L)
        val state = l.state(LocalDate(2026, 11, 13))
        val point = state.monthlyHistory.last()
        assertEquals(Money.rub(500_000L), point.envelopeExpense)
        assertEquals(state.month.flexibleFact, point.envelopeExpense)
    }

    @Test
    fun review11_pastEarlyRepaymentIsNotPaidOffByTodaysBalance() {
        val l = Ledger()
        l.openDebt("d", LocalDate(2026, 8, 5), principal = 10_000_000L, payment = 1_000_000L, payDay = 20, rate = null)
        l.pay("d", LocalDate(2026, 9, 10), 1_000_000L, early = true)
        l.pay("d", LocalDate(2026, 10, 20), 7_500_000L, early = true)
        val september = l.state(LocalDate(2026, 11, 13)).let { it.copy(period = l.periodOf(LocalDate(2026, 9, 25)), budgetLines = emptyList()) }.month
        assertFalse(september.debts.single().paidOff)
        assertEquals(ObligationStatus.OVERDUE, september.debts.single().status)
    }

    @Test
    fun review12_templateEarlyPaymentNotInPastMonths() {
        val l = ledgerWithLoan()
        val index = l.debts.indexOfFirst { it.id == "big" }
        l.debts[index] = l.debts[index].copy(extraPayment = Money.rub(1_000_000L))
        val october = l.state(LocalDate(2026, 11, 13)).let { it.copy(period = l.periodOf(LocalDate(2026, 10, 20))) }.month
        assertTrue(october.debts.first { it.debt.id == "big" }.earlyPlanned.isZero)
        val november = l.state(LocalDate(2026, 11, 13)).month
        assertEquals(Money.rub(1_000_000L), november.debts.first { it.debt.id == "big" }.earlyPlanned)
    }

    @Test
    fun review13_debtsLeftExcludesEarlyPlannedFromCushion() {
        val l = ledgerWithLoan()
        l.planEarly("big", nov, 10_000_000L, fromCushion = true)
        l.pay("big", LocalDate(2026, 11, 6), 2_000_000L)
        l.pay("small", LocalDate(2026, 11, 6), 130_000L)
        val month = l.state(LocalDate(2026, 11, 13)).month
        assertTrue(month.debtsLeft.isZero)
        assertEquals(month.debtsPlanned, Money.rub(2_000_000L + 130_000L))
    }

    @Test
    fun dueDateHelperStillClampsShortMonths() {
        assertEquals(LocalDate(2026, 11, 30), MonthModel.dueDate(Ledger().periodOf(LocalDate(2026, 11, 10)), 31))
    }
}
