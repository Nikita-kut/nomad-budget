package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EarlyRepaymentTest {

    private val loan = Debt("d", "Кредит", Currency.RUB, Money.rub(57_936_045L + 5_000_000L), Money.rub(2_048_997L), 10.0, payDay = 15)

    @Test
    fun annuityMatchesTextbook() {
        val payment = DebtCalculator.annuityPayment(Money.rub(100_000_000L), 12.0, 12)
        assertEquals(Money.rub(8_884_900L), payment)
        assertEquals(Money.rub(100_000L), DebtCalculator.annuityPayment(Money.rub(1_200_000L), null, 12))
        assertNull(DebtCalculator.annuityPayment(Money.rub(0L), 12.0, 12))
    }

    @Test
    fun suggestedPaymentIsLowerAfterRepaymentForSameTerm() {
        val suggested = DebtCalculator.suggestedPayment(loan, Money.rub(5_000_000L))
        assertTrue(suggested != null && suggested < loan.monthlyPayment)
        assertNull(DebtCalculator.suggestedPayment(loan, loan.principalRemaining))
    }

    @Test
    fun paymentForDateFollowsHistory() {
        val changed = loan.copy(
            monthlyPayment = Money.rub(1_800_000L),
            paymentChanges = listOf(
                PaymentChange(LocalDate(2026, 10, 5), Money.rub(2_048_997L)),
                PaymentChange(LocalDate(2026, 12, 15), Money.rub(1_800_000L)),
            ),
        )
        assertEquals(Money.rub(2_048_997L), changed.paymentOn(LocalDate(2026, 11, 15)))
        assertEquals(Money.rub(1_800_000L), changed.paymentOn(LocalDate(2026, 12, 15)))
        assertEquals(Money.rub(1_800_000L), loan.copy(monthlyPayment = Money.rub(1_800_000L)).paymentOn(LocalDate(2026, 11, 15)))
    }
}
