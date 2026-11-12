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

data class HistoryTotals(
    val months: Int,
    val income: Money,
    val expense: Money,
    val netSaved: Money,
) {
    val averageIncome: Money get() = Money(if (months > 0) income.minor / months else 0L, income.currency)

    val averageExpense: Money get() = Money(if (months > 0) expense.minor / months else 0L, expense.currency)

    val savingsShare: Double? get() = if (income.minor > 0L) netSaved.minor.toDouble() / income.minor else null
}

object HistoryCalculator {

    fun totals(points: List<MonthPoint>, base: Currency): HistoryTotals = HistoryTotals(
        months = points.size,
        income = points.map { it.income }.sumIn(base),
        expense = points.map { it.expense }.sumIn(base),
        netSaved = points.map { it.netSaved }.sumIn(base),
    )

    fun monthly(
        transactions: List<Transaction>,
        accounts: List<Account>,
        upTo: Period,
        startDay: Int,
        base: Currency,
        systemIds: Set<String> = emptySet(),
    ): List<MonthPoint> {
        if (transactions.isEmpty()) return emptyList()
        val savingsIds = accounts.filter { it.isSavings }.map { it.id }.toSet()
        val first = SalaryCycle.periodContaining(transactions.minOf { it.date }, startDay)
        val points = mutableListOf<MonthPoint>()
        var period = first
        while (period.start <= upTo.start) {
            val inPeriod = transactions.filter { it.date in period }
            val income = inPeriod.filterIsInstance<Transaction.Income>().filter { it.categoryId !in systemIds }.map { it.amountBase }.sumIn(base)
            val expense = inPeriod.filterIsInstance<Transaction.Expense>().filter { it.categoryId !in systemIds }.map { it.amountBase }.sumIn(base)
            val saved = BudgetCalculator.cushionInflow(inPeriod, savingsIds, systemIds, base)
            val taken = BudgetCalculator.cushionOutflow(inPeriod, savingsIds, base)
            val spent = BudgetCalculator.cushionSpent(inPeriod, savingsIds, systemIds, base)
            points += MonthPoint(period, income, expense, saved - taken - spent)
            period = SalaryCycle.next(period, startDay)
        }
        return points
    }

    fun expensesByCategory(
        transactions: List<Transaction>,
        categories: List<Category>,
        from: LocalDate,
        toExclusive: LocalDate,
        base: Currency,
    ): List<CategoryTotal> {
        val byId = categories.filter { it.kind == CategoryKind.EXPENSE && !it.isSystem }.associateBy { it.id }
        val totals = transactions
            .filterIsInstance<Transaction.Expense>()
            .filter { it.date >= from && it.date < toExclusive }
            .groupBy { it.categoryId }
            .mapNotNull { (id, list) -> byId[id]?.let { it to list.map { tx -> tx.amountBase }.sumIn(base) } }
        val grand = totals.map { it.second }.sumIn(base)
        return totals
            .map { (category, total) ->
                CategoryTotal(category, total, if (grand.minor > 0L) total.minor.toDouble() / grand.minor else 0.0)
            }
            .sortedByDescending { it.total.minor }
    }

    fun trackingStart(accounts: List<Account>, transactions: List<Transaction>): LocalDate? {
        val active = accounts.filterNot { it.isArchived }.map { it.id }.toSet()
        return transactions.filter { tx -> tx.touchesAny(active) }.minOfOrNull { it.date }
    }

    fun netWorth(
        periods: List<Period>,
        accounts: List<Account>,
        transactions: List<Transaction>,
        base: Currency,
        rateOn: (Currency, LocalDate) -> Double?,
    ): List<Pair<Period, Money>> {
        val active = accounts.filterNot { it.isArchived }
        val start = trackingStart(accounts, transactions) ?: return emptyList()
        return periods.filter { it.endExclusive > start }.map { period ->
            period to active.mapNotNull { account ->
                val balance = BalanceCalculator.balanceBefore(account, transactions, period.endExclusive)
                when (account.currency) {
                    base -> balance
                    else -> rateOn(account.currency, period.lastDay)?.let { rate ->
                        Money((balance.minor.toDouble() / account.currency.minorFactor * rate * base.minorFactor).roundToLong(), base)
                    }
                }
            }.sumIn(base)
        }
    }

    fun plannedExpenseByPeriod(lines: List<PeriodBudgetLine>, categories: List<Category>, base: Currency): Map<LocalDate, Money> {
        val expenseIds = categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()
        return lines
            .filter { it.line.categoryId in expenseIds }
            .groupBy { it.periodStart }
            .mapValues { (_, list) -> list.map { it.line.planned }.sumIn(base) }
    }

    fun cumulative(points: List<MonthPoint>, base: Currency, selector: (MonthPoint) -> Money): List<Money> {
        var acc = Money.zero(base)
        return points.map { point ->
            acc += selector(point)
            acc
        }
    }
}

data class PeriodBudgetLine(val periodStart: LocalDate, val line: BudgetLine)
