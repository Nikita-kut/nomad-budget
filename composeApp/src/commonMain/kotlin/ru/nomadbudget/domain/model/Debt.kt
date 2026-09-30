package ru.nomadbudget.domain.model

data class Debt(
    val id: String,
    val name: String,
    val currency: Currency,
    val principalRemaining: Money,
    val monthlyPayment: Money,
    val ratePercent: Double?,
    val payDay: Int?,
    val isClosed: Boolean = false,
    val extraPayment: Money = Money.zero(currency),
) {
    init {
        require(principalRemaining.currency == currency && monthlyPayment.currency == currency && extraPayment.currency == currency) {
            "Суммы кредита должны быть в валюте кредита"
        }
    }

    val plannedPayment: Money get() = monthlyPayment + extraPayment
}

object DebtCalculator {

    private const val MONTHS_IN_YEAR = 12.0
    private const val PERCENT = 100.0

    fun monthlyInterest(debt: Debt): Money {
        val rate = debt.ratePercent ?: return Money.zero(debt.currency)
        val interest = debt.principalRemaining.toMajor() * rate / PERCENT / MONTHS_IN_YEAR
        return Money.ofMajor(interest, debt.currency)
    }

    fun principalPart(debt: Debt, payment: Money): Money {
        require(payment.currency == debt.currency) { "Платёж должен быть в валюте кредита" }
        val principal = payment - monthlyInterest(debt)
        val bounded = when {
            principal.isNegative -> Money.zero(debt.currency)
            principal > debt.principalRemaining -> debt.principalRemaining
            else -> principal
        }
        return bounded
    }

    fun afterPayment(debt: Debt, payment: Money): Debt =
        debt.copy(principalRemaining = debt.principalRemaining - principalPart(debt, payment))
}
