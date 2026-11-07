package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

data class MonthPoint(
    val period: Period,
    val income: Money,
    val expense: Money,
    val netSaved: Money,
) {
    val balance: Money get() = income - expense

    val savingsRate: Double? get() = if (income.minor > 0L) netSaved.minor.toDouble() / income.minor else null
}

data class CategoryTotal(val category: Category, val total: Money, val share: Double)

object HistoryCalculator {

    fun monthly(transactions: List<Transaction>, accounts: List<Account>, upTo: Period, systemIds: Set<String> = emptySet()): List<MonthPoint> {
        if (transactions.isEmpty()) return emptyList()
        val savingsIds = accounts.filter { it.isSavings }.map { it.id }.toSet()
        val first = SalaryCycle.periodContaining(transactions.minOf { it.date })
        val points = mutableListOf<MonthPoint>()
        var period = first
        while (period.start <= upTo.start) {
            val inPeriod = transactions.filter { it.date in period }
            val income = inPeriod.filterIsInstance<Transaction.Income>().filter { it.categoryId !in systemIds }.map { it.amountBase }.sumIn(Currency.BASE)
            val expense = inPeriod.filterIsInstance<Transaction.Expense>().filter { it.categoryId !in systemIds }.map { it.amountBase }.sumIn(Currency.BASE)
            val saved = BudgetCalculator.cushionInflow(inPeriod, savingsIds, systemIds)
            val taken = BudgetCalculator.cushionOutflow(inPeriod, savingsIds)
            val spent = BudgetCalculator.cushionSpent(inPeriod, savingsIds, systemIds)
            points += MonthPoint(period, income, expense, saved - taken - spent)
            period = SalaryCycle.next(period)
        }
        return points
    }

    fun expensesByCategory(
        transactions: List<Transaction>,
        categories: List<Category>,
        from: LocalDate,
        toExclusive: LocalDate,
    ): List<CategoryTotal> {
        val byId = categories.filter { it.kind == CategoryKind.EXPENSE && !it.isSystem }.associateBy { it.id }
        val totals = transactions
            .filterIsInstance<Transaction.Expense>()
            .filter { it.date >= from && it.date < toExclusive }
            .groupBy { it.categoryId }
            .mapNotNull { (id, list) -> byId[id]?.let { it to list.map { tx -> tx.amountBase }.sumIn(Currency.BASE) } }
        val grand = totals.map { it.second }.sumIn(Currency.BASE)
        return totals
            .map { (category, total) ->
                CategoryTotal(category, total, if (grand.minor > 0L) total.minor.toDouble() / grand.minor else 0.0)
            }
            .sortedByDescending { it.total.minor }
    }

    fun trackingStart(accounts: List<Account>, transactions: List<Transaction>): LocalDate? {
        val active = accounts.filterNot { it.isArchived }.map { it.id }.toSet()
        return transactions.filter { tx -> tx.touches(active) }.minOfOrNull { it.date }
    }

    fun netWorth(
        periods: List<Period>,
        accounts: List<Account>,
        transactions: List<Transaction>,
        rateOn: (Currency, LocalDate) -> Double?,
    ): List<Pair<Period, Money>> {
        val active = accounts.filterNot { it.isArchived }
        val start = trackingStart(accounts, transactions) ?: return emptyList()
        return periods.filter { it.endExclusive > start }.map { period ->
            period to active.mapNotNull { account ->
                val balance = BalanceCalculator.balanceBefore(account, transactions, period.endExclusive)
                when (account.currency) {
                    Currency.BASE -> balance
                    else -> rateOn(account.currency, period.lastDay)?.let { rate ->
                        Money((balance.minor.toDouble() / account.currency.minorFactor * rate * Currency.BASE.minorFactor).roundToLong(), Currency.BASE)
                    }
                }
            }.sumIn(Currency.BASE)
        }
    }

    private fun Transaction.touches(ids: Set<String>): Boolean = when (this) {
        is Transaction.Expense -> accountId in ids
        is Transaction.Income -> accountId in ids
        is Transaction.Transfer -> fromAccountId in ids || toAccountId in ids
        is Transaction.Exchange -> fromAccountId in ids || toAccountId in ids
    }

    fun plannedExpenseByPeriod(lines: List<PeriodBudgetLine>, categories: List<Category>): Map<LocalDate, Money> {
        val expenseIds = categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()
        return lines
            .filter { it.line.categoryId in expenseIds }
            .groupBy { it.periodStart }
            .mapValues { (_, list) -> list.map { it.line.planned }.sumIn(Currency.BASE) }
    }

    fun savingsForecast(
        points: List<MonthPoint>,
        currentPeriodOpen: Boolean,
        savingsBalance: Money,
        monthlyTarget: Money?,
    ): SavingsForecast? {
        val closed = if (currentPeriodOpen) points.dropLast(1) else points
        val recent = closed.takeLast(FORECAST_MONTHS)
        val target = monthlyTarget?.takeIf { it.minor > 0L }
        if (recent.isEmpty() && target == null) return null
        val actualPerMonth = if (recent.isEmpty()) null else Money(recent.map { it.netSaved.minor }.sum() / recent.size, Currency.BASE)
        return SavingsForecast(
            balance = savingsBalance,
            monthsUsed = recent.size,
            actualPerMonth = actualPerMonth,
            targetPerMonth = target,
            byTargetInYear = target?.let { inYear(savingsBalance, it) },
            byActualInYear = actualPerMonth?.let { inYear(savingsBalance, it) },
        )
    }

    private fun inYear(balance: Money, perMonth: Money): Money? {
        val limit = Long.MAX_VALUE / (MONTHS_IN_YEAR * 2)
        if (perMonth.minor > limit || perMonth.minor < -limit || balance.minor > limit || balance.minor < -limit) return null
        return balance + Money(perMonth.minor * MONTHS_IN_YEAR, Currency.BASE)
    }

    private const val FORECAST_MONTHS = 3
    private const val MONTHS_IN_YEAR = 12L

    fun cumulative(points: List<MonthPoint>, selector: (MonthPoint) -> Money): List<Money> {
        var acc = Money.zero(Currency.BASE)
        return points.map { point ->
            acc += selector(point)
            acc
        }
    }
}

data class PeriodBudgetLine(val periodStart: LocalDate, val line: BudgetLine)

data class SavingsForecast(
    val balance: Money,
    val monthsUsed: Int,
    val actualPerMonth: Money?,
    val targetPerMonth: Money?,
    val byTargetInYear: Money?,
    val byActualInYear: Money?,
)
