package ru.nomadbudget.data.mapper

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import ru.nomadbudget.data.dto.TransactionInsertDto

class TransactionMapperDebtTest {

    private val card = Account("card", "Карта", Currency.RUB, AccountKind.CARD, false, Money.rub(0L))
    private val accounts = mapOf(card.id to card)

    private fun payment(principal: Money?, early: Boolean) = Transaction.Expense(
        id = "t1", date = LocalDate(2026, 10, 6), accountId = "card", amount = Money.rub(5_000_000L), categoryId = "loans",
        subcategoryId = null, amountBase = Money.rub(5_000_000L), rateSource = RateSource.API,
        debtId = "d1", debtPrincipal = principal, debtEarly = early,
    )

    @Test
    fun debtFields_roundTrip() {
        val insert = TransactionMapper.toInsert(payment(Money.rub(5_000_000L), early = true))
        assertEquals(5_000_000L, insert.debtPrincipal)
        assertEquals(true, insert.debtEarly)
        val back = TransactionMapper.toDomain(TransactionMapper.toDto("t1", insert), accounts) as Transaction.Expense
        assertEquals(Money.rub(5_000_000L), back.debtPrincipal)
        assertEquals(true, back.debtEarly)
    }

    @Test
    fun withoutPrincipal_staysNull() {
        val insert = TransactionMapper.toInsert(payment(null, early = false))
        assertNull(insert.debtPrincipal)
        val back = TransactionMapper.toDomain(TransactionMapper.toDto("t1", insert), accounts) as Transaction.Expense
        assertNull(back.debtPrincipal)
        assertEquals(false, back.debtEarly)
    }

    @Test
    fun insertDto_alwaysSendsDebtFields_soUpdateCanClearThem() {
        val json = Json { encodeDefaults = false }.encodeToString(TransactionInsertDto.serializer(), TransactionMapper.toInsert(payment(null, early = false)))
        assertTrue("\"debt_principal\":null" in json, json)
        assertTrue("\"debt_early\":false" in json, json)
    }
}
