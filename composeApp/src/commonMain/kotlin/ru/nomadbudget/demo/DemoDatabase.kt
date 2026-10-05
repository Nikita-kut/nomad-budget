package ru.nomadbudget.demo

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.core.SystemToday
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import kotlin.math.sin
import kotlin.random.Random

object DemoMode {
    var enabled: Boolean = false
}

class DemoDatabase(val today: LocalDate) {

    val currencies: List<Currency> = listOf(Currency.RUB, Currency.USD, TEST_CURRENCY)
    val accounts = mutableListOf<Account>()
    val categories = mutableListOf<Category>()
    val subcategories = mutableListOf<Subcategory>()
    val transactions = mutableListOf<Transaction>()
    val budgetLines = mutableMapOf<String, MutableList<BudgetLine>>()
    val savingsTargets = mutableMapOf<String, Money>()
    val debts = mutableListOf<Debt>()
    val balanceChecks = mutableListOf<BalanceCheck>()
    val rateHistory = mutableListOf<ExchangeRate>()

    private var sequence = 0

    init {
        DemoSeed(this).fill()
    }

    fun newId(prefix: String): String = "$prefix-${++sequence}"

    fun periodId(period: Period): String = "demo:${period.start}"

    fun ratesOn(date: LocalDate): RateTable =
        RateTable(rateHistory.filter { it.date <= date }.groupBy { it.quote }.values.map { list -> list.maxBy { it.date } })

    fun applyDebtPrincipal(transaction: Transaction, sign: Int) {
        val expense = transaction as? Transaction.Expense ?: return
        val debtId = expense.debtId ?: return
        val principal = expense.debtPrincipal ?: return
        val index = debts.indexOfFirst { it.id == debtId }
        if (index < 0) return
        val debt = debts[index]
        debts[index] = debt.copy(principalRemaining = Money(debt.principalRemaining.minor - sign * principal.minor, debt.currency))
    }

    companion object {
        val TEST_CURRENCY: Currency = Currency("XTS", 0, "¤")
        private const val DEMO_DAY_IN_PERIOD = 17

        fun anchorToday(): LocalDate = SalaryCycle.periodContaining(SystemToday.today()).start.plus(DEMO_DAY_IN_PERIOD, DateTimeUnit.DAY)
    }
}

private class DemoSeed(private val db: DemoDatabase) {

    private val random = Random(SEED)
    private val usd = Currency.USD
    private val local = DemoDatabase.TEST_CURRENCY

    private val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.rub(4_000_000L), 10)
    private val rubAccount = Account("rub_account", "Счёт", Currency.RUB, AccountKind.ACCOUNT, false, Money.rub(0L), 20)
    private val rubCash = Account("rub_cash", "Наличные ₽", Currency.RUB, AccountKind.CASH, false, Money.rub(300_000L), 30)
    private val usdCard = Account("usd_card", "Карта USD", usd, AccountKind.CARD, false, Money(50_000L, usd), 40)
    private val usdCash = Account("usd_cash", "Наличные $", usd, AccountKind.CASH, false, Money(20_000L, usd), 50)
    private val localCash = Account("local_cash", "Наличные ¤", local, AccountKind.CASH, false, Money(30_000L, local), 60)
    private val cushion = Account("cushion", "Подушка", Currency.RUB, AccountKind.SAVINGS, true, Money.rub(30_000_000L), 10)
    private val invest = Account("invest", "Инвесткопилка", Currency.RUB, AccountKind.INVESTMENT, true, Money.rub(15_000_000L), 20)
    private val oldCard = Account("old_card", "Старая карта", Currency.RUB, AccountKind.CARD, false, Money.rub(0L), 70, isArchived = true)

    private val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 10)
    private val side = Category("side", "Подработка", CategoryKind.INCOME, 20)
    private val food = Category("food", "Еда", CategoryKind.EXPENSE, 110)
    private val transport = Category("transport", "Транспорт", CategoryKind.EXPENSE, 120)
    private val housing = Category("housing", "Жильё", CategoryKind.EXPENSE, 130)
    private val comm = Category("comm", "Связь", CategoryKind.EXPENSE, 140)
    private val health = Category("health", "Здоровье", CategoryKind.EXPENSE, 150)
    private val travel = Category("travel", "Путешествия", CategoryKind.EXPENSE, 160)
    private val gifts = Category("gifts", "Подарки", CategoryKind.EXPENSE, 170)
    private val docs = Category("docs", "Документы", CategoryKind.EXPENSE, 180)
    private val fees = Category("fees", "Комиссии", CategoryKind.EXPENSE, 190)
    private val loans = Category("loans", "Долг", CategoryKind.EXPENSE, 200)

    private val grocery = Subcategory("sub_grocery", food.id, "супермаркет")
    private val cafe = Subcategory("sub_cafe", food.id, "кафе")
    private val delivery = Subcategory("sub_delivery", food.id, "доставка")
    private val taxi = Subcategory("sub_taxi", transport.id, "такси")
    private val metro = Subcategory("sub_metro", transport.id, "метро")
    private val rent = Subcategory("sub_rent", housing.id, "аренда")
    private val utilities = Subcategory("sub_utilities", housing.id, "коммуналка")
    private val internet = Subcategory("sub_internet", housing.id, "интернет")
    private val mobile = Subcategory("sub_mobile", comm.id, "мобильная связь")
    private val pharmacy = Subcategory("sub_pharmacy", health.id, "аптека")
    private val loanSubA = Subcategory("sub_loan_a", loans.id, "Кредит А")
    private val loanSubB = Subcategory("sub_loan_b", loans.id, "Рассрочка Б")

    private var loanA = Debt(
        id = "debt_a", name = "Кредит А", currency = Currency.RUB,
        principalRemaining = Money.rub(15_000_000L), monthlyPayment = Money.rub(1_206_006L), ratePercent = 14.5, payDay = 15,
    )
    private var loanB = Debt(
        id = "debt_b", name = "Рассрочка Б", currency = Currency.RUB,
        principalRemaining = Money.rub(4_000_000L), monthlyPayment = Money.rub(500_000L), ratePercent = null, payDay = 25,
    )

    fun fill() {
        val current = SalaryCycle.periodContaining(db.today)
        val periods = generateSequence(current) { SalaryCycle.previous(it) }.take(PERIODS).toList().reversed()
        db.accounts += listOf(card, rubAccount, rubCash, usdCard, usdCash, localCash, cushion, invest, oldCard)
        db.categories += listOf(salary, side, food, transport, housing, comm, health, travel, gifts, docs, fees, loans)
        db.subcategories += listOf(grocery, cafe, delivery, taxi, metro, rent, utilities, internet, mobile, pharmacy, loanSubA, loanSubB)
        seedRates(periods.first().start)
        periods.forEachIndexed { index, period -> seedPeriod(period, index, isCurrent = period == current) }
        db.debts += listOf(loanA, loanB)
        seedBalanceCheck(periods[periods.lastIndex - 1])
    }

    private fun seedRates(from: LocalDate) {
        var date = from
        var day = 0
        while (date <= db.today) {
            db.rateHistory += ExchangeRate(usd, 92.4 + 1.6 * sin(day / 9.0), date, RateSource.API, "demo")
            db.rateHistory += ExchangeRate(local, 0.61 + 0.02 * sin(day / 13.0), date, RateSource.API, "demo")
            date = date.plus(1, DateTimeUnit.DAY)
            day++
        }
    }

    private fun seedPeriod(period: Period, index: Int, isCurrent: Boolean) {
        income(period, 0, card, Money.rub(23_000_000L), salary)
        income(period, 12, usdCard, Money(25_000L, usd), side, "перевод от заказчика")
        transfer(period, 0, card, cushion, Money.rub(3_000_000L), "себе")
        transfer(period, 1, card, invest, Money.rub(1_000_000L))
        transfer(period, 3, card, rubCash, Money.rub(500_000L))
        exchange(period, 2, card, usdCard, Money.rub(3_712_000L), Money(40_000L, usd))
        exchange(period, 4, usdCard, localCash, Money(15_000L, usd), Money(22_350L, local))

        expense(period, 0, card, Money.rub(4_500_000L), housing, rent)
        expense(period, 7, card, Money.rub(550_000L + random.nextLong(0L, 80_000L)), housing, utilities)
        expense(period, 7, card, Money.rub(90_000L), housing, internet)
        expense(period, 9, card, Money.rub(65_000L), comm, mobile)
        loanPayment(period, 10, Money.rub(1_206_006L), isA = true, early = false)
        loanPayment(period, 20, Money.rub(500_000L), isA = false, early = false)
        if (index == 3) loanPayment(period, 14, Money.rub(2_000_000L), isA = true, early = true)

        var day = 1
        while (day < 31) {
            if (random.nextInt(3) == 0) {
                expense(period, day, localCash, Money(random.nextLong(1_200L, 4_800L), local), food, grocery)
            } else {
                expense(period, day, card, Money.rub(random.nextLong(150_000L, 450_000L)), food, grocery, if (day % 7 == 1) "продукты на неделю" else "")
            }
            day += 2 + random.nextInt(2)
        }
        repeat(if (isCurrent) 7 else 5) { expense(period, random.nextInt(1, 29), usdCard, Money(random.nextLong(800L, 2_500L), usd), food, cafe) }
        repeat(3) { expense(period, random.nextInt(1, 29), card, Money.rub(random.nextLong(80_000L, 160_000L)), food, delivery) }
        repeat(if (isCurrent) 10 else 7) {
            if (random.nextBoolean()) {
                expense(period, random.nextInt(1, 29), card, Money.rub(random.nextLong(30_000L, 90_000L)), transport, taxi)
            } else {
                expense(period, random.nextInt(1, 29), localCash, Money(random.nextLong(600L, 1_800L), local), transport, taxi)
            }
        }
        repeat(4) { expense(period, random.nextInt(1, 29), rubCash, Money.rub(random.nextLong(6_000L, 12_000L)), transport, metro) }
        repeat(2) { expense(period, random.nextInt(1, 29), card, Money.rub(random.nextLong(40_000L, 180_000L)), health, pharmacy) }
        expense(period, random.nextInt(1, 29), card, Money.rub(random.nextLong(300_000L, 800_000L)), gifts, null, "день рождения")
        expense(period, random.nextInt(1, 29), usdCard, Money(300L, usd), fees, null, "обслуживание карты")
        if (index == 2) expense(period, 16, usdCard, Money(45_000L, usd), travel, null, "билеты")
        if (index == 1) expense(period, 11, card, Money.rub(250_000L), docs, null)

        seedPlan(period, index, isCurrent)
    }

    private fun seedPlan(period: Period, index: Int, isCurrent: Boolean) {
        val lines = mutableListOf(
            BudgetLine(salary.id, Money.rub(23_000_000L)),
            BudgetLine(side.id, Money.rub(2_000_000L)),
            BudgetLine(food.id, Money.rub(1_500_000L)),
            BudgetLine(food.id, Money.rub(2_000_000L), grocery.id),
            BudgetLine(food.id, Money.rub(800_000L), cafe.id),
            BudgetLine(transport.id, Money.rub(500_000L), taxi.id),
            BudgetLine(transport.id, Money.rub(100_000L), metro.id),
            BudgetLine(housing.id, Money.rub(4_500_000L), rent.id),
            BudgetLine(housing.id, Money.rub(600_000L), utilities.id),
            BudgetLine(housing.id, Money.rub(90_000L), internet.id),
            BudgetLine(comm.id, Money.rub(65_000L), mobile.id),
            BudgetLine(health.id, Money.rub(300_000L)),
            BudgetLine(gifts.id, Money.rub(500_000L)),
            BudgetLine(fees.id, Money.rub(50_000L)),
            BudgetLine(loans.id, Money.rub(1_206_000L), loanSubA.id),
            BudgetLine(loans.id, Money.rub(500_000L), loanSubB.id),
        )
        if (index == 2) lines += BudgetLine(travel.id, Money.rub(4_500_000L))
        if (index == 3) lines.replaceEach { if (it.subcategoryId == loanSubA.id) it.copy(planned = Money.rub(3_206_000L)) else it }
        if (!isCurrent && index == 1) lines += BudgetLine(docs.id, Money.rub(300_000L))
        db.budgetLines[db.periodId(period)] = lines
        db.savingsTargets[db.periodId(period)] = Money.rub(4_000_000L)
    }

    private fun seedBalanceCheck(period: Period) {
        val computed = Money.rub(5_412_350L)
        db.balanceChecks += BalanceCheck(db.newId("check"), card.id, period.start.plus(20, DateTimeUnit.DAY), computed - Money.rub(12_000L), computed, "после поездки")
    }

    private fun dateIn(period: Period, offset: Int): LocalDate? =
        period.start.plus(offset, DateTimeUnit.DAY).takeIf { it in period && it <= db.today }

    private fun base(money: Money, date: LocalDate): Money = db.ratesOn(date).toBase(money)

    private fun income(period: Period, offset: Int, account: Account, amount: Money, category: Category, note: String = "") {
        val date = dateIn(period, offset) ?: return
        db.transactions += Transaction.Income(db.newId("tx"), date, account.id, amount, category.id, base(amount, date), RateSource.API, note)
    }

    private fun expense(period: Period, offset: Int, account: Account, amount: Money, category: Category, sub: Subcategory?, note: String = "") {
        val date = dateIn(period, offset) ?: return
        db.transactions += Transaction.Expense(db.newId("tx"), date, account.id, amount, category.id, sub?.id, base(amount, date), RateSource.API, note)
    }

    private fun transfer(period: Period, offset: Int, from: Account, to: Account, amount: Money, note: String = "") {
        val date = dateIn(period, offset) ?: return
        db.transactions += Transaction.Transfer(db.newId("tx"), date, from.id, to.id, amount, base(amount, date), note)
    }

    private fun exchange(period: Period, offset: Int, from: Account, to: Account, given: Money, received: Money) {
        val date = dateIn(period, offset) ?: return
        db.transactions += Transaction.Exchange(db.newId("tx"), date, from.id, to.id, given, received, base(given, date))
    }

    private fun loanPayment(period: Period, offset: Int, amount: Money, isA: Boolean, early: Boolean) {
        val date = dateIn(period, offset) ?: return
        val debt = if (isA) loanA else loanB
        val principal = DebtCalculator.principalFor(debt, amount, early) ?: return
        val updated = debt.copy(principalRemaining = debt.principalRemaining - principal)
        if (isA) loanA = updated else loanB = updated
        db.transactions += Transaction.Expense(
            id = db.newId("tx"), date = date, accountId = card.id, amount = amount, categoryId = loans.id,
            subcategoryId = if (isA) loanSubA.id else loanSubB.id, amountBase = amount, rateSource = RateSource.API,
            note = if (early) "досрочно" else "", debtId = debt.id, debtPrincipal = principal, debtEarly = early,
        )
    }

    private companion object {
        const val SEED = 20_261_005
        const val PERIODS = 6
    }
}

internal fun <T> MutableList<T>.replaceEach(transform: (T) -> T) {
    for (index in indices) this[index] = transform(this[index])
}
