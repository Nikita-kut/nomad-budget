package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.FundPlan
import ru.nomadbudget.domain.model.FundTarget
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.TEST_START_DAY
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FundTargetTest {

    private val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 5), TEST_START_DAY)
    private val day = LocalDate(2026, 9, 8)

    private val housing = Category("housing", "Жильё", CategoryKind.EXPENSE, 10)
    private val gifts = Category("gifts", "Подарки", CategoryKind.EXPENSE, 20)
    private val rent = Subcategory("rent", "housing", "аренда")
    private val utilities = Subcategory("utilities", "housing", "коммуналка")

    private val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.zero(Currency.RUB))
    private val reserve = Account("reserve", "Резерв", Currency.RUB, AccountKind.SAVINGS, true, Money.zero(Currency.RUB))
    private val fund = Account(
        "fund", "На квартиру", Currency.RUB, AccountKind.CASH, false, Money.zero(Currency.RUB),
        isFund = true, fundMonthly = Money.rub(5_000_000L), fundCategoryId = "housing", fundSubcategoryId = "rent",
    )
    private val plainFund = Account("plain", "На отпуск", Currency.RUB, AccountKind.CASH, false, Money.zero(Currency.RUB), isFund = true, fundMonthly = Money.rub(1_000_000L))
    private val accounts = listOf(card, reserve, fund, plainFund)

    private val lines = listOf(
        BudgetLine("housing", Money.rub(2_300_000L), "rent"),
        BudgetLine("housing", Money.rub(700_000L), "utilities"),
    )

    private fun transfer(id: String, from: String, to: String, rub: Long) =
        Transaction.Transfer(id, day, from, to, Money.rub(rub), Money.rub(rub))

    private fun expense(id: String, account: String, category: String, sub: String?, rub: Long) = Transaction.Expense(
        id = id, date = day, accountId = account, amount = Money.rub(rub), categoryId = category, subcategoryId = sub,
        amountBase = Money.rub(rub), rateSource = RateSource.API,
    )

    private fun budgets(transactions: List<Transaction>) =
        BudgetCalculator.categoryBudgets(listOf(housing, gifts), lines, transactions, period, listOf(rent, utilities), accounts, Currency.RUB)
            .associateBy { it.category.id }

    @Test
    fun transferIntoLinkedFundCountsAsSpendOfItsLine() {
        val housingBudget = budgets(listOf(transfer("t", "card", "fund", 2_235_500L))).getValue("housing")

        assertEquals(Money.rub(2_235_500L), housingBudget.fact)
        assertEquals(Money.rub(2_235_500L), housingBudget.items.single { it.subcategory?.id == "rent" }.fact)
        assertEquals(1, housingBudget.transactionCount)
    }

    @Test
    fun paymentFromLinkedFundIsNotCountedTwice() {
        val housingBudget = budgets(
            listOf(transfer("t", "card", "fund", 2_235_500L), expense("rent", "fund", "housing", "rent", 4_700_000L)),
        ).getValue("housing")

        assertEquals(Money.rub(2_235_500L), housingBudget.fact)
        assertTrue(housingBudget.fundFact.isZero)
    }

    @Test
    fun paymentFromLinkedFundForAnotherCategoryStaysOutsideEnvelopes() {
        val giftsBudget = budgets(listOf(expense("g", "fund", "gifts", null, 2_200_000L))).getValue("gifts")

        assertTrue(giftsBudget.fact.isZero)
        assertEquals(Money.rub(2_200_000L), giftsBudget.fundFact)
    }

    @Test
    fun moneyFromReserveOrAnotherFundIsNotAPlannedSpend() {
        val housingBudget = budgets(listOf(transfer("r", "reserve", "fund", 1_000_000L), transfer("p", "plain", "fund", 500_000L))).getValue("housing")

        assertTrue(housingBudget.fact.isZero)
    }

    @Test
    fun takingMoneyBackFromLinkedFundReducesTheSpend() {
        val housingBudget = budgets(listOf(transfer("t", "card", "fund", 2_000_000L), transfer("back", "fund", "card", 300_000L))).getValue("housing")

        assertEquals(Money.rub(1_700_000L), housingBudget.fact)
    }

    @Test
    fun linkedFundHasNoSeparateMonthlyContribution() {
        val transactions = listOf(transfer("t", "card", "fund", 2_235_500L), transfer("v", "card", "plain", 1_000_000L))
        val balances = accounts.associate { it.id to BalanceCalculator.balance(it, transactions) }
        val month = MonthModel.build(
            period, day, listOf(housing, gifts), listOf(rent, utilities), lines, transactions, accounts, emptyList(),
            RateTable(emptyList(), Currency.RUB), balances, null,
        )

        assertNull(month.funds.single { it.account.id == "fund" }.monthlyPlanned)
        assertEquals(Money.rub(1_000_000L), month.fundsPlanned)
        assertEquals(Money.rub(2_235_500L), month.flexibleFact)
        assertEquals(Money.rub(2_235_500L), month.funds.single { it.account.id == "fund" }.contributed)
    }

    @Test
    fun savingWithTargetDropsMonthlyContribution() {
        val plan = FundPlan(Money.rub(5_000_000L), Money.rub(0L), 23, FundTarget("housing", "rent")).normalized()

        assertNull(plan.monthly)
        assertNull(plan.goal)
        assertEquals(FundTarget("housing", "rent"), fund.withFundPlan(plan).fundTarget)
    }

    @Test
    fun monthTotalsLeaveExactlyWhatIsOnCardsAndCash() {
        val food = Category("food", "Еда", CategoryKind.EXPENSE, 30)
        val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 40)
        val opening = card.copy(openingBalance = Money.rub(5_000_000L))
        val all = listOf(opening, reserve.copy(openingBalance = Money.rub(10_000_000L)), fund, plainFund)
        val transactions = listOf(
            Transaction.Income("i", day, "card", Money.rub(10_000_000L), "salary", Money.rub(10_000_000L), RateSource.API),
            expense("f", "card", "food", null, 3_000_000L),
            transfer("s", "card", "reserve", 1_000_000L),
            transfer("back", "reserve", "card", 200_000L),
            expense("rf", "reserve", "food", null, 500_000L),
            transfer("p1", "card", "plain", 700_000L),
            transfer("p2", "reserve", "plain", 300_000L),
            transfer("l1", "card", "fund", 2_000_000L),
            transfer("l2", "reserve", "fund", 400_000L),
            expense("rent", "fund", "housing", "rent", 2_400_000L),
        )
        val balances = all.associate { it.id to BalanceCalculator.balance(it, transactions) }
        val month = MonthModel.build(
            period, day, listOf(housing, gifts, food, salary), listOf(rent, utilities), lines, transactions, all, emptyList(),
            RateTable(emptyList(), Currency.RUB), balances, Money.rub(1_000_000L),
        )

        assertEquals(Money.rub(5_000_000L), month.carryOver)
        assertEquals(Money.rub(5_000_000L), month.flexibleFact)
        assertEquals(Money.rub(100_000L), month.savedFromIncome)
        assertEquals(Money.rub(1_400_000L), month.fundsContributed)
        assertEquals(Money.rub(500_000L), month.spentFromReserves)
        assertEquals(balances.getValue("card"), month.leftFact)
        assertEquals(month.available - month.unallocated, month.outPlanned)
    }

    private fun totals(all: List<Account>, transactions: List<Transaction>, debts: List<ru.nomadbudget.domain.model.Debt> = emptyList()): MonthModel {
        val food = Category("food", "Еда", CategoryKind.EXPENSE, 30)
        val salary = Category("salary", "Зарплата", CategoryKind.INCOME, 40)
        val balances = all.associate { it.id to BalanceCalculator.balance(it, transactions) }
        return MonthModel.build(
            period, day, listOf(housing, gifts, food, salary), listOf(rent, utilities), lines, transactions, all, debts,
            RateTable(emptyList(), Currency.RUB), balances, null,
        )
    }

    private fun income(rub: Long) = Transaction.Income("i", day, "card", Money.rub(rub), "salary", Money.rub(rub), RateSource.API)

    @Test
    fun loanPaidFromFundIsNotMoneyOfTheMonth() {
        val loan = ru.nomadbudget.domain.model.Debt("loan", "Кредит", Currency.RUB, Money.rub(10_000_000L), Money.rub(1_000_000L), null, payDay = 20)
        val transactions = listOf(
            income(5_000_000L),
            transfer("p", "card", "plain", 1_500_000L),
            Transaction.Expense("d", day, "plain", Money.rub(1_000_000L), "gifts", null, Money.rub(1_000_000L), RateSource.API, debtId = "loan"),
        )
        val month = totals(accounts, transactions, listOf(loan))

        assertTrue(month.debtsPaidFromIncome.isZero)
        assertEquals(Money.rub(1_000_000L), month.reservesToDebts)
        assertTrue(month.spentFromReserves.isZero)
        assertEquals(BalanceCalculator.balance(card, transactions), month.leftFact)
    }

    @Test
    fun archivedFundStillCountsInTheMonthItGotMoney() {
        val archived = plainFund.copy(isArchived = true)
        val all = listOf(card, reserve, fund, archived)
        val transactions = listOf(income(5_000_000L), transfer("p", "card", "plain", 700_000L), transfer("x", "plain", "fund", 200_000L))
        val month = totals(all, transactions)

        assertEquals(Money.rub(700_000L), month.fundsContributed + month.fundInflowInEnvelopes)
        assertEquals(BalanceCalculator.balance(card, transactions), month.leftFact)
    }

    @Test
    fun fundLinkedToSubcategoryPayingAnotherSubcategoryStaysVisible() {
        val transactions = listOf(transfer("t", "card", "fund", 2_000_000L), expense("u", "fund", "housing", "utilities", 700_000L))
        val housingBudget = budgets(transactions).getValue("housing")

        assertEquals(Money.rub(2_000_000L), housingBudget.fact)
        assertEquals(Money.rub(700_000L), housingBudget.fundFact)
        assertEquals(Money.rub(700_000L), totals(accounts, transactions).spentFromReserves)
    }
}
