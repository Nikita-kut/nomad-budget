package ru.nomadbudget.domain.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.DefaultRates
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.TransactionKind

class EntryDefaultsTest {

    private val today = LocalDate(2026, 10, 6)

    private fun expense(rub: Long, account: String = "card", category: String = "food", date: LocalDate = LocalDate(2026, 9, 20)) = Transaction.Expense(
        id = "e$rub$account$date", date = date, accountId = account, amount = Money.rub(rub), categoryId = category,
        subcategoryId = null, amountBase = Money.rub(rub), rateSource = RateSource.API,
    )

    private fun transfer(from: String, to: String, date: LocalDate) =
        Transaction.Transfer("t$from$to$date", date, from, to, Money.rub(100L), Money.rub(100L))

    @Test
    fun threshold_isFiveTimesLargestRecentOperation() {
        assertEquals(Money.rub(22_500_000L), EntryDefaults.largeAmountThreshold(listOf(expense(4_500_000L), expense(100_000L)), today, TransactionKind.EXPENSE, DefaultRates.table()))
    }

    @Test
    fun threshold_hasFloorForNewUsers_andIgnoresOtherKinds() {
        assertEquals(Money.rub(10_000_000L), EntryDefaults.largeAmountThreshold(emptyList(), today, TransactionKind.EXPENSE, DefaultRates.table()))
        assertEquals(Money.rub(10_000_000L), EntryDefaults.largeAmountThreshold(listOf(expense(4_500_000L)), today, TransactionKind.INCOME, DefaultRates.table()))
    }

    @Test
    fun threshold_ignoresOperationsOlderThanLookback() {
        val old = expense(9_000_000L, date = today.minusDays(EntryDefaults.LARGE_LOOKBACK_DAYS + 1))
        assertEquals(Money.rub(10_000_000L), EntryDefaults.largeAmountThreshold(listOf(old), today, TransactionKind.EXPENSE, DefaultRates.table()))
    }

    @Test
    fun lastUsedAccount_prefersSameCategory_thenMostRecent_onlyActive() {
        val tx = listOf(
            expense(100L, account = "cash", category = "food", date = LocalDate(2026, 9, 1)),
            expense(100L, account = "card", category = "taxi", date = LocalDate(2026, 9, 5)),
            expense(100L, account = "old", category = "food", date = LocalDate(2026, 9, 9)),
        )
        val active = setOf("cash", "card")
        assertEquals("cash", EntryDefaults.lastUsedAccountId(tx, active, TransactionKind.EXPENSE, "food"))
        assertEquals("card", EntryDefaults.lastUsedAccountId(tx, active, TransactionKind.EXPENSE, null))
        assertNull(EntryDefaults.lastUsedAccountId(tx, active, TransactionKind.INCOME, null))
    }

    @Test
    fun lastUsedAccount_forTransfer_usesSourceAccount() {
        val tx = listOf(transfer("card", "cushion", LocalDate(2026, 9, 2)), transfer("archived", "card", LocalDate(2026, 9, 8)))
        assertEquals("card", EntryDefaults.lastUsedAccountId(tx, setOf("card", "cushion"), TransactionKind.TRANSFER))
    }

    private fun LocalDate.minusDays(days: Int): LocalDate = kotlinx.datetime.LocalDate.fromEpochDays(toEpochDays() - days)
}
