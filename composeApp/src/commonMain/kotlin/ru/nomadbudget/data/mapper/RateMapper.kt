package ru.nomadbudget.data.mapper

import kotlinx.datetime.LocalDate
import ru.nomadbudget.data.dto.CurrencyDto
import ru.nomadbudget.data.dto.ExchangeRateDto
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.RateSource

object RateMapper {

    fun toDomain(dto: ExchangeRateDto, currencies: Map<String, Currency>): ExchangeRate? {
        val quote = currencies[dto.quote] ?: return null
        return ExchangeRate(
            quote = quote,
            basePerUnit = dto.rate,
            date = LocalDate.parse(dto.rateDate),
            source = RateSource.API,
        )
    }
}

object CurrencyMapper {

    fun toDomain(dto: CurrencyDto): Currency = Currency(
        code = dto.code,
        minorUnits = dto.minorUnits,
        symbol = dto.symbol,
    )
}
