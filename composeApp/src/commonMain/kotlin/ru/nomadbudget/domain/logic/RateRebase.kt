package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.RateTable

object RateRebase {

    fun table(pivot: RateTable, base: Currency): RateTable {
        if (base == pivot.base) return pivot
        val baseRate = pivot.rateFor(base) ?: return RateTable(emptyList(), base)
        val others = pivot.all.filter { it.quote != base }.map { it.copy(basePerUnit = it.basePerUnit / baseRate.basePerUnit) }
        val pivotInBase = baseRate.copy(quote = pivot.base, basePerUnit = 1.0 / baseRate.basePerUnit)
        return RateTable(others + pivotInBase, base)
    }

    fun history(pivot: List<ExchangeRate>, pivotCurrency: Currency, base: Currency): List<ExchangeRate> {
        if (base == pivotCurrency) return pivot
        val baseSeries = pivot.filter { it.quote == base }.sortedBy { it.date }
        if (baseSeries.isEmpty()) return emptyList()
        fun baseOn(rate: ExchangeRate): ExchangeRate = baseSeries.lastOrNull { it.date <= rate.date } ?: baseSeries.first()
        val others = pivot.filter { it.quote != base }.map { rate -> rate.copy(basePerUnit = rate.basePerUnit / baseOn(rate).basePerUnit) }
        val pivotInBase = baseSeries.map { it.copy(quote = pivotCurrency, basePerUnit = 1.0 / it.basePerUnit) }
        return (others + pivotInBase).sortedBy { it.date }
    }
}
