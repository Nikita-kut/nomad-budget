package ru.nomadbudget.presentation.entry

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.home.EntryType
import ru.nomadbudget.presentation.home.HomeState
import kotlin.test.Test
import kotlin.test.assertEquals

class LargeAmountTest {

    private val today = LocalDate(2026, 10, 6)

    private fun expense(rub: Long) = Transaction.Expense(
        id = "e$rub", date = LocalDate(2026, 9, 20), accountId = "card", amount = Money.rub(rub), categoryId = "food",
        subcategoryId = null, amountBase = Money.rub(rub), rateSource = RateSource.API,
    )

    private fun state(vararg tx: Transaction) = HomeState(today = today, period = SalaryCycle.periodContaining(today), transactions = tx.toList())

    @Test
    fun threshold_isFiveTimesLargestRecentOperation() {
        assertEquals(Money.rub(22_500_000L), largeAmountThreshold(state(expense(4_500_000L), expense(100_000L)), EntryType.EXPENSE))
    }

    @Test
    fun threshold_hasFloorForNewUsers() {
        assertEquals(Money.rub(10_000_000L), largeAmountThreshold(state(), EntryType.EXPENSE))
    }
}
