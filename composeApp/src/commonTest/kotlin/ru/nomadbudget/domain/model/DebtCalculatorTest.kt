package ru.nomadbudget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DebtCalculatorTest {

    private val mortgage = Debt(
        id = "m", name = "Ипотека", currency = Currency.RUB,
        principalRemaining = Money.rub(720_000_000L), monthlyPayment = Money.rub(3_388_200L), ratePercent = 3.5, payDay = 22,
    )

    @Test
    fun monthlyInterest_fromRate() {
        assertEquals(Money.rub(2_100_000L), DebtCalculator.monthlyInterest(mortgage))
    }

    @Test
    fun principalPart_isPaymentMinusInterest() {
        assertEquals(Money.rub(1_288_200L), DebtCalculator.principalPart(mortgage, mortgage.monthlyPayment))
        assertEquals(Money.rub(718_711_800L), DebtCalculator.afterPayment(mortgage, mortgage.monthlyPayment).principalRemaining)
    }

    @Test
    fun principalPart_withoutRate_isFullPayment() {
        val installment = mortgage.copy(ratePercent = null, principalRemaining = Money.rub(2_000_000L))
        assertEquals(Money.rub(767_900L), DebtCalculator.principalPart(installment, Money.rub(767_900L)))
    }

    @Test
    fun principalPart_neverNegative_andNeverAboveRemaining() {
        assertEquals(Money.zero(Currency.RUB), DebtCalculator.principalPart(mortgage, Money.rub(100_000L)))
        val small = mortgage.copy(principalRemaining = Money.rub(500_000L), ratePercent = null)
        assertEquals(Money.rub(500_000L), DebtCalculator.principalPart(small, Money.rub(900_000L)))
    }

    @Test
    fun earlyPayment_goesFullyToPrincipal() {
        assertEquals(Money.rub(10_000_000L), DebtCalculator.principalFor(mortgage, Money.rub(10_000_000L), early = true))
        assertEquals(Money.rub(1_288_200L), DebtCalculator.principalFor(mortgage, mortgage.monthlyPayment, early = false))
    }

    @Test
    fun earlyPayment_cappedByRemaining() {
        val almostPaid = mortgage.copy(principalRemaining = Money.rub(500_000L))
        assertEquals(Money.rub(500_000L), DebtCalculator.principalFor(almostPaid, Money.rub(900_000L), early = true))
    }

    @Test
    fun principalFor_otherCurrency_isNull() {
        assertEquals(null, DebtCalculator.principalFor(mortgage, Money(10_000L, Currency.USD), early = true))
    }
}
