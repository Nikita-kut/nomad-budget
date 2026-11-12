package ru.nomadbudget.domain.logic

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

enum class ObligationStatus { PAID, OVERDUE, SOON, UPCOMING }

enum class SavingsProgress { NONE, DONE, PENDING, MISSED }

data class DebtObligation(
    val debt: Debt,
    val due: LocalDate?,
    val planned: Money,
    val paid: Money,
    val status: ObligationStatus,
    val regularPaid: Money = paid,
    val earlyPaid: Money = Money.zero(paid.currency),
    val paidOff: Boolean = false,
    val earlyPlanned: Money = Money.zero(paid.currency),
    val earlyFromCushion: Boolean = false,
) {
    val regularPlanned: Money get() = planned - earlyPlanned

    val budgetPlanned: Money get() = if (earlyFromCushion) regularPlanned else planned

    val left: Money get() = if (paid >= planned) Money.zero(paid.currency) else planned - paid
}

data class FundState(
    val account: Account,
    val balance: Money,
    val monthlyPlanned: Money?,
    val contributed: Money,
    val spent: Money,
    val goal: Money?,
    val due: LocalDate?,
    val status: ObligationStatus?,
) {
    val contributionLeft: Money
        get() = monthlyPlanned?.let { if (contributed >= it) Money.zero(contributed.currency) else it - contributed } ?: Money.zero(contributed.currency)

    val isEnough: Boolean? get() = goal?.let { balance + contributionLeft >= it }
}

data class MonthModel(
    val base: Currency,
    val period: Period,
    val today: LocalDate,
    val incomeBudgets: List<CategoryBudget>,
    val envelopes: List<CategoryBudget>,
    val debts: List<DebtObligation>,
    val funds: List<FundState>,
    val savingsTarget: Money?,
    val savedIn: Money,
    val savedOut: Money,
    val spentFromCushion: Money,
    val cushionToDebts: Money = Money.zero(base),
    val corrections: Money,
    val carryOver: Money = Money.zero(base),
) {
    val incomePlanned: Money = incomeBudgets.map { it.planned }.sumIn(base)
    val incomeFact: Money = incomeBudgets.map { it.fact }.sumIn(base)

    val flexiblePlanned: Money = envelopes.map { it.planned }.sumIn(base)
    val flexibleFact: Money = envelopes.map { it.fact }.sumIn(base)
    val flexiblePlannedWhole: Money = envelopes.map { it.planned.withoutFraction() }.sumIn(base)
    val flexibleFactWhole: Money = envelopes.map { it.fact.withoutFraction() }.sumIn(base)

    val canSpend: Money = flexiblePlannedWhole - flexibleFactWhole

    val isCurrent: Boolean = today in period
    val isPast: Boolean = today >= period.endExclusive
    val daysLeft: Int = if (isCurrent) period.lengthDays - period.dayNumber(today) + 1 else 0
    val elapsedShare: Double = when {
        isCurrent -> period.dayNumber(today).toDouble() / period.lengthDays
        isPast -> 1.0
        else -> 0.0
    }

    val perDay: Money? = if (daysLeft > 0 && canSpend.minor > 0L) Money(canSpend.minor / daysLeft, base).withoutFraction() else null

    val debtsPlanned: Money = debts.map { it.budgetPlanned }.sumIn(base)
    val debtsFromCushion: Money = debts.filter { it.earlyFromCushion }.map { it.earlyPlanned }.sumIn(base)
    val fundsPlanned: Money = funds.mapNotNull { it.monthlyPlanned }.sumIn(base)
    val obligationsPlanned: Money = debtsPlanned + fundsPlanned
    val obligationsCount: Int = debts.size + funds.count { it.monthlyPlanned != null }
    val debtsLeft: Money = debts.map { it.left }.sumIn(base)
    val debtsPaid: Int = debts.count { it.status == ObligationStatus.PAID }
    val debtsOverdue: Boolean = debts.any { it.status == ObligationStatus.OVERDUE }
    val nextObligation: DebtObligation? = debts.filter { it.status != ObligationStatus.PAID }.sortedWith(compareBy(nullsLast()) { it.due }).firstOrNull()

    val cushionNet: Money = savedIn - savedOut - spentFromCushion
    val takenFromCushion: Money = savedOut + spentFromCushion
    val cushionOtherOut: Money = takenFromCushion - cushionToDebts
    val saved: Money = savedIn - cushionOtherOut

    val savingsProgress: SavingsProgress = when {
        savingsTarget == null || savingsTarget.minor <= 0L -> SavingsProgress.NONE
        saved.wholeUnits >= savingsTarget.wholeUnits -> SavingsProgress.DONE
        isPast -> SavingsProgress.MISSED
        else -> SavingsProgress.PENDING
    }

    val savingsLeft: Money = savingsTarget?.let { if (saved >= it) Money.zero(base) else it - saved.coerceAtLeastZero() } ?: Money.zero(base)

    val available: Money = incomePlanned + carryOver

    val unallocated: Money = available - obligationsPlanned - flexiblePlanned - (savingsTarget ?: Money.zero(base))

    val allocatedShare: Double? =
        if (available.minor > 0L) (available - unallocated).minor.toDouble() / available.minor else null

    val hasPlan: Boolean = !flexiblePlanned.isZero || !incomePlanned.isZero || (savingsTarget?.isZero == false) || obligationsCount > 0

    companion object {
        const val SOON_DAYS = 3

        fun build(
            period: Period,
            today: LocalDate,
            categories: List<Category>,
            subcategories: List<Subcategory>,
            lines: List<BudgetLine>,
            transactions: List<Transaction>,
            accounts: List<Account>,
            debts: List<Debt>,
            rates: RateTable,
            balances: Map<String, Money>,
            savingsTarget: Money?,
        ): MonthModel {
            val base = rates.base
            val carryOver = accounts
                .filter { !it.isArchived && it.role == AccountRole.LIFE }
                .mapNotNull { rates.toBaseOrNull(BalanceCalculator.balanceBefore(it, transactions, period.start)) }
                .sumIn(base)
            val inPeriod = transactions.filter { it.date in period }
            val systemIds = categories.filter { it.isSystem }.map { it.id }.toSet()
            val visible = categories.filter { !it.isSystem && (!it.isArchived || inPeriod.any { tx -> tx.categoryIdOrNull == it.id }) }
            val budgets = BudgetCalculator.categoryBudgets(visible, lines, inPeriod, period, subcategories, accounts, base)
            val cushionIds = accounts.filter { it.role == AccountRole.CUSHION }.map { it.id }.toSet()
            val corrections = inPeriod.mapNotNull {
                when (it) {
                    is Transaction.Income -> it.amountBase.takeIf { _ -> it.categoryId in systemIds }
                    is Transaction.Expense -> (-it.amountBase).takeIf { _ -> it.categoryId in systemIds }
                    is Transaction.Transfer, is Transaction.Exchange -> null
                }
            }.sumIn(base)
            return MonthModel(
                base = base,
                period = period,
                today = today,
                incomeBudgets = budgets.filter { it.category.kind == CategoryKind.INCOME },
                envelopes = budgets.filter { it.category.kind == CategoryKind.EXPENSE },
                debts = debts.filter { it.isActiveIn(period) }.map { debtObligation(it, period, today, inPeriod, rates) },
                funds = accounts.filter { it.role == AccountRole.FUND && !it.isArchived }.map { fundState(it, period, today, inPeriod, rates, balances, systemIds) },
                savingsTarget = savingsTarget?.takeIf { it.minor > 0L },
                carryOver = carryOver,
                savedIn = BudgetCalculator.cushionInflow(inPeriod, cushionIds, systemIds, base),
                savedOut = BudgetCalculator.cushionOutflow(inPeriod, cushionIds, base),
                spentFromCushion = BudgetCalculator.cushionSpent(inPeriod, cushionIds, systemIds, base),
                cushionToDebts = BudgetCalculator.cushionToDebts(inPeriod, cushionIds, systemIds, base),
                corrections = corrections,
            )
        }

        fun dueDate(period: Period, day: Int?): LocalDate? {
            if (day == null) return null
            var month = LocalDate(period.start.year, period.start.month, 1)
            repeat(2) {
                val next = month.plus(1, DateTimeUnit.MONTH)
                val length = month.daysUntil(next)
                val candidate = LocalDate(month.year, month.month, day.coerceIn(1, length))
                if (candidate in period) return candidate
                month = next
            }
            return null
        }

        private fun status(paidEnough: Boolean, due: LocalDate?, today: LocalDate, period: Period): ObligationStatus = when {
            paidEnough -> ObligationStatus.PAID
            today >= period.endExclusive -> ObligationStatus.OVERDUE
            due == null -> ObligationStatus.UPCOMING
            today > due && today in period -> ObligationStatus.OVERDUE
            today in period && today.daysUntil(due) <= SOON_DAYS -> ObligationStatus.SOON
            else -> ObligationStatus.UPCOMING
        }

        private fun debtObligation(debt: Debt, period: Period, today: LocalDate, inPeriod: List<Transaction>, rates: RateTable): DebtObligation {
            val base = rates.base
            val due = dueDate(period, debt.payDay)
            val monthly = debt.paymentOn(due ?: period.start)
            val earlyPlan = debt.earlyPlanFor(period)
            val earlyPlanned = earlyPlan?.let { rates.toBaseOrNull(it.amount) } ?: Money.zero(base)
            val regularPlanned = rates.toBaseOrNull(monthly) ?: Money.zero(base)
            val planned = regularPlanned + earlyPlanned
            val payments = inPeriod.filterIsInstance<Transaction.Expense>().filter { it.debtId == debt.id }
            val regularPaid = payments.filterNot { it.debtEarly }.map { it.amountBase }.sumIn(base)
            val earlyPaid = payments.filter { it.debtEarly }.map { it.amountBase }.sumIn(base)
            val paid = regularPaid + earlyPaid
            val closedHere = debt.closedIn(period)
            val paidOff = closedHere || earlyPaid.minor > 0L && debt.principalRemaining.minor < debt.monthlyPayment.minor
            val paidEnough = paidOff ||
                regularPlanned.minor > 0L && regularPaid.wholeUnits >= regularPlanned.wholeUnits ||
                regularPlanned.isZero && paid.minor > 0L
            return DebtObligation(
                debt = debt,
                due = due,
                planned = if (closedHere) paid else planned,
                paid = paid,
                status = status(paidEnough, due, today, period),
                regularPaid = regularPaid,
                earlyPaid = earlyPaid,
                paidOff = paidOff,
                earlyPlanned = if (closedHere) Money.zero(base) else earlyPlanned,
                earlyFromCushion = earlyPlan?.fromCushion == true,
            )
        }

        private fun fundState(
            fund: Account,
            period: Period,
            today: LocalDate,
            inPeriod: List<Transaction>,
            rates: RateTable,
            balances: Map<String, Money>,
            systemIds: Set<String>,
        ): FundState {
            val base = rates.base
            val ids = setOf(fund.id)
            val contributed = BudgetCalculator.cushionInflow(inPeriod, ids, systemIds, base) - BudgetCalculator.cushionOutflow(inPeriod, ids, base)
            val spent = BudgetCalculator.cushionSpent(inPeriod, ids, systemIds, base)
            val monthly = fund.fundMonthly?.let(rates::toBaseOrNull)?.takeIf { it.minor > 0L }
            val due = dueDate(period, fund.fundDay)
            return FundState(
                account = fund,
                balance = balances[fund.id]?.let(rates::toBaseOrNull) ?: Money.zero(base),
                monthlyPlanned = monthly,
                contributed = contributed,
                spent = spent,
                goal = fund.fundGoal?.let(rates::toBaseOrNull)?.takeIf { it.minor > 0L },
                due = due,
                status = monthly?.let { status(contributed.wholeUnits >= it.wholeUnits, null, today, period) },
            )
        }
    }
}
