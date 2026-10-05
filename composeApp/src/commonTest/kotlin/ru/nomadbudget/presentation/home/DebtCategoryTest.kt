package ru.nomadbudget.presentation.home

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebtCategoryTest {

    private val today = LocalDate(2026, 10, 5)
    private val period = SalaryCycle.periodContaining(today)
    private val loans = Category("loans", "Долг", CategoryKind.EXPENSE, 10)
    private val food = Category("food", "Еда", CategoryKind.EXPENSE, 20)
    private val bank = Debt("d1", "Банк А", Currency.BASE, Money.rub(100_000_000L), Money.rub(3_000_000L), 12.0, 10)

    private fun state(
        debts: List<Debt> = listOf(bank),
        subcategories: List<Subcategory> = emptyList(),
        transactions: List<Transaction> = emptyList(),
    ) = HomeState(
        today = today,
        period = period,
        categories = listOf(loans, food),
        debts = debts,
        subcategories = subcategories,
        transactions = transactions,
    )

    @Test
    fun subcategoryNamedAsDebt_marksCategory() {
        val s = state(subcategories = listOf(Subcategory("s1", "loans", "банк а")))
        assertTrue(s.isDebtCategory("loans"))
        assertFalse(s.isDebtCategory("food"))
        assertEquals(bank, s.debtBySubcategoryName(" БАНК А "))
    }

    @Test
    fun pastDebtPayment_marksCategory() {
        val payment = Transaction.Expense(
            id = "e1", date = today, accountId = "card", amount = Money.rub(3_000_000L), categoryId = "loans",
            subcategoryId = null, amountBase = Money.rub(3_000_000L), rateSource = RateSource.API, debtId = "d1",
        )
        val s = state(transactions = listOf(payment))
        assertTrue(s.isDebtCategory("loans"))
        assertFalse(s.isDebtCategory("food"))
    }

    @Test
    fun noSignals_showsForAnyCategory_noDebts_hidesEverywhere() {
        assertTrue(state().isDebtCategory("food"))
        assertFalse(state(debts = emptyList()).isDebtCategory("loans"))
    }
}
