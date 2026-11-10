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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CostBasisCalculatorTest {

    private val xts = Currency("XTS", 0, "¤")
    private val d1 = LocalDate(2026, 10, 1)
    private val d2 = LocalDate(2026, 10, 5)
    private val d3 = LocalDate(2026, 10, 9)
    private val d4 = LocalDate(2026, 10, 12)

    private val card = account("card", Currency.RUB)
    private val cash = account("cash", Currency.USD)
    private val reserve = account("reserve", Currency.USD)
    private val wallet = account("wallet", xts)

    private val history = listOf(rate(Currency.USD, 90.0, d1), rate(Currency.USD, 95.0, d3), rate(xts, 0.5, d1))
    private val rates = RateTable(listOf(rate(Currency.USD, 100.0, d4), rate(xts, 0.5, d4)))

    private fun account(id: String, currency: Currency, opening: Long = 0L) =
        Account(id, id, currency, AccountKind.CASH, false, Money(opening, currency))

    private fun rate(currency: Currency, value: Double, date: LocalDate) = ExchangeRate(currency, value, date, RateSource.API)

    private fun usd(minor: Long) = Money(minor, Currency.USD)

    private fun buy(id: String, date: LocalDate, rub: Long, usdMinor: Long, to: String = "cash") =
        Transaction.Exchange(id, date, "card", to, Money.rub(rub), usd(usdMinor), Money.rub(rub))

    private fun spend(id: String, date: LocalDate, amount: Money, from: String = "cash", stored: Long = 1L) =
        Transaction.Expense(id, date, from, amount, "food", null, Money.rub(stored), RateSource.API)

    private fun earn(id: String, date: LocalDate, amount: Money, to: String = "cash", stored: Long = 1L) =
        Transaction.Income(id, date, to, amount, "salary", Money.rub(stored), RateSource.API)

    private fun basis(transactions: List<Transaction>, accounts: List<Account> = listOf(card, cash, reserve, wallet)) =
        CostBasisCalculator.calculate(accounts, transactions, history, rates)

    @Test
    fun singleExchange_expensesCostWhatWasPaid() {
        val result = basis(listOf(buy("x", d1, 10_500_000L, 100_000L), spend("a", d2, usd(10_000L)), spend("b", d3, usd(90_000L))))
        assertEquals(Money.rub(1_050_000L), result.effectiveBase["a"])
        assertEquals(Money.rub(9_450_000L), result.effectiveBase["b"])
        assertEquals(Money.rub(10_500_000L), result.effectiveBase["x"])
        assertNull(result.averageCost["cash"])
    }

    @Test
    fun twoExchangesAtDifferentRates_weightedAverage() {
        val result = basis(listOf(buy("x1", d1, 10_000_000L, 100_000L), buy("x2", d2, 11_000_000L, 100_000L), spend("a", d3, usd(20_000L))))
        assertEquals(Money.rub(2_100_000L), result.effectiveBase["a"])
        assertEquals(RateQuote(Currency.USD, Currency.RUB, 105.0), result.averageCost["cash"])
    }

    @Test
    fun expenseBetweenExchanges_usesAverageAtThatMoment() {
        val result = basis(listOf(buy("x1", d1, 10_000_000L, 100_000L), spend("a", d2, usd(50_000L)), buy("x2", d3, 6_000_000L, 50_000L), spend("b", d4, usd(10_000L))))
        assertEquals(Money.rub(5_000_000L), result.effectiveBase["a"])
        assertEquals(Money.rub(1_100_000L), result.effectiveBase["b"])
    }

    @Test
    fun transfer_carriesCostToTheOtherAccount() {
        val transfer = Transaction.Transfer("t", d2, "cash", "reserve", usd(40_000L), Money.rub(1L))
        val result = basis(listOf(buy("x", d1, 10_500_000L, 100_000L), transfer, spend("a", d3, usd(10_000L), from = "reserve")))
        assertEquals(Money.rub(4_200_000L), result.effectiveBase["t"])
        assertEquals(Money.rub(1_050_000L), result.effectiveBase["a"])
        assertEquals(105.0, result.averageCost.getValue("cash").value)
        assertEquals(105.0, result.averageCost.getValue("reserve").value)
    }

    @Test
    fun income_entersAtMarketRateOfItsDate_andKeepsStoredValue() {
        val income = earn("i", d2, usd(10_000L), stored = 777L)
        val transactions = listOf(income, spend("a", d4, usd(10_000L)))
        val result = basis(transactions)
        assertEquals(Money.rub(900_000L), result.effectiveBase["a"])
        assertNull(result.effectiveBase["i"])
        assertSame(income, result.applyTo(transactions).first())
    }

    @Test
    fun systemCorrectionIncome_entersAtMarketLikeAnyIncome() {
        val correction = Transaction.Income("c", d3, "cash", usd(10_000L), "system", Money.rub(1L), RateSource.API)
        val result = basis(listOf(buy("x", d1, 10_500_000L, 100_000L), correction, spend("a", d4, usd(110_000L))))
        assertEquals(Money.rub(11_450_000L), result.effectiveBase["a"])
    }

    @Test
    fun openingBalance_entersAtMarketRateOfFirstOperation() {
        val opened = account("cash", Currency.USD, opening = 100_000L)
        val result = basis(listOf(spend("a", d3, usd(10_000L))), listOf(card, opened))
        assertEquals(Money.rub(950_000L), result.effectiveBase["a"])
        assertEquals(95.0, result.averageCost.getValue("cash").value)
    }

    @Test
    fun openingBalanceWithoutOperations_usesCurrentRate() {
        val opened = account("cash", Currency.USD, opening = 100_000L)
        assertEquals(100.0, basis(emptyList(), listOf(card, opened)).averageCost.getValue("cash").value)
    }

    @Test
    fun emptyAccount_expenseFallsBackToMarketOnItsDate() {
        val result = basis(listOf(spend("a", d2, usd(5_000L)), spend("b", d3, usd(5_000L))))
        assertEquals(Money.rub(450_000L), result.effectiveBase["a"])
        assertEquals(Money.rub(475_000L), result.effectiveBase["b"])
        assertNull(result.averageCost["cash"])
    }

    @Test
    fun negativeBalance_refillCoversDebtFirst_restKeepsCost() {
        val result = basis(listOf(spend("a", d1, usd(5_000L)), buy("x", d2, 1_050_000L, 10_000L), spend("b", d3, usd(10_000L))))
        assertEquals(Money.rub(450_000L), result.effectiveBase["a"])
        assertEquals(Money.rub(525_000L + 475_000L), result.effectiveBase["b"])
    }

    @Test
    fun backdatedExchange_changesLaterExpenses() {
        val expense = spend("a", d3, usd(10_000L))
        assertEquals(Money.rub(950_000L), basis(listOf(expense)).effectiveBase["a"])
        assertEquals(Money.rub(1_050_000L), basis(listOf(expense, buy("x", d1, 10_500_000L, 100_000L))).effectiveBase["a"])
    }

    @Test
    fun sameDay_exchangeIsProcessedBeforeExpense_whateverTheListOrder() {
        val result = basis(listOf(spend("a", d2, usd(10_000L)), buy("z", d2, 10_500_000L, 100_000L)))
        assertEquals(Money.rub(1_050_000L), result.effectiveBase["a"])
    }

    @Test
    fun foreignToForeignExchange_movesAverageCostOfGivenUnits() {
        val swap = Transaction.Exchange("s", d2, "cash", "wallet", usd(50_000L), Money(100_000L, xts), Money.rub(1L))
        val result = basis(listOf(buy("x", d1, 10_500_000L, 100_000L), swap, spend("a", d3, Money(10_000L, xts), from = "wallet")))
        assertEquals(Money.rub(5_250_000L), result.effectiveBase["s"])
        assertEquals(Money.rub(525_000L), result.effectiveBase["a"])
        assertEquals(105.0, result.averageCost.getValue("cash").value)
        val walletQuote = result.averageCost.getValue("wallet")
        assertEquals(Currency.RUB, walletQuote.unit)
        assertEquals(xts, walletQuote.price)
        assertTrue(abs(walletQuote.value - 1.0 / 0.525) < 1e-9)
    }

    @Test
    fun exchangeBackToRubles_isWorthReceivedRubles_andRemovesUnitsAtCost() {
        val sell = Transaction.Exchange("s", d3, "cash", "card", usd(40_000L), Money.rub(3_800_000L), Money.rub(1L))
        val result = basis(listOf(buy("x", d1, 10_500_000L, 100_000L), sell, spend("a", d4, usd(60_000L))))
        assertEquals(Money.rub(3_800_000L), result.effectiveBase["s"])
        assertEquals(Money.rub(6_300_000L), result.effectiveBase["a"])
    }

    @Test
    fun twoDecimalCurrency_partsAddUpToWhatWasPaid() {
        val result = basis(
            listOf(buy("x", d1, 10_000_001L, 100_000L), spend("a", d2, usd(33_333L)), spend("b", d2, usd(33_333L)), spend("c", d3, usd(33_334L))),
        )
        val parts = listOf("a", "b", "c").map { result.effectiveBase.getValue(it).minor }
        assertEquals(10_000_001L, parts.sum())
        assertTrue(parts.all { abs(it - 3_333_334L) <= 100L })
    }

    @Test
    fun zeroDecimalCurrency_partsAddUpToWhatWasPaid() {
        val exchange = Transaction.Exchange("x", d1, "card", "wallet", Money.rub(100_000L), Money(3L, xts), Money.rub(100_000L))
        val one = Money(1L, xts)
        val result = basis(listOf(exchange, spend("a", d2, one, from = "wallet"), spend("b", d2, one, from = "wallet"), spend("c", d3, one, from = "wallet")))
        val parts = listOf("a", "b", "c").map { result.effectiveBase.getValue(it).minor }
        assertEquals(100_000L, parts.sum())
        assertTrue(parts.all { abs(it - 33_333L) <= 1L })
    }

    @Test
    fun rubleAccounts_areNotTouched() {
        val rubExpense = spend("r", d2, Money.rub(50_000L), from = "card", stored = 50_000L)
        val transactions = listOf(rubExpense, buy("x", d1, 10_500_000L, 100_000L))
        val result = basis(transactions)
        assertNull(result.effectiveBase["r"])
        assertSame(rubExpense, result.applyTo(transactions).first())
        assertSame(CostBasis.EMPTY, basis(listOf(rubExpense), listOf(card)))
    }

    @Test
    fun applyTo_replacesStoredValueWithEffective() {
        val transactions = listOf(buy("x", d1, 10_500_000L, 100_000L), spend("a", d2, usd(10_000L), stored = 900_000L))
        val applied = basis(transactions).applyTo(transactions)
        assertEquals(Money.rub(1_050_000L), applied.first { it.id == "a" }.amountBase)
        assertEquals(usd(10_000L), applied.first { it.id == "a" }.ownAmount)
    }

    @Test
    fun currencyWithoutAnyRate_keepsStoredValues() {
        val noRates = CostBasisCalculator.calculate(
            listOf(card, wallet),
            listOf(spend("a", d2, Money(10L, xts), from = "wallet", stored = 555L)),
            emptyList(),
            RateTable(listOf(rate(Currency.USD, 100.0, d4))),
        )
        assertNull(noRates.effectiveBase["a"])
    }

    @Test
    fun marketRates_latestOnOrBefore_thenCurrent_thenEarliest() {
        val market = MarketRates(history, rates)
        assertEquals(90.0, market.basePerUnit(Currency.USD, d2))
        assertEquals(95.0, market.basePerUnit(Currency.USD, d3))
        assertEquals(100.0, market.basePerUnit(Currency.USD, LocalDate(2026, 9, 1)))
        assertEquals(1.0, market.basePerUnit(Currency.RUB, d1))
        assertEquals(Money.rub(900_000L), market.toBase(usd(10_000L), d2))
        val historyOnly = MarketRates(history, RateTable(emptyList()))
        assertEquals(90.0, historyOnly.basePerUnit(Currency.USD, LocalDate(2026, 9, 1)))
        assertNull(MarketRates(emptyList(), RateTable(emptyList())).basePerUnit(xts, d1))
    }
}
