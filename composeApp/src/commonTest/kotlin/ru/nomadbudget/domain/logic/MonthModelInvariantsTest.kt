package ru.nomadbudget.domain.logic

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DefaultRates
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.nomadbudget.domain.model.TEST_START_DAY

class MonthModelInvariantsTest {

    private val period = SalaryCycle.periodContaining(LocalDate(2026, 11, 10), TEST_START_DAY)
    private val rates = DefaultRates.table()

    @Test
    fun invariantsHoldOnRandomScenarios() {
        repeat(SCENARIOS) { seed -> check(seed) }
    }

    private fun check(seed: Int) {
        val random = Random(seed)
        val accounts = listOf(
            Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.rub(random.nextLong(0L, 50_000_000L))),
            Account("cash", "Наличные", Currency.RUB, AccountKind.CASH, false, Money.rub(0L)),
            Account("cushion", "Подушка", Currency.RUB, AccountKind.SAVINGS, true, Money.rub(random.nextLong(0L, 300_000_000L))),
            Account("usd", "Доллары", Currency.USD, AccountKind.SAVINGS, true, Money.zero(Currency.USD)),
            Account(
                "fund", "Копилка", Currency.RUB, AccountKind.ACCOUNT, false, Money.rub(random.nextLong(0L, 5_000_000L)),
                isFund = true, fundMonthly = Money.rub(random.nextLong(0L, 5_000_000L)).takeIf { random.nextBoolean() },
                fundGoal = Money.rub(random.nextLong(0L, 8_000_000L)).takeIf { random.nextBoolean() }, fundDay = random.nextInt(1, 32).takeIf { random.nextBoolean() },
            ),
        )
        val categories = listOf(
            Category("salary", "Зарплата", CategoryKind.INCOME, 10),
            Category("side", "Подработка", CategoryKind.INCOME, 20),
            Category("food", "Еда", CategoryKind.EXPENSE, 30),
            Category("fun", "Развлечения", CategoryKind.EXPENSE, 40),
            Category("home", "Жильё", CategoryKind.EXPENSE, 50),
            Category("loans", "Долг", CategoryKind.EXPENSE, 60),
            Category("old", "Старое", CategoryKind.EXPENSE, 70, isArchived = true),
            Category("corr", "Корректировка", CategoryKind.EXPENSE, 900, isSystem = true),
            Category("corr_in", "Корректировка", CategoryKind.INCOME, 900, isSystem = true),
        )
        val subcategories = listOf(Subcategory("cafe", "food", "кафе"), Subcategory("shop", "food", "магазин"), Subcategory("rent", "home", "аренда"))
        val debts = (0 until random.nextInt(0, 4)).map { i ->
            Debt("d$i", "Кредит $i", Currency.RUB, Money.rub(random.nextLong(1_000_000L, 900_000_000L)), Money.rub(random.nextLong(100_000L, 5_000_000L)), if (random.nextBoolean()) random.nextDouble(0.0, 30.0) else null, random.nextInt(1, 32).takeIf { random.nextInt(5) > 0 })
        }
        val lines = buildList {
            if (random.nextBoolean()) add(BudgetLine("salary", Money.rub(random.nextLong(0L, 60_000_000L))))
            listOf("food", "fun", "home", "loans", "old").forEach { cat ->
                if (random.nextBoolean()) add(BudgetLine(cat, Money(random.nextLong(0L, 3_000_000_00L), Currency.RUB)))
            }
            if (random.nextBoolean()) add(BudgetLine("food", Money.rub(random.nextLong(1L, 2_000_000L)), "cafe"))
            if (random.nextBoolean()) add(BudgetLine("home", Money.rub(random.nextLong(1L, 6_000_000L)), "rent"))
        }
        val days = period.lengthDays
        fun day() = period.start.plus(random.nextInt(-3, days + 3), DateTimeUnit.DAY)
        val ids = accounts.map { it.id }
        val tx = (0 until random.nextInt(0, 60)).map { i ->
            val amount = random.nextLong(1L, if (random.nextInt(10) == 0) 10_000_000_000L else 2_000_000L)
            when (random.nextInt(6)) {
                0 -> Transaction.Income("t$i", day(), ids.random(random).takeIf { it != "usd" } ?: "card", Money.rub(amount), listOf("salary", "side", "corr_in").random(random), Money.rub(amount), RateSource.API)
                1 -> {
                    val from = listOf("card", "cash", "cushion", "fund").random(random)
                    val to = listOf("card", "cash", "cushion", "fund").filter { it != from }.random(random)
                    Transaction.Transfer("t$i", day(), from, to, Money.rub(amount), Money.rub(amount))
                }
                2 -> Transaction.Exchange("t$i", day(), "card", "usd", Money.rub(amount), Money(amount / 90 + 1, Currency.USD), Money.rub(amount))
                else -> {
                    val debt = debts.randomOrNull(random)?.takeIf { random.nextInt(4) == 0 }
                    val category = if (debt != null) "loans" else listOf("food", "fun", "home", "loans", "old", "corr").random(random)
                    val sub = when (category) {
                        "food" -> listOf("cafe", "shop", null).random(random)
                        "home" -> listOf("rent", null).random(random)
                        else -> null
                    }
                    Transaction.Expense("t$i", day(), listOf("card", "cash", "cushion", "fund").random(random), Money.rub(amount), category, sub, Money.rub(amount), RateSource.API, debtId = debt?.id)
                }
            }
        }
        val today = period.start.plus(random.nextInt(-2, days + 4), DateTimeUnit.DAY)
        val target = Money.rub(random.nextLong(0L, 20_000_000L)).takeIf { random.nextBoolean() }
        val balances = accounts.associate { it.id to BalanceCalculator.balance(it, tx) }
        val m = MonthModel.build(period, today, categories, subcategories, lines, tx, accounts, debts, rates, balances, target)
        val ctx = "seed=$seed"

        assertEquals(m.available, m.obligationsPlanned + m.flexiblePlanned + (m.savingsTarget ?: Money.zero(Currency.BASE)) + m.unallocated, ctx)
        m.allocatedShare?.let { share -> if (!m.unallocated.isNegative) assertTrue(share <= 1.0 + 1e-9, "$ctx share=$share") }
        assertEquals(m.flexiblePlannedWhole - m.flexibleFactWhole, m.canSpend, ctx)
        m.perDay?.let { assertTrue(it.minor >= 0L && it.minor * m.daysLeft <= m.canSpend.minor, "$ctx perDay") }
        assertTrue(m.envelopes.none { it.category.isSystem } && m.incomeBudgets.none { it.category.isSystem }, ctx)

        val accountsById = accounts.associateBy { it.id }
        val inPeriod = tx.filter { it.date in period }
        val expectedFlexible = inPeriod.filterIsInstance<Transaction.Expense>()
            .filter { it.categoryId != "corr" && it.debtId == null && accountsById.getValue(it.accountId).role == AccountRole.LIFE }
            .map { it.amountBase }.sumIn(Currency.BASE)
        assertEquals(expectedFlexible, m.flexibleFact, ctx)

        m.envelopes.forEach { b ->
            assertEquals(b.freePlanned + b.itemsPlanned, b.planned, ctx)
            assertEquals(b.fact, b.items.map { it.fact }.sumIn(Currency.BASE).takeIf { b.items.isNotEmpty() } ?: b.fact, ctx)
            val over = b.fact.wholeUnits > b.planned.wholeUnits
            assertEquals(over, b.status == BudgetStatus.OVER || b.status == BudgetStatus.OVER_SLIGHTLY, "$ctx ${b.category.id}")
            if (b.category.isArchived) assertTrue(b.planned.isZero, ctx)
        }

        assertEquals(m.savedIn - m.savedOut - m.spentFromCushion, m.saved, ctx)
        assertTrue(!m.savedIn.isNegative && !m.savedOut.isNegative && !m.spentFromCushion.isNegative, ctx)
        if (m.savingsProgress == SavingsProgress.DONE) assertTrue(m.savingsLeft.isZero, ctx)

        m.debts.forEach { d ->
            d.due?.let { assertTrue(it in period, "$ctx due") }
            assertTrue(!d.left.isNegative, ctx)
            if (d.status == ObligationStatus.PAID) assertTrue(d.left.isZero || d.planned.isZero, ctx)
        }
        assertTrue(m.obligationsPaid <= m.obligationsCount, ctx)
        m.funds.forEach { assertTrue(!it.contributionLeft.isNegative, ctx) }

        if (!m.flexibleOk && m.level != null) assertEquals(MonthLevel.BAD, m.level, ctx)
    }

    private companion object {
        const val SCENARIOS = 120
    }
}
