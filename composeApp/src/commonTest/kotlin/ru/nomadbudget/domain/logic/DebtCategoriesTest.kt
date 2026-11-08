package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebtCategoriesTest {

    private val bank = Debt("d1", "Ипотека", Currency.BASE, Money.rub(100_000_000L), Money.rub(3_000_000L), 12.0, 10)
    private val loans = Category("loans", "Кредиты", CategoryKind.EXPENSE, 10)
    private val payment = Transaction.Expense("p", LocalDate(2026, 10, 1), "card", Money.rub(3_000_000L), "other", null, Money.rub(3_000_000L), RateSource.API, debtId = "d1")

    @Test
    fun categoryIds_fromSubcategoryName_andFromPayments() {
        val ids = DebtCategories.categoryIds(listOf(bank), listOf(Subcategory("s", "loans", " ипотека ")), listOf(payment))
        assertEquals(setOf("loans", "other"), ids)
    }

    @Test
    fun isDebtCategory_anyCategoryWhenNothingLinkedYet() {
        assertTrue(DebtCategories.isDebtCategory("food", listOf(bank), emptySet()))
        assertFalse(DebtCategories.isDebtCategory("food", listOf(bank), setOf("loans")))
        assertFalse(DebtCategories.isDebtCategory("loans", emptyList(), setOf("loans")))
        assertFalse(DebtCategories.isDebtCategory(null, listOf(bank), emptySet()))
    }

    @Test
    fun byName_ignoresCaseAndSpaces() {
        assertEquals(bank, DebtCategories.byName(listOf(bank), "ИПОТЕКА "))
        assertNull(DebtCategories.byName(listOf(bank), "авто"))
    }

    @Test
    fun isDebtOnlyEnvelope_requiresNoPlanAndNoFact() {
        val idle = CategoryBudget(loans, Money.zero(Currency.BASE), Money.zero(Currency.BASE), 0)
        assertTrue(DebtCategories.isDebtOnlyEnvelope(idle, setOf("loans")))
        assertFalse(DebtCategories.isDebtOnlyEnvelope(idle.copy(fact = Money.rub(100L)), setOf("loans")))
        assertFalse(DebtCategories.isDebtOnlyEnvelope(idle, emptySet()))
    }
}
