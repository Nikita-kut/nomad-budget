package ru.nomadbudget.presentation.onboarding

import ru.nomadbudget.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingStateTest {

    private val xts = Currency("XTS", 2, "XTS")

    @Test
    fun changeBase_movesDefaultAccounts_andGoesToResult() {
        val form = OnboardingState()
        form.changeBase(xts)
        assertTrue(form.accounts.all { it.currencyCode == "XTS" })
        assertEquals("XTS", form.result().baseCode)
        assertEquals(listOf("XTS"), form.availableCurrencies())
    }

    @Test
    fun changeBase_toExtraCurrency_removesItFromExtras_andItsAutoCash() {
        val form = OnboardingState()
        form.currencyCodes += "XTS"
        form.syncAutoAccounts()
        form.changeBase(xts)
        assertFalse("XTS" in form.currencyCodes)
        assertEquals(1, form.accounts.count { it.name.startsWith(OnboardingDefaults.CASH) })
        assertTrue(form.accounts.all { it.currencyCode == "XTS" })
    }

    @Test
    fun changeBase_keepsAccountsInOtherCurrencies() {
        val form = OnboardingState()
        form.currencyCodes += "USD"
        form.syncAutoAccounts()
        form.changeBase(xts)
        assertEquals(1, form.accounts.count { it.currencyCode == "USD" })
    }
}
