package ru.nomadbudget.domain.model

data class Currency(val code: String, val minorUnits: Int, val symbol: String) {

    init {
        require(code.length == 3) { "Код валюты должен быть из трёх букв" }
        require(minorUnits in 0..4) { "Знаков после запятой может быть от 0 до 4" }
    }

    val minorFactor: Long = powerOfTen(minorUnits)

    val symbolBeforeAmount: Boolean = symbol == "$"

    companion object {
        val RUB: Currency = Currency("RUB", 2, "₽")
        val USD: Currency = Currency("USD", 2, "$")
        val BASE: Currency = RUB

        val builtIn: List<Currency> = listOf(RUB, USD)
    }
}

private fun powerOfTen(exponent: Int): Long {
    var result = 1L
    repeat(exponent) { result *= 10L }
    return result
}
