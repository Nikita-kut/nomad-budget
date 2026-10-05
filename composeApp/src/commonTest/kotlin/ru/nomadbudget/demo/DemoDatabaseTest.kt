package ru.nomadbudget.demo

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoDatabaseTest {

    private val today = LocalDate(2026, 10, 22)
    private val db = DemoDatabase(today)

    @Test
    fun seed_isDeterministic_andHasNoFutureOperations() {
        val again = DemoDatabase(today)
        assertEquals(db.transactions.size, again.transactions.size)
        assertTrue(db.transactions.all { it.date <= today })
    }

    @Test
    fun seed_coversSixPeriods_withPlanForCurrent() {
        val current = SalaryCycle.periodContaining(today)
        assertEquals(6, db.budgetLines.size)
        assertTrue(db.budgetLines.getValue(db.periodId(current)).isNotEmpty())
        assertTrue(db.transactions.any { it.date in current })
    }

    @Test
    fun loanPayments_reducePrincipal_andRollbackRestoresIt() {
        val debt = db.debts.first { it.id == "debt_a" }
        val payment = db.transactions.filterIsInstance<Transaction.Expense>().first { it.debtId == debt.id }
        db.applyDebtPrincipal(payment, sign = -1)
        val restored = db.debts.first { it.id == debt.id }
        assertEquals(debt.principalRemaining.minor + (payment.debtPrincipal?.minor ?: 0L), restored.principalRemaining.minor)
        assertTrue(debt.principalRemaining.minor > 0L)
    }

    @Test
    fun ratesCoverEveryDay_andAllCurrencies() {
        val rates = db.ratesOn(today)
        assertTrue(db.currencies.all(rates::hasRate))
    }
}
