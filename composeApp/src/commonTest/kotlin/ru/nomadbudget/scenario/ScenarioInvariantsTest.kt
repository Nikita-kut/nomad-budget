package ru.nomadbudget.scenario

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import ru.nomadbudget.domain.logic.DebtSchedule
import ru.nomadbudget.domain.model.DebtCloseReason
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.home.HomeState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ScenarioInvariantsTest {

    private val from = LocalDate(2026, 8, 5)

    @Test
    fun invariantsHoldOnRandomLifecycles() {
        repeat(SEEDS) { seed ->
            CHECK_DAYS.forEach { offset ->
                val today = from.plus(offset, DateTimeUnit.DAY)
                val ledger = randomLedger(seed, today)
                runCatching { checkAll(ledger, today) }.onFailure { fail("seed=$seed today=$today: ${it.message}") }
            }
        }
    }

    private fun randomLedger(seed: Int, to: LocalDate): Ledger {
        val random = Random(seed)
        val l = Ledger()
        val days = from.daysUntil(to) + 1
        fun day() = from.plus(random.nextInt(0, days), DateTimeUnit.DAY)
        var start = l.periodOf(from).start
        while (start <= to) {
            l.planMonth(start, income = 30_000_000L, food = 5_000_000L, savings = if (random.nextBoolean()) 3_000_000L else null)
            l.income(start, 30_000_000L)
            start = l.periodOf(start).endExclusive
        }
        repeat(3) { index ->
            val opened = day()
            val debt = l.openDebt("d$index", opened, principal = random.nextLong(500_000L, 30_000_000L), payment = random.nextLong(200_000L, 3_000_000L), payDay = random.nextInt(1, 29))
            var month = l.periodOf(opened)
            while (month.start <= to) {
                val due = ru.nomadbudget.domain.logic.MonthModel.dueDate(month, debt.payDay)
                if (due != null && due >= opened && due <= to && random.nextInt(4) > 0) {
                    val current = l.debts.first { it.id == debt.id }
                    if (!current.isClosed && current.principalRemaining.minor > 0L) {
                        l.pay(debt.id, due, current.paymentOn(due).minor, from = if (random.nextInt(5) == 0) l.cushion else l.card)
                    }
                }
                month = l.periodOf(month.endExclusive)
            }
            if (random.nextBoolean()) {
                val date = day().let { if (it < opened) opened else it }
                val current = l.debts.first { it.id == debt.id }
                if (!current.isClosed && current.principalRemaining.minor > 0L) {
                    val amount = random.nextLong(1L, current.principalRemaining.minor + 1)
                    l.pay(debt.id, date, amount, from = if (random.nextBoolean()) l.cushion else l.card, early = true)
                    when (random.nextInt(3)) {
                        0 -> l.close(debt.id, date, DebtCloseReason.EARLY)
                        1 -> l.changePayment(debt.id, date.plus(random.nextInt(1, 40), DateTimeUnit.DAY), random.nextLong(100_000L, 1_000_000L))
                    }
                }
            }
            if (random.nextInt(4) == 0) l.planEarly(debt.id, l.periodOf(to).start, random.nextLong(100_000L, 2_000_000L), fromCushion = random.nextBoolean())
        }
        repeat(random.nextInt(10, 30)) { l.spend(day(), random.nextLong(10_000L, 1_000_000L), account = if (random.nextInt(6) == 0) l.cushion else l.card) }
        repeat(random.nextInt(0, 4)) { l.move(day(), random.nextLong(100_000L, 5_000_000L), l.card, l.cushion) }
        repeat(random.nextInt(0, 3)) { l.move(day(), random.nextLong(100_000L, 2_000_000L), l.cushion, l.cushion2) }
        repeat(random.nextInt(0, 3)) { l.move(day(), random.nextLong(100_000L, 2_000_000L), l.card, l.fund) }
        repeat(random.nextInt(0, 3)) { l.exchange(day(), random.nextLong(100_000L, 1_000_000L), random.nextLong(1_000L, 10_000L)) }
        repeat(random.nextInt(0, 2)) { l.correct(day(), random.nextLong(1_000L, 50_000L)) }
        if (random.nextInt(4) == 0) l.archive(l.cash, to)
        if (random.nextInt(3) == 0) {
            l.debts.firstOrNull { !it.isClosed && it.principalRemaining.minor > 0L && (it.openedOn == null || it.openedOn <= to) }?.let { debt ->
                l.pay(debt.id, to, debt.principalRemaining.minor, from = if (random.nextBoolean()) l.cushion else l.card, early = true)
                l.close(debt.id, to, DebtCloseReason.EARLY)
            }
        }
        return l
    }

    private fun checkAll(l: Ledger, today: LocalDate) {
        val state = l.state(today)
        amountsAreSane(state)
        historyMatchesMonth(state)
        debtScreensAgree(l, state, today)
        cushionRepaymentKeepsPlan(l, today)
        cushionEarlyNeverInPlan(l, today)
        archivingChangesNoMonthNumbers(l, today)
        moveBetweenCushionsChangesNothing(l, today)
    }

    private fun amountsAreSane(state: HomeState) {
        val month = state.month
        month.debts.forEach {
            assertTrue(it.planned.minor >= 0L && it.left.minor >= 0L && it.budgetPlanned.minor >= 0L, "отрицательные суммы по ${it.debt.id}")
            assertTrue(it.left.minor <= it.budgetPlanned.minor + 1, "осталось ${it.left} больше плана ${it.budgetPlanned} по ${it.debt.id}")
            assertTrue(it.regularLeft.currency == it.debt.currency, "regularLeft не в валюте кредита")
        }
        assertTrue(month.debtsPaid <= month.debts.size)
        month.funds.forEach { assertTrue(!it.contributionLeft.isNegative) }
        listOf(month.unallocated, month.carryOver, month.saved, state.capital.netWorth, state.obligatoryMonthly).forEach {
            assertEquals(state.base, it.currency)
        }
    }

    private fun historyMatchesMonth(state: HomeState) {
        val point = state.monthlyHistory.lastOrNull { it.period == state.period } ?: return
        assertEquals(state.month.saved, point.netSaved, "«Себе» в графиках и в месяце разные")
        assertEquals(state.month.flexibleFact, point.envelopeExpense, "факт конвертов в графиках и в месяце разный")
        val capitalMonth = state.capital.months.lastOrNull { it.period == state.period } ?: return
        assertEquals(state.month.cushionToDebts, capitalMonth.cushionToDebts, "кредиты с подушки в «Капитале» и в месяце разные")
        assertEquals(state.month.cushionNet, capitalMonth.saved, "изменение подушки в «Капитале» и в месяце разное")
    }

    private fun debtScreensAgree(l: Ledger, state: HomeState, today: LocalDate) {
        val current = state.debtMonths.lastOrNull { it.isCurrent } ?: return
        assertEquals(state.capital.debt, current.debtAtEnd, "остаток долга в истории и в «Капитале» разный")
        val payments = state.transactions.filterIsInstance<Transaction.Expense>().filter { it.debtId != null }
        val comparable = l.debts.none { debt ->
            debt.paymentChanges.drop(1).any { it.effectiveFrom >= state.period.start && it.effectiveFrom < state.period.endExclusive } ||
                (DebtSchedule.opensAfterDue(debt, state.period) && !debt.isClosed) ||
                (debt.openedOn != null && debt.openedOn > today)
        }
        if (comparable) {
            assertEquals(state.obligatoryMonthly, current.obligatory, "обязательный платёж на «Кредитах» и в истории разный")
        }
        assertTrue(payments.all { it.date <= today })
    }

    private fun cushionRepaymentKeepsPlan(l: Ledger, today: LocalDate) {
        val before = l.state(today).month
        val target = l.debts.firstOrNull { it.principalRemaining.minor > 0L && (it.openedOn == null || it.openedOn <= today) } ?: return
        val probe = l.copy()
        val amount = minOf(target.principalRemaining.minor, 100_000L)
        probe.pay(target.id, today, amount, from = probe.cushion, early = true)
        val after = probe.state(today).month
        assertEquals(before.unallocated, after.unallocated, "досрочка с подушки поменяла «Не распределено»")
        assertEquals(before.canSpend, after.canSpend, "досрочка с подушки поменяла «Можно потратить»")
        assertEquals(before.saved, after.saved, "досрочка с подушки поменяла «Себе»")
        assertEquals(before.cushionNet - Money.rub(amount), after.cushionNet, "подушка за месяц не уменьшилась на досрочку")
    }

    private fun cushionEarlyNeverInPlan(l: Ledger, today: LocalDate) {
        val cushions = setOf(l.cushion.id, l.cushion2.id)
        val before = l.state(today).month
        val probe = l.copy().also { copy -> copy.transactions.removeAll { it is Transaction.Expense && it.debtEarly && it.accountId in cushions } }
        val after = probe.state(today).month
        assertEquals(after.unallocated, before.unallocated, "досрочки с подушки попали в «Не распределено»")
        assertEquals(after.debtsPlanned, before.debtsPlanned, "досрочки с подушки попали в платежи по кредитам месяца")
        assertEquals(after.canSpend, before.canSpend, "досрочки с подушки попали в «Можно потратить»")
        assertEquals(after.saved, before.saved, "досрочки с подушки уменьшили «Себе»")
    }

    private fun archivingChangesNoMonthNumbers(l: Ledger, today: LocalDate) {
        val before = l.state(today)
        val probe = l.copy().also { it.archive(it.card, today) }.state(today)
        assertEquals(before.month.carryOver, probe.month.carryOver, "архивирование счёта поменяло остаток с прошлого месяца")
        assertEquals(before.month.unallocated, probe.month.unallocated, "архивирование счёта поменяло «Не распределено»")
        assertEquals(before.month.flexibleFact, probe.month.flexibleFact, "архивирование счёта поменяло траты по конвертам")
    }

    private fun moveBetweenCushionsChangesNothing(l: Ledger, today: LocalDate) {
        val before = l.state(today).month
        val probe = l.copy().also { it.move(today, 1_000_000L, it.cushion, it.cushion2) }
        val after = probe.state(today).month
        assertEquals(before.saved, after.saved)
        assertEquals(before.cushionNet, after.cushionNet)
        assertEquals(before.unallocated, after.unallocated)
    }

    private companion object {
        const val SEEDS = 150
        val CHECK_DAYS = listOf(0, 31, 62, 77, 90, 101, 107)
    }
}
