package ru.nomadbudget.presentation.format

import ru.nomadbudget.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyWordsTest {

    @Test
    fun knownCurrencies_inWords_othersByCode() {
        assertEquals("в рублях", CurrencyWords.inCurrency(Currency.RUB))
        assertEquals("в долларах", CurrencyWords.inCurrency(Currency.USD))
        assertEquals("в XTS", CurrencyWords.inCurrency(Currency("XTS", 2, "XTS")))
        assertEquals("В XTS", CurrencyWords.columnIn(Currency("XTS", 2, "XTS")))
    }
}
