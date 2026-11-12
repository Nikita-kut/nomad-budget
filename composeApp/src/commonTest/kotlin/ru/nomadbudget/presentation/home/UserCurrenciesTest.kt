package ru.nomadbudget.presentation.home

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.TEST_START_DAY
import ru.nomadbudget.domain.model.UserSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class UserCurrenciesTest {

    private val today = LocalDate(2026, 11, 12)
    private val xts = Currency("XTS", 2, "¤")
    private val eur = Currency("EUR", 2, "€")

    private fun account(id: String, currency: Currency, archived: Boolean = false) =
        Account(id, id, currency, AccountKind.CARD, isSavings = false, openingBalance = Money.zero(currency), isArchived = archived)

    private fun state(codes: List<String>, accounts: List<Account>) = HomeState(
        today = today,
        period = SalaryCycle.periodContaining(today, TEST_START_DAY),
        currencies = listOf(Currency.BASE, Currency.USD, xts, eur),
        accounts = accounts,
        settings = UserSettings(currencyCodes = codes),
    )

    @Test
    fun listIsBasePlusOwnListPlusActiveAccountCurrencies() {
        val s = state(codes = listOf("EUR"), accounts = listOf(account("a", Currency.USD)))
        assertEquals(listOf("RUB", "EUR", "USD"), s.userCurrencies.map { it.code })
    }

    @Test
    fun othersCurrenciesAndArchivedOnlyCurrenciesAreHidden() {
        val s = state(codes = emptyList(), accounts = listOf(account("old", xts, archived = true)))
        assertEquals(listOf("RUB"), s.userCurrencies.map { it.code })
        assertEquals(emptyList(), s.foreignCurrencies)
    }
}
