package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceBeforeTest {

    private val card = Account("card", "RU карта", Currency.RUB, AccountKind.CARD, false, Money.rub(1_000_000L))

    private fun expense(id: String, date: LocalDate, rub: Long) = Transaction.Expense(
        id = id, date = date, accountId = "card", amount = Money.rub(rub), categoryId = "misc", subcategoryId = null,
        amountBase = Money.rub(rub), rateSource = RateSource.API,
    )

    private val transactions = listOf(
        expense("a", LocalDate(2026, 9, 3), 100_000L),
        expense("b", LocalDate(2026, 9, 4), 200_000L),
        expense("c", LocalDate(2026, 9, 5), 300_000L),
        expense("d", LocalDate(2026, 9, 20), 50_000L),
    )

    @Test
    fun balanceBefore_periodStart_excludesStartDayAndLater() {
        val atStart = BalanceCalculator.balanceBefore(card, transactions, LocalDate(2026, 9, 5))
        assertEquals(Money.rub(700_000L), atStart)
    }

    @Test
    fun balanceBefore_matchesFullBalance_whenDateIsAfterAll() {
        val full = BalanceCalculator.balance(card, transactions)
        assertEquals(full, BalanceCalculator.balanceBefore(card, transactions, LocalDate(2026, 12, 1)))
        assertEquals(Money.rub(350_000L), full)
    }
}
