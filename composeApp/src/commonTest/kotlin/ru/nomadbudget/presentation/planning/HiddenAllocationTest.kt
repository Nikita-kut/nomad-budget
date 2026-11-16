package ru.nomadbudget.presentation.planning

import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.features.AppModule
import ru.nomadbudget.features.Feature
import ru.nomadbudget.features.FeatureGate
import ru.nomadbudget.features.FeatureState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HiddenAllocationTest {
    private fun money(units: Long) = Money(units * 100, Currency.DEFAULT_BASE)

    private val debts = money(30_000)
    private val funds = money(5_000)
    private val envelopes = money(60_000)
    private val savings = money(10_000)
    private val available = money(120_000)
    private val unallocated = available - debts - funds - envelopes - savings

    private fun parts() = allocationParts(debts, funds, envelopes, savings, unallocated)

    @AfterTest
    fun reset() {
        FeatureGate.state = FeatureState.EVERYTHING
    }

    @Test
    fun rowsShownInThePlanAddUpToAvailableForEveryModuleSet() {
        val modules = listOf(AppModule.DEBTS, AppModule.FUNDS, AppModule.CUSHION)
        (0 until (1 shl modules.size)).forEach { mask ->
            val off = modules.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.map { it.key }.toSet()
            FeatureGate.state = FeatureState.EVERYTHING.copy(modulesOff = off)
            val rows = parts()
            assertEquals(available, rows.fold(Money.zero(Currency.DEFAULT_BASE)) { sum, part -> sum + part.money }, "выключено $off: $rows")
            assertEquals(off.isNotEmpty(), rows.any { it.kind == AllocationKind.HIDDEN }, "строка скрытых при $off")
        }
    }

    @Test
    fun hiddenBlockInsideAModuleAlsoGoesToTheHiddenLine() {
        FeatureGate.state = FeatureState.EVERYTHING.copy(blocksOff = setOf(Feature.CUSHION_PLAN.key))
        val hidden = parts().single { it.kind == AllocationKind.HIDDEN }
        assertEquals("Скрытые модули: «Отложить»", hidden.label)
        assertEquals(savings, hidden.money)
    }

    @Test
    fun hiddenModuleWithoutMoneyAddsNoLine() {
        FeatureGate.state = FeatureState.EVERYTHING.copy(modulesOff = setOf("funds"))
        val rows = allocationParts(debts, Money.zero(Currency.DEFAULT_BASE), envelopes, savings, unallocated + funds)
        assertTrue(rows.none { it.kind == AllocationKind.HIDDEN })
    }
}
