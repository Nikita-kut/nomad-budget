package ru.nomadbudget.presentation.format

import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmountMaskTest {

    @AfterTest
    fun reset() {
        AmountMask.hidden = false
    }

    @Test
    fun hiddenAmountsShowMaskWithCurrency() {
        AmountMask.hidden = true
        assertEquals("••• ₽", MoneyFormat.format(Money.rub(123_456_00L)))
        assertEquals("••• ₽", MoneyFormat.formatSigned(Money.rub(-5_00L)))
        assertEquals("$•••", MoneyFormat.format(Money(1_00L, Currency.USD)))
        assertEquals("•••", MoneyFormat.formatAxis(1_500_000.0))
        assertEquals("••• ₽", MoneyFormat.formatWhole(42.0, currency = Currency.RUB))
    }

    @Test
    fun editingAndRatesStayVisible() {
        AmountMask.hidden = true
        assertEquals("1234,50", MoneyFormat.editText(Money.rub(1_234_50L)))
        assertEquals("92,46", MoneyFormat.formatQuote(92.456))
    }

    @Test
    fun privacyRemembersStateAndHideOnStart() {
        val store = MapStore()
        val privacy = AmountPrivacy(store)
        assertFalse(privacy.hidden)
        privacy.toggle()
        assertTrue(AmountPrivacy(store).hidden)
        privacy.setHidden(false)
        privacy.changeHideOnStart(true)
        assertTrue(AmountPrivacy(store).hidden)
    }

    private class MapStore : ru.nomadbudget.domain.repository.KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }
        override fun remove(key: String) {
            map.remove(key)
        }
        override fun keys(prefix: String): List<String> = map.keys.filter { it.startsWith(prefix) }
        override fun clear() = map.clear()
    }
}
