package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.pow

enum class DebtCloseReason(val id: String) {
    PAID_OFF("paid_off"),
    EARLY("early"),
    MANUAL("manual");

    companion object {
        fun fromId(id: String?): DebtCloseReason? = entries.firstOrNull { it.id == id }
    }
}

data class PaymentChange(val effectiveFrom: LocalDate, val payment: Money)

enum class EarlyRepaymentMode { REDUCE_PAYMENT, REDUCE_TERM, PAY_OFF }

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
    val openedOn: LocalDate? = null,
    val closedOn: LocalDate? = null,
    val closeReason: DebtCloseReason? = null,
    val writtenOff: Money = Money.zero(currency),
    val paymentChanges: List<PaymentChange> = emptyList(),
) {
    init {
        require(principalRemaining.currency == currency && monthlyPayment.currency == currency && extraPayment.currency == currency) {
            "Суммы кредита должны быть в валюте кредита"
        }
    }

    val plannedPayment: Money get() = monthlyPayment + extraPayment

    fun paymentOn(date: LocalDate): Money =
        paymentChanges.filter { it.effectiveFrom <= date }.maxByOrNull { it.effectiveFrom }?.payment ?: monthlyPayment

    fun nextPaymentChange(after: LocalDate): PaymentChange? = paymentChanges.filter { it.effectiveFrom > after }.minByOrNull { it.effectiveFrom }

    val lessThanOnePayment: Boolean get() = !isClosed && monthlyPayment.minor > 0L && principalRemaining.minor < monthlyPayment.minor

    fun isActiveIn(period: Period): Boolean =
        (openedOn == null || openedOn < period.endExclusive) &&
            (if (isClosed) closedOn != null && closedOn >= period.start else true)

    fun closedIn(period: Period): Boolean = isClosed && closedOn != null && closedOn in period
}

data class DebtTotals(val paid: Money, val principal: Money?, val interest: Money?, val firstPayment: LocalDate?, val lastPayment: LocalDate?)

object DebtHistory {
    fun totals(debt: Debt, payments: List<Transaction.Expense>): DebtTotals {
        val own = payments.filter { it.debtId == debt.id }
        val paid = own.map { it.amountBase }.sumIn(Currency.BASE)
        val principalParts = own.mapNotNull { it.debtPrincipal }.filter { it.currency == debt.currency }
        val principal = principalParts.takeIf { it.isNotEmpty() }?.sumIn(debt.currency)
        val interest = principal?.takeIf { debt.currency == Currency.BASE }?.let { paid - it }?.takeUnless { it.isNegative }
        return DebtTotals(paid, principal, interest, own.minOfOrNull { it.date }, own.maxOfOrNull { it.date })
    }
}

data class DebtForecast(val months: Int, val totalInterest: Money)

object DebtCalculator {

    private const val MAX_MONTHS = 600

    fun forecast(debt: Debt, payment: Money): DebtForecast? {
        if (payment.currency != debt.currency || payment.minor <= 0L) return null
        var principal = debt.principalRemaining
        var interestTotal = Money.zero(debt.currency)
        var months = 0
        while (principal.minor > 0L && months < MAX_MONTHS) {
            val interest = monthlyInterest(debt.copy(principalRemaining = principal))
            if (payment <= interest) return null
            val part = payment - interest
            principal = if (part > principal) Money.zero(debt.currency) else principal - part
            interestTotal += interest
            months++
        }
        return if (principal.minor > 0L) null else DebtForecast(months, interestTotal)
    }

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

    fun earlyPrincipalPart(debt: Debt, payment: Money): Money {
        require(payment.currency == debt.currency) { "Платёж должен быть в валюте кредита" }
        return when {
            payment.isNegative -> Money.zero(debt.currency)
            payment > debt.principalRemaining -> debt.principalRemaining
            else -> payment
        }
    }

    fun annuityPayment(principal: Money, ratePercent: Double?, months: Int): Money? {
        if (months <= 0 || principal.minor <= 0L) return null
        val rate = (ratePercent ?: 0.0) / PERCENT / MONTHS_IN_YEAR
        val minor = if (rate <= 0.0) {
            principal.minor.toDouble() / months
        } else {
            principal.minor * rate / (1.0 - (1.0 + rate).pow(-months))
        }
        val factor = principal.currency.minorFactor
        return Money(ceil(minor / factor).toLong() * factor, principal.currency)
    }

    fun remainingPayments(debt: Debt): Int? = forecast(debt, debt.monthlyPayment)?.months

    fun suggestedPayment(debt: Debt, repayment: Money): Money? {
        val months = remainingPayments(debt) ?: return null
        val left = debt.principalRemaining - repayment
        return if (left.minor <= 0L) null else annuityPayment(left, debt.ratePercent, months)
    }

    fun principalFor(debt: Debt, payment: Money, early: Boolean): Money? = when {
        payment.currency != debt.currency -> null
        early -> earlyPrincipalPart(debt, payment)
        else -> principalPart(debt, payment)
    }
}
