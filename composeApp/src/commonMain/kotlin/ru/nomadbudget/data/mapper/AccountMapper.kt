package ru.nomadbudget.data.mapper

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.nomadbudget.data.dto.AccountDto
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money

object AccountMapper {

    fun toDomain(dto: AccountDto, currencies: Map<String, Currency>): Account {
        val currency = requireNotNull(currencies[dto.currency]) { "Неизвестная валюта ${dto.currency}" }
        return Account(
            id = dto.id,
            name = dto.name,
            currency = currency,
            kind = AccountKind.valueOf(dto.kind.uppercase()),
            isSavings = dto.isSavings,
            openingBalance = Money(dto.openingBalance, currency),
            sortOrder = dto.sortOrder,
            isArchived = dto.archivedAt != null,
            archivedOn = dto.archivedAt?.let(::localDate),
            isFund = dto.isFund && !dto.isSavings,
            fundMonthly = dto.fundMonthly?.let { Money(it, currency) },
            fundGoal = dto.fundGoal?.let { Money(it, currency) },
            fundDay = dto.fundDay,
        )
    }
}

internal fun localDate(timestamp: String, zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    runCatching { Instant.parse(timestamp).toLocalDateTime(zone).date }.getOrElse { LocalDate.parse(timestamp.take(10)) }
