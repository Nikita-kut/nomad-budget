package ru.nomadbudget.domain.logic

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

data class CapitalGoal(
    val id: String,
    val name: String,
    val amount: Money,
    val targetDate: LocalDate,
)

data class CapitalSettings(
    val cushionMonths: Int = DEFAULT_CUSHION_MONTHS,
    val annualReturn: Double = 0.0,
) {
    companion object {
        const val DEFAULT_CUSHION_MONTHS = 6
        val CUSHION_MONTHS_RANGE: IntRange = 1..36
        const val MAX_ANNUAL_RETURN = 100.0
    }
}

data class CapitalMonth(
    val period: Period,
    val cushionIn: Money,
    val cushionOut: Money,
    val cushionSpent: Money,
    val principalPaid: Money,
    val expenses: Money,
    val isCurrent: Boolean,
    val cushionToDebts: Money = Money.zero(Currency.BASE),
) {
    val cushionGone: Money get() = cushionOut + cushionSpent

    val saved: Money get() = cushionIn - cushionGone

    val capitalGrowth: Money get() = saved + principalPaid
}

enum class Scenario { PLAN, FACT, FACT_WITH_RETURN }

data class GoalProgress(
    val goal: CapitalGoal,
    val monthsLeft: Int,
    val requiredMonthly: Money,
    val reachableByPace: Boolean,
    val overdue: Boolean = false,
)

data class CapitalModel(
    val today: LocalDate,
    val life: Money,
    val funds: Money,
    val cushion: Money,
    val debt: Money,
    val months: List<CapitalMonth>,
    val planMonthly: Money?,
    val settings: CapitalSettings,
    val debtSchedule: List<Money>,
    val goals: List<CapitalGoal>,
) {
    val assets: Money get() = life + funds + cushion

    val netWorth: Money get() = assets - debt

    val closedMonths: List<CapitalMonth> = months.filterNot { it.isCurrent }

    private val recent: List<CapitalMonth> = closedMonths.takeLast(PACE_MONTHS)

    val paceMonths: Int = recent.size

    val paceAverage: Money? = recent.takeIf { it.isNotEmpty() }?.let { list -> Money(list.sumOf { it.saved.minor } / list.size, Currency.BASE) }

    val paceMedian: Money? = recent.takeIf { it.isNotEmpty() }?.let { list ->
        Money(median(list.map { it.saved.minor }), Currency.BASE)
    }

    val principalPace: Money = recent.takeIf { it.isNotEmpty() }?.let { list -> Money(median(list.map { it.principalPaid.minor }), Currency.BASE) }
        ?: Money.zero(Currency.BASE)

    val averageExpense: Money? = recent.takeIf { it.isNotEmpty() }?.let { list -> Money(median(list.map { it.expenses.minor }), Currency.BASE) }

    val cushionTarget: Money? = averageExpense?.let { Money(it.minor * settings.cushionMonths, Currency.BASE) }

    val cushionMonthsCovered: Double? = averageExpense?.takeIf { it.minor > 0L }?.let { cushion.minor.toDouble() / it.minor }

    val debtFreeInMonths: Int? = debtSchedule.indexOfFirst { it.minor <= 0L }.takeIf { it >= 0 }

    fun monthly(scenario: Scenario): Money = when (scenario) {
        Scenario.PLAN -> planMonthly ?: Money.zero(Currency.BASE)
        Scenario.FACT, Scenario.FACT_WITH_RETURN -> paceMedian ?: Money.zero(Currency.BASE)
    }

    fun cushionPath(scenario: Scenario, horizon: Int): List<Money> {
        val effectiveRate = if (scenario == Scenario.FACT_WITH_RETURN) settings.annualReturn / PERCENT / MONTHS else 0.0
        val add = monthly(scenario).minor.toDouble()
        val path = ArrayList<Money>(horizon + 1)
        var balance = cushion.minor.toDouble()
        path += Money(balance.toLong(), Currency.BASE)
        repeat(horizon) {
            balance = balance * (1 + effectiveRate) + add
            path += Money(balance.toLong(), Currency.BASE)
        }
        return path
    }

    fun capitalPath(scenario: Scenario, horizon: Int): List<Money> {
        val cushionPath = cushionPath(scenario, horizon)
        return cushionPath.mapIndexed { month, cushionValue ->
            val debtLeft = debtSchedule.getOrElse(month) { debtSchedule.lastOrNull() ?: Money.zero(Currency.BASE) }
            life + funds + cushionValue - debtLeft
        }
    }

    fun monthsToReach(target: Money, scenario: Scenario): Int? {
        if (cushion >= target) return 0
        val path = cushionPath(scenario, MAX_HORIZON)
        return path.indexOfFirst { it >= target }.takeIf { it >= 0 }
    }

    fun goalProgress(goal: CapitalGoal): GoalProgress {
        val overdue = goal.targetDate < today
        val monthsLeft = monthsBetween(today, goal.targetDate).coerceAtLeast(1)
        val missing = goal.amount - cushion
        val required = if (missing.isNegative || missing.isZero) Money.zero(Currency.BASE) else Money(missing.minor / monthsLeft, Currency.BASE)
        val pace = paceMedian ?: Money.zero(Currency.BASE)
        return GoalProgress(goal, monthsLeft, required, reachableByPace = !overdue && pace >= required, overdue = overdue)
    }

    fun dateAfterMonths(months: Int): LocalDate = today.plus(months, DateTimeUnit.MONTH)

    companion object {
        const val PACE_MONTHS = 6
        const val MAX_HORIZON = 600
        const val HISTORY_MONTHS = 12
        const val YEAR_MONTHS = 12
        private const val PERCENT = 100.0
        private const val MONTHS = 12.0
        private const val COARSE_HORIZON = 24
        private const val COARSE_STEP = 3

        fun forecastIndices(horizon: Int): List<Int> = (0..horizon step (if (horizon > COARSE_HORIZON) COARSE_STEP else 1)).toList()

        fun build(
            today: LocalDate,
            accounts: List<Account>,
            balances: Map<String, Money>,
            transactions: List<Transaction>,
            debts: List<Debt>,
            rates: RateTable,
            systemCategoryIds: Set<String>,
            planMonthly: Money?,
            settings: CapitalSettings,
            goals: List<CapitalGoal>,
            startDay: Int,
        ): CapitalModel {
            fun sumRole(role: AccountRole) = accounts.filter { !it.isArchived && it.role == role }
                .mapNotNull { rates.toBaseOrNull(balances[it.id] ?: Money.zero(it.currency)) }
                .sumIn(Currency.BASE)
            val openDebts = debts.filterNot { it.isClosed }
            val current = SalaryCycle.periodContaining(today, startDay)
            val periods = generateSequence(current) { SalaryCycle.previous(it, startDay) }.take(HISTORY_MONTHS + 1).toList().reversed()
            val cushionIds = accounts.filter { it.role == AccountRole.CUSHION }.map { it.id }.toSet()
            val months = periods.map { period ->
                val inPeriod = transactions.filter { it.date in period }
                val expenses = inPeriod.filterIsInstance<Transaction.Expense>().filter { it.categoryId !in systemCategoryIds }
                CapitalMonth(
                    period = period,
                    cushionIn = BudgetCalculator.cushionInflow(inPeriod, cushionIds, systemCategoryIds),
                    cushionOut = BudgetCalculator.cushionOutflow(inPeriod, cushionIds),
                    cushionSpent = BudgetCalculator.cushionSpent(inPeriod, cushionIds, systemCategoryIds),
                    cushionToDebts = BudgetCalculator.cushionToDebts(inPeriod, cushionIds, systemCategoryIds),
                    principalPaid = expenses.mapNotNull { tx -> tx.debtPrincipal?.let(rates::toBaseOrNull) }.sumIn(Currency.BASE),
                    expenses = expenses.map { it.amountBase }.sumIn(Currency.BASE),
                    isCurrent = period == current,
                )
            }.dropWhile { month -> month.cushionIn.isZero && month.cushionOut.isZero && month.expenses.isZero && !month.isCurrent }
            return CapitalModel(
                today = today,
                life = sumRole(AccountRole.LIFE),
                funds = sumRole(AccountRole.FUND),
                cushion = sumRole(AccountRole.CUSHION),
                debt = openDebts.mapNotNull { rates.toBaseOrNull(it.principalRemaining) }.sumIn(Currency.BASE),
                months = months,
                planMonthly = planMonthly?.takeIf { it.minor > 0L },
                settings = settings,
                debtSchedule = debtSchedule(openDebts, rates, MAX_HORIZON),
                goals = goals.sortedBy { it.targetDate },
            )
        }

        fun debtSchedule(debts: List<Debt>, rates: RateTable, horizon: Int): List<Money> {
            val states = debts.map { it }.toMutableList()
            val result = ArrayList<Money>(horizon + 1)
            fun total() = states.mapNotNull { rates.toBaseOrNull(it.principalRemaining) }.sumIn(Currency.BASE)
            result += total()
            repeat(horizon) {
                for (i in states.indices) {
                    val debt = states[i]
                    if (debt.principalRemaining.minor <= 0L) continue
                    val payment = debt.plannedPayment
                    val interest = DebtCalculator.monthlyInterest(debt)
                    val principal = if (payment > interest) payment - interest else Money.zero(debt.currency)
                    val next = if (principal >= debt.principalRemaining) Money.zero(debt.currency) else debt.principalRemaining - principal
                    states[i] = debt.copy(principalRemaining = next)
                }
                result += total()
            }
            return result
        }

        fun median(values: List<Long>): Long {
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
        }

        fun monthsBetween(from: LocalDate, to: LocalDate): Int =
            (to.year - from.year) * MONTHS.toInt() + (to.month.ordinal - from.month.ordinal) - if (to.day < from.day) 1 else 0
    }
}
