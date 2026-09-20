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

class BalanceCalculatorTest {

    private val date = LocalDate(2026, 9, 7)
    private val usdCard = Account("usd_card", "USD карта", Currency.USD, AccountKind.CARD, false, Money(180_000L, Currency.USD))
    private val vndCash = Account("vnd_cash", "VND наличка", Currency.VND, AccountKind.CASH, false, Money.zero(Currency.VND))
    private val ruCard = Account("ru_card", "RU карта", Currency.RUB, AccountKind.CARD, false, Money.zero(Currency.RUB))
    private val invest = Account("ru_invest", "Инвесткопилка", Currency.RUB, AccountKind.INVESTMENT, true, Money(29_016_900L, Currency.RUB))

    private val exchange = Transaction.Exchange(
        id = "x1", date = date, fromAccountId = "usd_card", toAccountId = "vnd_cash",
        given = Money(20_000L, Currency.USD), received = Money(5_180_000L, Currency.VND),
        amountBase = Money(1_683_950L, Currency.RUB),
    )
    private val expense = Transaction.Expense(
        id = "e1", date = date, accountId = "vnd_cash", amount = Money(385_000L, Currency.VND),
        categoryId = "food", subcategoryId = null, amountBase = Money(126_442L, Currency.RUB), rateSource = RateSource.API,
    )
    private val salary = Transaction.Income(
        id = "i1", date = date, accountId = "ru_card", amount = Money(26_348_000L, Currency.RUB),
        categoryId = "salary", amountBase = Money(26_348_000L, Currency.RUB), rateSource = RateSource.API,
    )
    private val payYourself = Transaction.Transfer(
        id = "t1", date = date, fromAccountId = "ru_card", toAccountId = "ru_invest",
        amount = Money(12_000_000L, Currency.RUB), amountBase = Money(12_000_000L, Currency.RUB),
    )
    private val all = listOf(exchange, expense, salary, payYourself)

    @Test
    fun exchange_debitsGivenAndCreditsReceived() {
        assertEquals(Money(160_000L, Currency.USD), BalanceCalculator.balance(usdCard, all))
        assertEquals(Money(4_795_000L, Currency.VND), BalanceCalculator.balance(vndCash, all))
    }

    @Test
    fun income_and_transfer_onRubCard() {
        assertEquals(Money(14_348_000L, Currency.RUB), BalanceCalculator.balance(ruCard, all))
    }

    @Test
    fun transfer_creditsSavings() {
        assertEquals(Money(41_016_900L, Currency.RUB), BalanceCalculator.balance(invest, all))
    }

    @Test
    fun noTransactions_returnsOpeningBalance() {
        assertEquals(usdCard.openingBalance, BalanceCalculator.balance(usdCard, emptyList()))
    }
}
