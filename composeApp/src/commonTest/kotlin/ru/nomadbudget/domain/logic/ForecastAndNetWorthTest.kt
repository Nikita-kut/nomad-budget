package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForecastAndNetWorthTest {

    @Test
    fun interestFreeDebt_closesInWholeMonths() {
        val debt = Debt("d", "Рассрочка", Currency.RUB, Money.rub(4_000_000L), Money.rub(500_000L), null, 25)
        val forecast = DebtCalculator.forecast(debt, debt.monthlyPayment)
        assertEquals(8, forecast?.months)
        assertTrue(forecast?.totalInterest?.isZero == true)
    }

    @Test
    fun extraPayment_shortensTerm_andSavesInterest() {
        val debt = Debt("d", "Кредит", Currency.RUB, Money.rub(15_000_000L), Money.rub(1_206_006L), 14.5, 15, extraPayment = Money.rub(500_000L))
        val base = DebtCalculator.forecast(debt, debt.monthlyPayment)
        val withExtra = DebtCalculator.forecast(debt, debt.plannedPayment)
        requireNotNull(base)
        requireNotNull(withExtra)
        assertTrue(withExtra.months < base.months)
        assertTrue(withExtra.totalInterest < base.totalInterest)
    }

    @Test
    fun paymentBelowInterest_neverCloses() {
        val debt = Debt("d", "Кредит", Currency.RUB, Money.rub(100_000_000L), Money.rub(100_000L), 20.0, 15)
        assertNull(DebtCalculator.forecast(debt, debt.monthlyPayment))
    }

    @Test
    fun netWorth_sumsAccountsAtPeriodEnd_withRateOnThatDay() {
        val rub = Account("rub", "Карта", Currency.RUB, AccountKind.CARD, false, Money.rub(1_000_000L))
        val usd = Account("usd", "Доллары", Currency.USD, AccountKind.CASH, false, Money(10_000L, Currency.USD))
        val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 10))
        val spent = Transaction.Expense(
            id = "e", date = LocalDate(2026, 9, 10), accountId = "rub", amount = Money.rub(200_000L), categoryId = "food",
            subcategoryId = null, amountBase = Money.rub(200_000L), rateSource = RateSource.API,
        )
        val worth = HistoryCalculator.netWorth(listOf(period), listOf(rub, usd), listOf(spent)) { _, _ -> 90.0 }
        assertEquals(listOf(Money.rub(1_700_000L)), worth)
    }

    @Test
    fun plannedExpenseByPeriod_ignoresIncomeLines() {
        val start = LocalDate(2026, 9, 5)
        val categories = listOf(Category("food", "Еда", CategoryKind.EXPENSE), Category("salary", "Зарплата", CategoryKind.INCOME))
        val lines = listOf(
            PeriodBudgetLine(start, BudgetLine("food", Money.rub(3_000_000L))),
            PeriodBudgetLine(start, BudgetLine("food", Money.rub(1_000_000L), "sub")),
            PeriodBudgetLine(start, BudgetLine("salary", Money.rub(20_000_000L))),
        )
        assertEquals(mapOf(start to Money.rub(4_000_000L)), HistoryCalculator.plannedExpenseByPeriod(lines, categories))
    }
}
