package ru.nomadbudget.presentation.format

import kotlin.test.Test
import kotlin.test.assertEquals

class PluralTest {

    private fun ops(n: Int) = plural(n, "операция", "операции", "операций")

    @Test
    fun one() {
        assertEquals("1 операция", ops(1))
        assertEquals("21 операция", ops(21))
        assertEquals("101 операция", ops(101))
    }

    @Test
    fun few() {
        assertEquals("2 операции", ops(2))
        assertEquals("3 операции", ops(3))
        assertEquals("4 операции", ops(4))
        assertEquals("22 операции", ops(22))
        assertEquals("104 операции", ops(104))
    }

    @Test
    fun many() {
        assertEquals("0 операций", ops(0))
        assertEquals("5 операций", ops(5))
        assertEquals("10 операций", ops(10))
        assertEquals("25 операций", ops(25))
        assertEquals("100 операций", ops(100))
    }

    @Test
    fun teensAreMany() {
        assertEquals("11 операций", ops(11))
        assertEquals("12 операций", ops(12))
        assertEquals("13 операций", ops(13))
        assertEquals("14 операций", ops(14))
        assertEquals("111 операций", ops(111))
        assertEquals("1012 операций", ops(1012))
    }

    @Test
    fun longAndHelpers() {
        assertEquals("2 раза", plural(2L, "раз", "раза", "раз"))
        assertEquals("1 раз", plural(1L, "раз", "раза", "раз"))
        assertEquals("12 раз", plural(12L, "раз", "раза", "раз"))
        assertEquals("3 операции", operations(3))
    }
}
