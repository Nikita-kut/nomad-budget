package ru.nomadbudget.data.mapper

import ru.nomadbudget.data.dto.DebtDto
import ru.nomadbudget.data.dto.DebtInsertDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.DebtCloseReason
import kotlin.time.Instant
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Money

object DebtMapper {

    fun toDomain(dto: DebtDto, currencies: Map<String, Currency>): Debt? {
        val currency = currencies[dto.currency] ?: return null
        return Debt(
            id = dto.id,
            name = dto.name,
            currency = currency,
            principalRemaining = Money(dto.principalRemaining, currency),
            monthlyPayment = Money(dto.monthlyPayment, currency),
            ratePercent = dto.ratePercent,
            payDay = dto.payDay,
            isClosed = dto.closedAt != null,
            extraPayment = Money(dto.extraPayment, currency),
            openedOn = dto.openedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            closedOn = dto.closedAt?.let { runCatching { Instant.parse(it).toLocalDateTime(TimeZone.UTC).date }.getOrNull() },
            closeReason = DebtCloseReason.fromId(dto.closeReason) ?: if (dto.closedAt != null) DebtCloseReason.MANUAL else null,
            writtenOff = Money(dto.writtenOff, currency),
        )
    }

    fun toInsert(debt: Debt): DebtInsertDto = DebtInsertDto(
        name = debt.name,
        currency = debt.currency.code,
        principalRemaining = debt.principalRemaining.minor,
        monthlyPayment = debt.monthlyPayment.minor,
        ratePercent = debt.ratePercent,
        payDay = debt.payDay,
        extraPayment = debt.extraPayment.minor,
    )
}
