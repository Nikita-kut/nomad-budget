package ru.nomadbudget.presentation.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingCurrencyTest {
    @Test
    fun oneCurrencyIsTheDefaultAndDropsExtraCurrencies() {
        val form = OnboardingState()
        form.multiCurrency = true
        form.currencyCodes += "USD"
        form.syncAutoAccounts()
        form.multiCurrency = false
        val result = form.result()
        assertFalse(result.multiCurrency)
        assertEquals(emptyList(), result.currencyCodes)
        assertTrue(result.accounts.all { it.currencyCode == form.base.code })
    }

    @Test
    fun severalCurrenciesKeepTheChosenOnes() {
        val form = OnboardingState()
        form.multiCurrency = true
        form.currencyCodes += "USD"
        val result = form.result()
        assertTrue(result.multiCurrency)
        assertEquals(listOf("USD"), result.currencyCodes)
    }
}
