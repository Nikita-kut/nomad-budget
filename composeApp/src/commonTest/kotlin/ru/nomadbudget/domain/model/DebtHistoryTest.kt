package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DebtHistoryTest {

    private val loan = Debt(
        id = "l", name = "Кредит", currency = Currency.USD,
        principalRemaining = Money(0L, Currency.USD), monthlyPayment = Money(50_000L, Currency.USD), ratePercent = 12.0, payDay = 5,
    )

    private fun payment(id: String, amount: Money, base: Money, principal: Money) = Transaction.Expense(
        id = id, date = LocalDate(2026, 10, 5), accountId = "a", amount = amount, categoryId = "c", subcategoryId = null,
        amountBase = base, rateSource = RateSource.API, debtId = loan.id, debtPrincipal = principal,
    )

    @Test
    fun interest_inDebtCurrency_whenBaseIsDifferent() {
        val payments = listOf(
            payment("1", Money(50_000L, Currency.USD), Money.rub(4_000_000L), Money(45_000L, Currency.USD)),
            payment("2", Money(50_000L, Currency.USD), Money.rub(4_200_000L), Money(46_000L, Currency.USD)),
        )
        val totals = DebtHistory.totals(loan, payments)
        assertEquals(Money(100_000L, Currency.USD), totals.paid)
        assertEquals(Money(91_000L, Currency.USD), totals.principal)
        assertEquals(Money(9_000L, Currency.USD), totals.interest)
    }

    @Test
    fun mixedCurrencies_paidInBase_withoutInterest() {
        val payments = listOf(
            payment("1", Money(50_000L, Currency.USD), Money.rub(4_000_000L), Money(45_000L, Currency.USD)),
            payment("2", Money.rub(4_200_000L), Money.rub(4_200_000L), Money(46_000L, Currency.USD)),
        )
        val totals = DebtHistory.totals(loan, payments)
        assertEquals(Money.rub(8_200_000L), totals.paid)
        assertNull(totals.interest)
    }

    @Test
    fun noPayments_zeroInDebtCurrency() {
        val totals = DebtHistory.totals(loan, emptyList())
        assertEquals(Money(0L, Currency.USD), totals.paid)
        assertNull(totals.principal)
    }
}
