package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

enum class RateSource { API, MANUAL }

data class ExchangeRate(
    val quote: Currency,
    val basePerUnit: Double,
    val date: LocalDate,
    val source: RateSource,
) {
    init {
        require(basePerUnit > 0.0) { "Курс должен быть положительным" }
    }
}

class RateTable(rates: List<ExchangeRate>) {

    private val byQuote: Map<Currency, ExchangeRate> = rates.associateBy { it.quote }

    fun rateFor(currency: Currency): ExchangeRate? = when (currency) {
        Currency.BASE -> null
        else -> byQuote[currency]
    }

    fun basePerUnit(currency: Currency): Double = when (currency) {
        Currency.BASE -> 1.0
        else -> requireNotNull(byQuote[currency]) { "Нет курса для ${currency.code}" }.basePerUnit
    }

    fun toBase(money: Money): Money {
        val majorInBase = money.toMajor() * basePerUnit(money.currency)
        return Money((majorInBase * Currency.BASE.minorFactor).roundToLong(), Currency.BASE)
    }

    fun fromBase(base: Money, target: Currency): Money {
        require(base.currency == Currency.BASE) { "Ожидалась сумма в ${Currency.BASE.code}" }
        val majorInTarget = base.toMajor() / basePerUnit(target)
        return Money((majorInTarget * target.minorFactor).roundToLong(), target)
    }

    fun cross(from: Currency, to: Currency): Double = basePerUnit(from) / basePerUnit(to)
}
