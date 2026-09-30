package ru.nomadbudget.data.mapper

import ru.nomadbudget.data.dto.DebtDto
import ru.nomadbudget.data.dto.DebtInsertDto
import ru.nomadbudget.domain.model.Currency
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
        )
    }

    fun toInsert(debt: Debt): DebtInsertDto = DebtInsertDto(
        name = debt.name,
        currency = debt.currency.code,
        principalRemaining = debt.principalRemaining.minor,
        monthlyPayment = debt.monthlyPayment.minor,
        ratePercent = debt.ratePercent,
        payDay = debt.payDay,
    )
}
