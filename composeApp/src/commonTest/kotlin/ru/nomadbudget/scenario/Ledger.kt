package ru.nomadbudget.scenario

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.ExchangeRate
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.logic.PeriodBudgetLine
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.DebtCloseReason
import ru.nomadbudget.domain.model.EarlyPlan
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.PaymentChange
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.UserSettings
import ru.nomadbudget.presentation.home.HomeState

class Ledger(val startDay: Int = START_DAY) {

    val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.rub(0L))
    val cash = Account("cash", "Наличные", Currency.RUB, AccountKind.CASH, false, Money.rub(0L))
    val usd = Account("usd", "Карта USD", Currency.USD, AccountKind.CARD, false, Money(0L, Currency.USD))
    val cushion = Account("cushion", "Подушка", Currency.RUB, AccountKind.SAVINGS, true, Money.rub(0L))
    val cushion2 = Account("cushion2", "Вклад", Currency.RUB, AccountKind.SAVINGS, true, Money.rub(0L))
    val fund = Account(
        "fund", "На аренду", Currency.RUB, AccountKind.ACCOUNT, false, Money.rub(0L),
        isFund = true, fundMonthly = Money.rub(3_000_000L), fundGoal = Money.rub(3_000_000L), fundDay = 10,
    )

    val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 10)
    val food = Category("food", "Еда", CategoryKind.EXPENSE, 20)
    val rent = Category("rent", "Жильё", CategoryKind.EXPENSE, 30)
    val loans = Category("loans", "Кредиты", CategoryKind.EXPENSE, 40)
    val correction = Category("corr", "Корректировка", CategoryKind.EXPENSE, 900, isSystem = true)
    val correctionIn = Category("corr_in", "Корректировка", CategoryKind.INCOME, 900, isSystem = true)

    val accounts = mutableListOf(card, cash, usd, cushion, cushion2, fund)
    val categories = listOf(salary, food, rent, loans, correction, correctionIn)
    val subcategories = mutableListOf<Subcategory>()
    val transactions = mutableListOf<Transaction>()
    val debts = mutableListOf<Debt>()
    val plan = mutableMapOf<LocalDate, MutableList<BudgetLine>>()
    val savingsTargets = mutableMapOf<LocalDate, Money>()

    private var sequence = 0
    fun usdRate(date: LocalDate): Double = 90.0 + (date.toEpochDays() % 11) * 0.7

    fun ratesOn(date: LocalDate): RateTable = RateTable(listOf(ExchangeRate(Currency.USD, usdRate(date), date, RateSource.API, "test")))

    private fun baseOf(amount: Money, date: LocalDate): Money = ratesOn(date).toBase(amount)

    private fun history(until: LocalDate): List<ExchangeRate> =
        generateSequence(HISTORY_FROM) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= until }
            .map { ExchangeRate(Currency.USD, usdRate(it), it, RateSource.API, "test") }.toList()

    private fun id(prefix: String) = "$prefix${++sequence}"

    fun periodOf(date: LocalDate) = SalaryCycle.periodContaining(date, startDay)

    fun income(date: LocalDate, rub: Long, account: Account = card) {
        val amount = Money(rub, account.currency)
        transactions += Transaction.Income(id("i"), date, account.id, amount, salary.id, baseOf(amount, date), RateSource.API)
    }

    fun spend(date: LocalDate, rub: Long, account: Account = card, category: Category = food) {
        val amount = Money(rub, account.currency)
        transactions += Transaction.Expense(id("e"), date, account.id, amount, category.id, null, baseOf(amount, date), RateSource.API)
    }

    fun correct(date: LocalDate, rub: Long, account: Account = card) {
        val amount = Money.rub(rub)
        transactions += Transaction.Expense(id("c"), date, account.id, amount, correction.id, null, amount, RateSource.API)
    }

    fun move(date: LocalDate, rub: Long, from: Account, to: Account) {
        val amount = Money.rub(rub)
        transactions += Transaction.Transfer(id("t"), date, from.id, to.id, amount, amount)
    }

    fun exchange(date: LocalDate, rub: Long, usdCents: Long, from: Account = card, to: Account = usd) {
        val given = Money.rub(rub)
        transactions += Transaction.Exchange(id("x"), date, from.id, to.id, given, Money(usdCents, Currency.USD), given)
    }

    fun openDebt(id: String, opened: LocalDate, principal: Long, payment: Long, payDay: Int, rate: Double? = 12.0, currency: Currency = Currency.RUB): Debt {
        val debt = Debt(
            id, "Кредит $id", currency, Money(principal, currency), Money(payment, currency), rate, payDay,
            openedOn = opened, paymentChanges = listOf(PaymentChange(opened, Money(payment, currency))),
        )
        debts += debt
        subcategories += Subcategory("sub_$id", loans.id, debt.name)
        return debt
    }

    fun pay(debtId: String, date: LocalDate, rub: Long, from: Account = card, early: Boolean = false) {
        val index = debts.indexOfFirst { it.id == debtId }
        val debt = debts[index]
        val amount = Money(rub, from.currency)
        val amountBase = baseOf(amount, date)
        val inDebt = if (amount.currency == debt.currency) amount else ratesOn(date).fromBase(amountBase, debt.currency)
        val principal = DebtCalculator.principalFor(debt, inDebt, early)?.let { if (it > debt.principalRemaining) debt.principalRemaining else it }
        transactions += Transaction.Expense(
            id("p"), date, from.id, amount, loans.id, "sub_$debtId", amountBase, RateSource.API,
            debtId = debtId, debtPrincipal = principal, debtEarly = early,
        )
        if (principal != null) debts[index] = debt.copy(principalRemaining = debt.principalRemaining - principal)
    }

    fun close(debtId: String, on: LocalDate, reason: DebtCloseReason = DebtCloseReason.EARLY) {
        val index = debts.indexOfFirst { it.id == debtId }
        val debt = debts[index]
        debts[index] = debt.copy(isClosed = true, closedOn = on, closeReason = reason, writtenOff = debt.principalRemaining, principalRemaining = Money.zero(debt.currency))
    }

    fun changePayment(debtId: String, from: LocalDate, rub: Long) {
        val index = debts.indexOfFirst { it.id == debtId }
        val debt = debts[index]
        debts[index] = debt.copy(monthlyPayment = Money(rub, debt.currency), paymentChanges = debt.paymentChanges + PaymentChange(from, Money(rub, debt.currency)))
    }

    fun planEarly(debtId: String, periodStart: LocalDate, rub: Long, fromCushion: Boolean) {
        val index = debts.indexOfFirst { it.id == debtId }
        val debt = debts[index]
        debts[index] = debt.copy(earlyPlans = debt.earlyPlans + EarlyPlan(periodStart, Money(rub, debt.currency), fromCushion))
    }

    fun archive(account: Account, on: LocalDate) {
        val index = accounts.indexOfFirst { it.id == account.id }
        accounts[index] = accounts[index].copy(isArchived = true, archivedOn = on)
    }

    fun planMonth(periodStart: LocalDate, income: Long, food: Long, savings: Long? = null) {
        plan[periodStart] = mutableListOf(BudgetLine(salary.id, Money.rub(income)), BudgetLine(this.food.id, Money.rub(food)))
        if (savings != null) savingsTargets[periodStart] = Money.rub(savings)
    }

    fun copy(): Ledger = Ledger(startDay).also { other ->
        other.accounts.clear()
        other.accounts += accounts
        other.subcategories += subcategories
        other.transactions += transactions
        other.debts += debts
        plan.forEach { (k, v) -> other.plan[k] = v.toMutableList() }
        other.savingsTargets += savingsTargets
        other.sequence = sequence
    }

    fun state(today: LocalDate): HomeState {
        val period = periodOf(today)
        return HomeState(
            today = today,
            period = period,
            periodId = period.start.toString(),
            loading = false,
            currencies = Currency.builtIn,
            accounts = accounts.toList(),
            categories = categories,
            subcategories = subcategories.toList(),
            rawTransactions = transactions.filter { it.date <= today },
            budgetLines = plan[period.start].orEmpty().toList(),
            savingsTarget = savingsTargets[period.start],
            allPlanLines = plan.flatMap { (start, lines) -> lines.map { PeriodBudgetLine(start, it) } },
            rates = ratesOn(today),
            rateHistory = history(today),
            debts = debts.toList(),
            settings = UserSettings(periodStartDay = startDay, onboardedAt = kotlin.time.Instant.fromEpochMilliseconds(0L)),
        )
    }

    companion object {
        const val START_DAY = 5
        val HISTORY_FROM = LocalDate(2026, 6, 1)
    }
}
