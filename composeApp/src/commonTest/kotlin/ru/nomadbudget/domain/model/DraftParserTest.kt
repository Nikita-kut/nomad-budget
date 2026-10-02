package ru.nomadbudget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DraftParserTest {

    @Test
    fun thousands_suffix() {
        assertEquals("60000", DraftParser.amountText("кофе 60к"))
        assertEquals("60000", DraftParser.amountText("кофе 60 k"))
        assertEquals("1500000", DraftParser.amountText("аренда 1.5м"))
    }

    @Test
    fun plainNumber_andDecimal() {
        assertEquals("250", DraftParser.amountText("такси 250"))
        assertEquals("12,5", DraftParser.amountText("12.5 usd обед"))
    }

    @Test
    fun noNumber_returnsNull() {
        assertNull(DraftParser.amountText("вернуть долг другу"))
    }

    @Test
    fun noteWithoutAmount_stripsNumber() {
        assertEquals("кофе", DraftParser.noteWithoutAmount("кофе 60к"))
        assertEquals("такси до дома", DraftParser.noteWithoutAmount("250 такси до дома"))
        assertEquals("вернуть долг", DraftParser.noteWithoutAmount("вернуть долг"))
    }
}
