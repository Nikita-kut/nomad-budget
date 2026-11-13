package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.Transaction

object DebtSchedule {

    fun opensAfterDue(debt: Debt, period: Period): Boolean {
        val opened = debt.openedOn ?: return false
        val due = MonthModel.dueDate(period, debt.payDay) ?: return false
        return opened in period && opened > due
    }

    fun inPlan(debt: Debt, period: Period, payments: List<Transaction.Expense>, today: LocalDate): Boolean =
        debt.isActiveIn(period) &&
            !(opensAfterDue(debt, period) && payments.none { it.debtId == debt.id && it.date in period } && debt.earlyPlanFor(period, today) == null)

    fun owesScheduled(debt: Debt, period: Period, payments: List<Transaction.Expense>): Boolean =
        debt.isActiveIn(period) && !debt.closedIn(period) &&
            !(opensAfterDue(debt, period) && payments.none { it.debtId == debt.id && !it.debtEarly && it.date in period })
}
