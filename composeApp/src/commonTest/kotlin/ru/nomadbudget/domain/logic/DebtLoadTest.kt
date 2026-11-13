package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DefaultRates
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.PaymentChange
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.TEST_START_DAY
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DebtLoadTest {

    private val rates = DefaultRates.table()
    private val today = LocalDate(2026, 12, 20)

    private val mortgage = Debt(
        "m", "Ипотека", Currency.RUB, Money.rub(45_000_000L), Money.rub(1_500_000L), 10.0, payDay = 15,
        openedOn = LocalDate(2026, 10, 5),
        paymentChanges = listOf(PaymentChange(LocalDate(2026, 10, 5), Money.rub(2_000_000L)), PaymentChange(LocalDate(2026, 12, 15), Money.rub(1_500_000L))),
    )

    private fun payment(id: String, date: LocalDate, rub: Long, principal: Long, early: Boolean = false) = Transaction.Expense(
        id, date, "card", Money.rub(rub), "loans", null, Money.rub(rub), RateSource.API,
        debtId = "m", debtPrincipal = Money.rub(principal), debtEarly = early,
    )

    private fun income(date: LocalDate, rub: Long) = Transaction.Income("i$date", date, "card", Money.rub(rub), "salary", Money.rub(rub), RateSource.API)

    private val tx = listOf(
        payment("o", LocalDate(2026, 10, 15), 2_000_000L, 1_500_000L),
        payment("n", LocalDate(2026, 11, 15), 2_000_000L, 1_500_000L),
        payment("e", LocalDate(2026, 11, 20), 5_000_000L, 5_000_000L, early = true),
        payment("d", LocalDate(2026, 12, 15), 1_500_000L, 1_000_000L),
        income(LocalDate(2026, 10, 5), 10_000_000L),
        income(LocalDate(2026, 11, 5), 10_000_000L),
    )

    private val months = DebtLoad.months(listOf(mortgage), tx, rates, today, TEST_START_DAY, emptySet())

    @Test
    fun obligatoryPaymentDropsAfterChange() {
        assertEquals(listOf(2_000_000L, 2_000_000L, 1_500_000L), months.map { it.obligatory.minor })
    }

    @Test
    fun closedThisMonth_leavesCurrentObligation_butStaysInPastMonths() {
        val small = Debt(
            "s", "Рассрочка", Currency.RUB, Money.rub(0L), Money.rub(130_000L), null, payDay = 16,
            openedOn = LocalDate(2026, 10, 5), isClosed = true, closedOn = LocalDate(2026, 12, 12),
        )
        val withClosed = DebtLoad.months(listOf(mortgage, small), tx, rates, today, TEST_START_DAY, emptySet())
        assertEquals(listOf(2_130_000L, 2_130_000L, 1_500_000L), withClosed.map { it.obligatory.minor })
    }

    @Test
    fun earlyAndRegularPaymentsSeparate() {
        val november = months[1]
        assertEquals(Money.rub(2_000_000L), november.regularPaid)
        assertEquals(Money.rub(5_000_000L), november.earlyPaid)
    }

    @Test
    fun debtBalanceRebuiltBackwards() {
        assertEquals(listOf(52_500_000L, 46_000_000L, 45_000_000L), months.map { it.debtAtEnd.minor })
    }

    @Test
    fun loadOnlyForClosedMonthsWithIncome() {
        assertEquals(0.2, months[0].loadShare)
        assertNull(months[2].loadShare)
    }
}
