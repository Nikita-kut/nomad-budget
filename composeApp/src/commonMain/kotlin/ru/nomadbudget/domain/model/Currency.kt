package ru.nomadbudget.domain.model

enum class Currency(val code: String, val minorUnits: Int, val symbol: String) {
    RUB("RUB", 2, "₽"),
    USD("USD", 2, "$"),
    VND("VND", 0, "₫");

    val minorFactor: Long = powerOfTen(minorUnits)

    companion object {
        val BASE: Currency = RUB

        fun fromCode(code: String): Currency = entries.first { it.code == code }
    }
}

private fun powerOfTen(exponent: Int): Long {
    var result = 1L
    repeat(exponent) { result *= 10L }
    return result
}
