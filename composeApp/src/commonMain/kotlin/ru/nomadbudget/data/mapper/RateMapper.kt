package ru.nomadbudget.data.mapper

import kotlinx.datetime.LocalDate
import ru.nomadbudget.data.dto.BalanceCheckDto
import ru.nomadbudget.data.dto.BalanceCheckInsertDto
import ru.nomadbudget.data.dto.CurrencyDto
import ru.nomadbudget.data.dto.ExchangeRateDto
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.BalanceCheck
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import kotlin.time.Instant

object RateMapper {

    fun toDomain(dto: ExchangeRateDto, currencies: Map<String, Currency>): ExchangeRate? {
        val quote = currencies[dto.quote] ?: return null
        return ExchangeRate(
            quote = quote,
            basePerUnit = dto.rate,
            date = LocalDate.parse(dto.rateDate),
            source = RateSource.API,
            sourceName = dto.source,
            fetchedAt = dto.fetchedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
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

object BalanceCheckMapper {

    fun toDomain(dto: BalanceCheckDto, accountsById: Map<String, Account>): BalanceCheck? {
        val account = accountsById[dto.accountId] ?: return null
        return BalanceCheck(
            id = dto.id,
            accountId = dto.accountId,
            date = LocalDate.parse(dto.checkDate),
            actual = Money(dto.actualBalance, account.currency),
            computed = Money(dto.computedBalance, account.currency),
            note = dto.note.orEmpty(),
        )
    }

    fun toInsert(check: BalanceCheck): BalanceCheckInsertDto = BalanceCheckInsertDto(
        accountId = check.accountId,
        checkDate = check.date.toString(),
        actualBalance = check.actual.minor,
        computedBalance = check.computed.minor,
        note = check.note.ifBlank { null },
    )
}
