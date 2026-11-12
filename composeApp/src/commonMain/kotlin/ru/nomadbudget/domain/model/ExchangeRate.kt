package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong
import kotlin.time.Instant

enum class RateSource { API, MANUAL }

data class ExchangeRate(
    val quote: Currency,
    val basePerUnit: Double,
    val date: LocalDate,
    val source: RateSource,
    val sourceName: String = source.name.lowercase(),
    val fetchedAt: Instant? = null,
) {
    init {
        require(basePerUnit > 0.0) { "Курс должен быть положительным" }
    }
}

class RateTable(rates: List<ExchangeRate>, val base: Currency = Currency.RUB) {

    private val byQuote: Map<Currency, ExchangeRate> = rates.associateBy { it.quote }

    val all: List<ExchangeRate> = byQuote.values.sortedBy { it.quote.code }

    fun rateFor(currency: Currency): ExchangeRate? = when (currency) {
        base -> null
        else -> byQuote[currency]
    }

    fun hasRate(currency: Currency): Boolean = currency == base || currency in byQuote

    fun basePerUnit(currency: Currency): Double = when (currency) {
        base -> 1.0
        else -> requireNotNull(byQuote[currency]) { "Нет курса для ${currency.code}" }.basePerUnit
    }

    fun toBase(money: Money): Money {
        val majorInBase = money.toMajor() * basePerUnit(money.currency)
        return Money((majorInBase * base.minorFactor).roundToLong(), base)
    }

    fun toBaseOrNull(money: Money): Money? = if (hasRate(money.currency)) toBase(money) else null

    fun fromBase(amount: Money, target: Currency): Money {
        require(amount.currency == base) { "Ожидалась сумма в ${base.code}" }
        val majorInTarget = amount.toMajor() / basePerUnit(target)
        return Money((majorInTarget * target.minorFactor).roundToLong(), target)
    }

    fun cross(from: Currency, to: Currency): Double = basePerUnit(from) / basePerUnit(to)
}
