package ru.nomadbudget.data.mapper

import kotlinx.datetime.LocalDate
import ru.nomadbudget.data.dto.ExchangeRateDto
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.RateSource

object RateMapper {

    fun toDomain(dto: ExchangeRateDto): ExchangeRate = ExchangeRate(
        quote = Currency.fromCode(dto.quote),
        basePerUnit = dto.rate,
        date = LocalDate.parse(dto.rateDate),
        source = RateSource.API,
    )
}
