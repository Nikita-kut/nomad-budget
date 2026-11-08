package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.RateTable
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.TransactionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainHelpersTest {

    private val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.zero(Currency.RUB))
    private val usd = Account("usd", "Доллары", Currency.USD, AccountKind.CASH, false, Money.zero(Currency.USD))
    private val rates = RateTable(listOf(ExchangeRate(Currency.USD, 90.0, LocalDate(2026, 10, 1), RateSource.API)))

    @Test
    fun totalInBase_convertsAndSkipsAccountsWithoutRate() {
        val jpy = Account("jpy", "Иены", Currency("JPY", 0, "¥"), AccountKind.CASH, false, Money.zero(Currency("JPY", 0, "¥")))
        val balances = mapOf("card" to Money.rub(100_000L), "usd" to Money(10_000L, Currency.USD), "jpy" to Money(5_000L, Currency("JPY", 0, "¥")))
        assertEquals(Money.rub(1_000_000L), BalanceCalculator.totalInBase(listOf(card, usd, jpy), balances, rates))
        assertEquals(Money.rub(100_000L), BalanceCalculator.totalInBase(listOf(card, Account("x", "Нет", Currency.RUB, AccountKind.CARD, false, Money.zero(Currency.RUB))), balances, rates))
    }

    @Test
    fun transactionKind_ownAmount_accounts() {
        val exchange = Transaction.Exchange("x", LocalDate(2026, 10, 1), "usd", "card", Money(10_000L, Currency.USD), Money.rub(900_000L), Money.rub(900_000L))
        assertEquals(TransactionKind.EXCHANGE, exchange.kind)
        assertEquals(Money(10_000L, Currency.USD), exchange.ownAmount)
        assertEquals(setOf("usd", "card"), exchange.accountIds)
        assertTrue(exchange.touches("card"))
        assertFalse(exchange.touches("cash"))
        assertTrue(exchange.touchesAny(setOf("cash", "usd")))
        val expense = Transaction.Expense("e", LocalDate(2026, 10, 1), "card", Money.rub(100L), "food", null, Money.rub(100L), RateSource.API)
        assertEquals("food", expense.categoryIdOrNull)
        assertEquals(null, exchange.categoryIdOrNull)
    }

    @Test
    fun differsFromUsual_byTwentyPercent() {
        val history = CategoryHistory(average = Money.rub(1_000_000L), previous = Money.rub(900_000L), months = 3)
        assertTrue(PlanCalculator.differsFromUsual(Money.rub(1_300_000L), history))
        assertFalse(PlanCalculator.differsFromUsual(Money.rub(1_100_000L), history))
        assertFalse(PlanCalculator.differsFromUsual(Money.rub(5_000_000L), history.copy(average = Money.rub(0L))))
    }

    @Test
    fun forecastIndices_coarseBeyondTwoYears() {
        assertEquals((0..12).toList(), CapitalModel.forecastIndices(12))
        assertEquals(0, CapitalModel.forecastIndices(60).first())
        assertEquals(3, CapitalModel.forecastIndices(60)[1])
        assertEquals(60, CapitalModel.forecastIndices(60).last())
    }
}
