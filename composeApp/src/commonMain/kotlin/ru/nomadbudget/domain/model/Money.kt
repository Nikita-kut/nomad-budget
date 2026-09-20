package ru.nomadbudget.domain.model

import kotlin.math.roundToLong

data class Money(val minor: Long, val currency: Currency) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isNegative: Boolean get() = minor < 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minor = minor + other.minor)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minor = minor - other.minor)
    }

    operator fun unaryMinus(): Money = copy(minor = -minor)

    fun toMajor(): Double = minor.toDouble() / currency.minorFactor

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Нельзя складывать ${currency.code} и ${other.currency.code} без курса"
        }
    }

    companion object {
        fun zero(currency: Currency): Money = Money(0L, currency)

        fun ofMajor(value: Double, currency: Currency): Money =
            Money((value * currency.minorFactor).roundToLong(), currency)

        fun rub(minor: Long): Money = Money(minor, Currency.RUB)
    }
}

fun Iterable<Money>.sumIn(currency: Currency): Money =
    fold(Money.zero(currency)) { acc, money -> acc + money }
