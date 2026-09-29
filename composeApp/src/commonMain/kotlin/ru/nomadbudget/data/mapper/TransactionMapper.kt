package ru.nomadbudget.data.mapper

import kotlinx.datetime.LocalDate
import ru.nomadbudget.data.dto.TransactionDto
import ru.nomadbudget.data.dto.TransactionInsertDto
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.Transaction

object TransactionMapper {

    private const val TYPE_EXPENSE = "expense"
    private const val TYPE_INCOME = "income"
    private const val TYPE_TRANSFER = "transfer"
    private const val TYPE_EXCHANGE = "exchange"

    fun toDomain(dto: TransactionDto, accountsById: Map<String, Account>): Transaction {
        val account = requireNotNull(accountsById[dto.accountId]) { "Неизвестный счёт ${dto.accountId}" }
        val amount = Money(dto.amount, account.currency)
        val amountBase = Money.rub(dto.amountBase)
        val date = LocalDate.parse(dto.txDate)
        val note = dto.note.orEmpty()
        return when (dto.type) {
            TYPE_EXPENSE -> Transaction.Expense(
                id = dto.id,
                date = date,
                accountId = dto.accountId,
                amount = amount,
                categoryId = requireNotNull(dto.categoryId) { "У расхода нет категории" },
                subcategoryId = dto.subcategoryId,
                amountBase = amountBase,
                rateSource = RateSource.valueOf(dto.rateSource.uppercase()),
                note = note,
            )
            TYPE_INCOME -> Transaction.Income(
                id = dto.id,
                date = date,
                accountId = dto.accountId,
                amount = amount,
                categoryId = requireNotNull(dto.categoryId) { "У дохода нет категории" },
                amountBase = amountBase,
                rateSource = RateSource.valueOf(dto.rateSource.uppercase()),
                note = note,
            )
            TYPE_TRANSFER -> Transaction.Transfer(
                id = dto.id,
                date = date,
                fromAccountId = dto.accountId,
                toAccountId = requireNotNull(dto.counterAccountId) { "У перевода нет второго счёта" },
                amount = amount,
                amountBase = amountBase,
                note = note,
            )
            TYPE_EXCHANGE -> {
                val toId = requireNotNull(dto.counterAccountId) { "У обмена нет второго счёта" }
                val toAccount = requireNotNull(accountsById[toId]) { "Неизвестный счёт $toId" }
                Transaction.Exchange(
                    id = dto.id,
                    date = date,
                    fromAccountId = dto.accountId,
                    toAccountId = toId,
                    given = amount,
                    received = Money(requireNotNull(dto.counterAmount) { "У обмена нет полученной суммы" }, toAccount.currency),
                    amountBase = amountBase,
                    note = note,
                )
            }
            else -> error("Неизвестный тип операции ${dto.type}")
        }
    }

    fun toInsert(tx: Transaction): TransactionInsertDto = when (tx) {
        is Transaction.Expense -> TransactionInsertDto(
            txDate = tx.date.toString(),
            type = TYPE_EXPENSE,
            accountId = tx.accountId,
            amount = tx.amount.minor,
            categoryId = tx.categoryId,
            subcategoryId = tx.subcategoryId,
            note = tx.note.ifBlank { null },
            rateToBase = rateOf(tx.amount, tx.amountBase),
            amountBase = tx.amountBase.minor,
            rateSource = tx.rateSource.name.lowercase(),
        )
        is Transaction.Income -> TransactionInsertDto(
            txDate = tx.date.toString(),
            type = TYPE_INCOME,
            accountId = tx.accountId,
            amount = tx.amount.minor,
            categoryId = tx.categoryId,
            note = tx.note.ifBlank { null },
            rateToBase = rateOf(tx.amount, tx.amountBase),
            amountBase = tx.amountBase.minor,
            rateSource = tx.rateSource.name.lowercase(),
        )
        is Transaction.Transfer -> TransactionInsertDto(
            txDate = tx.date.toString(),
            type = TYPE_TRANSFER,
            accountId = tx.fromAccountId,
            counterAccountId = tx.toAccountId,
            amount = tx.amount.minor,
            note = tx.note.ifBlank { null },
            rateToBase = rateOf(tx.amount, tx.amountBase),
            amountBase = tx.amountBase.minor,
            rateSource = RateSource.API.name.lowercase(),
        )
        is Transaction.Exchange -> TransactionInsertDto(
            txDate = tx.date.toString(),
            type = TYPE_EXCHANGE,
            accountId = tx.fromAccountId,
            counterAccountId = tx.toAccountId,
            amount = tx.given.minor,
            counterAmount = tx.received.minor,
            note = tx.note.ifBlank { null },
            rateToBase = rateOf(tx.given, tx.amountBase),
            amountBase = tx.amountBase.minor,
            rateSource = RateSource.API.name.lowercase(),
        )
    }

    private fun rateOf(amount: Money, amountBase: Money): Double = amountBase.toMajor() / amount.toMajor()
}
