package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
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

    fun monthly(transactions: List<Transaction>, accounts: List<Account>, upTo: Period): List<MonthPoint> {
        if (transactions.isEmpty()) return emptyList()
        val savingsIds = accounts.filter { it.isSavings }.map { it.id }.toSet()
        val first = SalaryCycle.periodContaining(transactions.minOf { it.date })
        val points = mutableListOf<MonthPoint>()
        var period = first
        while (period.start <= upTo.start) {
            val inPeriod = transactions.filter { it.date in period }
            val income = inPeriod.filterIsInstance<Transaction.Income>().map { it.amountBase }.sumIn(Currency.BASE)
            val expense = inPeriod.filterIsInstance<Transaction.Expense>().map { it.amountBase }.sumIn(Currency.BASE)
            val transfers = inPeriod.filterIsInstance<Transaction.Transfer>()
            val saved = transfers.filter { it.toAccountId in savingsIds && it.fromAccountId !in savingsIds }.map { it.amountBase }.sumIn(Currency.BASE)
            val taken = transfers.filter { it.fromAccountId in savingsIds && it.toAccountId !in savingsIds }.map { it.amountBase }.sumIn(Currency.BASE)
            points += MonthPoint(period, income, expense, saved - taken)
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
        val byId = categories.filter { it.kind == CategoryKind.EXPENSE }.associateBy { it.id }
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

    fun netWorth(
        periods: List<Period>,
        accounts: List<Account>,
        transactions: List<Transaction>,
        rateOn: (Currency, LocalDate) -> Double?,
    ): List<Money> = periods.map { period ->
        accounts.mapNotNull { account ->
            val balance = BalanceCalculator.balanceBefore(account, transactions, period.endExclusive)
            when (account.currency) {
                Currency.BASE -> balance
                else -> rateOn(account.currency, period.lastDay)?.let { rate ->
                    Money((balance.minor.toDouble() / account.currency.minorFactor * rate * Currency.BASE.minorFactor).toLong(), Currency.BASE)
                }
            }
        }.sumIn(Currency.BASE)
    }

    fun plannedExpenseByPeriod(lines: List<PeriodBudgetLine>, categories: List<Category>): Map<LocalDate, Money> {
        val expenseIds = categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()
        return lines
            .filter { it.line.categoryId in expenseIds }
            .groupBy { it.periodStart }
            .mapValues { (_, list) -> list.map { it.line.planned }.sumIn(Currency.BASE) }
    }

    fun cumulative(points: List<MonthPoint>, selector: (MonthPoint) -> Money): List<Money> {
        var acc = Money.zero(Currency.BASE)
        return points.map { point ->
            acc += selector(point)
            acc
        }
    }
}

data class PeriodBudgetLine(val periodStart: LocalDate, val line: BudgetLine)
