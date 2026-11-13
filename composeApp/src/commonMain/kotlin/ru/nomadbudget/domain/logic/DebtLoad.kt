package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

data class DebtMonth(
    val period: Period,
    val regularPaid: Money,
    val earlyPaid: Money,
    val obligatory: Money,
    val debtAtEnd: Money,
    val income: Money,
    val isCurrent: Boolean,
) {
    val loadShare: Double? = if (!isCurrent && income.minor > 0L && obligatory.minor > 0L) obligatory.minor.toDouble() / income.minor else null
}

object DebtLoad {

    const val MAX_MONTHS = 36

    fun months(
        debts: List<Debt>,
        transactions: List<Transaction>,
        rates: RateTable,
        today: LocalDate,
        startDay: Int,
        systemCategoryIds: Set<String>,
    ): List<DebtMonth> {
        if (debts.isEmpty()) return emptyList()
        val base = rates.base
        val payments = transactions.filterIsInstance<Transaction.Expense>().filter { it.debtId != null }
        val first = (debts.mapNotNull { it.openedOn } + payments.map { it.date }).minOrNull() ?: today
        val current = SalaryCycle.periodContaining(today, startDay)
        val periods = generateSequence(current) { SalaryCycle.previous(it, startDay) }
            .takeWhile { it.endExclusive > first }
            .take(MAX_MONTHS)
            .toList()
            .reversed()
        return periods.map { period ->
            val inPeriod = payments.filter { it.date in period }
            val isCurrent = period == current
            DebtMonth(
                period = period,
                regularPaid = inPeriod.filterNot { it.debtEarly }.map { it.amountBase }.sumIn(base),
                earlyPaid = inPeriod.filter { it.debtEarly }.map { it.amountBase }.sumIn(base),
                obligatory = debts.filter { DebtSchedule.owesScheduled(it, period, payments) }
                    .mapNotNull { debt -> rates.toBaseOrNull(debt.paymentOn(MonthModel.dueDate(period, debt.payDay) ?: period.start)) }
                    .sumIn(base),
                debtAtEnd = debts.mapNotNull { debt -> rates.toBaseOrNull(remainingAt(debt, payments, period.endExclusive)) }.sumIn(base),
                income = transactions.filterIsInstance<Transaction.Income>()
                    .filter { it.date in period && it.categoryId !in systemCategoryIds }
                    .map { it.amountBase }
                    .sumIn(base),
                isCurrent = isCurrent,
            )
        }
    }

    fun remainingAt(debt: Debt, payments: List<Transaction.Expense>, moment: LocalDate): Money {
        val opened = debt.openedOn
        if (opened != null && opened >= moment) return Money.zero(debt.currency)
        val paidAfter = payments
            .filter { it.debtId == debt.id && it.date >= moment }
            .mapNotNull { it.debtPrincipal?.takeIf { p -> p.currency == debt.currency } }
            .sumIn(debt.currency)
        val closedOn = debt.closedOn
        val writtenOffAfter = if (debt.isClosed && closedOn != null && closedOn >= moment) debt.writtenOff else Money.zero(debt.currency)
        val remaining = debt.principalRemaining + paidAfter + writtenOffAfter
        return if (remaining.isNegative) Money.zero(debt.currency) else remaining
    }
}
