package ru.nomadbudget.data.mapper

import ru.nomadbudget.data.dto.AccountDto
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money

object AccountMapper {

    fun toDomain(dto: AccountDto): Account {
        val currency = Currency.fromCode(dto.currency)
        return Account(
            id = dto.id,
            name = dto.name,
            currency = currency,
            kind = AccountKind.valueOf(dto.kind.uppercase()),
            isSavings = dto.isSavings,
            openingBalance = Money(dto.openingBalance, currency),
            sortOrder = dto.sortOrder,
        )
    }
}
