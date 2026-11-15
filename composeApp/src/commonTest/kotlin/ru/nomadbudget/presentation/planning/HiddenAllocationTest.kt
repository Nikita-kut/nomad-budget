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

class HiddenAllocationTest {
    private val zero = Money.zero(Currency.DEFAULT_BASE)
    private fun money(units: Long) = Money(units * 100, Currency.DEFAULT_BASE)

    @AfterTest
    fun reset() {
        FeatureGate.state = FeatureState.EVERYTHING
    }

    @Test
    fun visiblePlusHiddenAlwaysAddsUpForEveryModuleSet() {
        val debts = money(30_000)
        val funds = money(5_000)
        val savings = money(10_000)
        val modules = listOf(AppModule.DEBTS, AppModule.FUNDS, AppModule.CUSHION)
        (0 until (1 shl modules.size)).forEach { mask ->
            val off = modules.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.map { it.key }.toSet()
            FeatureGate.state = FeatureState.EVERYTHING.copy(modulesOff = off)
            val visible = listOfNotNull(
                debts.takeIf { FeatureGate.isOn(Feature.DEBTS_PLAN) },
                funds.takeIf { FeatureGate.isOn(Feature.FUNDS_PLAN) },
                savings.takeIf { FeatureGate.isOn(Feature.CUSHION_PLAN) },
            ).fold(zero) { a, b -> a + b }
            val hidden = hiddenAllocation(debts, funds, savings, zero)?.second ?: zero
            assertEquals(debts + funds + savings, visible + hidden, "выключено $off")
        }
    }

    @Test
    fun hiddenLineNamesOnlyHiddenModulesWithMoney() {
        FeatureGate.state = FeatureState.EVERYTHING.copy(modulesOff = setOf("debts", "funds"))
        assertEquals("кредиты", hiddenAllocation(money(100), zero, money(5), zero)?.first)
        FeatureGate.state = FeatureState.EVERYTHING.copy(blocksOff = setOf(Feature.CUSHION_PLAN.key))
        assertEquals("«Себе»" to money(5), hiddenAllocation(zero, zero, money(5), zero))
        FeatureGate.state = FeatureState.EVERYTHING
        assertEquals(null, hiddenAllocation(money(100), money(1), money(5), zero))
    }
}
