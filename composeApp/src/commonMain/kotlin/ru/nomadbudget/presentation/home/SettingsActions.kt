package ru.nomadbudget.presentation.home

import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.logic.CapitalGoal
import ru.nomadbudget.domain.logic.CapitalSettings
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.IsoCurrencies
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.UserSettings
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.CapitalRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.CurrencyRepository
import ru.nomadbudget.domain.repository.KeyValueStore
import ru.nomadbudget.domain.repository.UserSettingsRepository
import ru.nomadbudget.presentation.onboarding.OnboardingDefaults
import ru.nomadbudget.presentation.onboarding.OnboardingResult

internal class SettingsActions(
    private val store: HomeStore,
    private val runner: ActionRunner,
    private val auth: AuthRepository,
    private val localStore: KeyValueStore,
    private val userSettingsRepository: UserSettingsRepository,
    private val capitalRepository: CapitalRepository,
    private val currencyRepository: CurrencyRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val loader: LoadCoordinator,
    private val today: LocalDate,
) {

    fun addCapitalGoal(name: String, amount: Money, targetDate: LocalDate) = runner.run("Не удалось добавить цель") {
        require(name.isNotBlank()) { "Введи название цели" }
        require(amount.minor > 0L) { "Сумма цели должна быть больше нуля" }
        require(targetDate > today) { "Дата цели должна быть в будущем" }
        val goal = capitalRepository.addGoal(name.trim(), amount, targetDate)
        store.update { it.copy(capitalGoals = (it.capitalGoals + goal).sortedBy { g -> g.targetDate }) }
        store.message("Цель добавлена")
    }

    fun updateCapitalGoal(goal: CapitalGoal) = runner.run("Не удалось сохранить цель") {
        require(goal.name.isNotBlank()) { "Введи название цели" }
        require(goal.amount.minor > 0L) { "Сумма цели должна быть больше нуля" }
        require(goal.targetDate > today) { "Дата цели должна быть в будущем" }
        val cleaned = goal.copy(name = goal.name.trim())
        capitalRepository.updateGoal(cleaned)
        store.update { state -> state.copy(capitalGoals = state.capitalGoals.map { if (it.id == goal.id) cleaned else it }.sortedBy { it.targetDate }) }
        store.message("Цель сохранена")
    }

    fun deleteCapitalGoal(id: String) = runner.run("Не удалось удалить цель") {
        capitalRepository.deleteGoal(id)
        store.update { state -> state.copy(capitalGoals = state.capitalGoals.filterNot { it.id == id }) }
        store.message("Цель удалена")
    }

    fun saveCapitalSettings(settings: CapitalSettings) = runner.run("Не удалось сохранить настройки") {
        require(settings.cushionMonths in CapitalSettings.CUSHION_MONTHS_RANGE) { "Подушка: от 1 до 36 месяцев" }
        require(settings.annualReturn in 0.0..CapitalSettings.MAX_ANNUAL_RETURN) { "Доходность от 0 до 100 %" }
        saveSettings(store.value.settings.withCapital(settings))
        store.message("Настройки капитала сохранены")
    }

    fun setPeriodStartDay(day: Int) = runner.run("Не удалось сохранить начало месяца") {
        require(day in SalaryCycle.startDays) { "Начало месяца: день от 1 до 28" }
        val current = store.value
        if (day == current.periodStartDay) return@run
        saveSettings(current.settings.copy(periodStartDay = day))
        loader.switchPeriod(SalaryCycle.periodContaining(today, day))
        store.message("Месяц бюджета теперь начинается $day числа")
    }

    fun setIncomeDays(days: List<Int>) = runner.run("Не удалось сохранить дни дохода") {
        val cleaned = cleanIncomeDays(days)
        saveSettings(store.value.settings.copy(incomeDays = cleaned))
        store.message(if (cleaned.isEmpty()) "Дни дохода не заданы" else "Дни дохода сохранены")
    }

    fun addCurrency(code: String) = runner.run("Не удалось добавить валюту") {
        val normalized = code.trim().uppercase()
        require(IsoCurrencies.find(normalized) != null) { "Неизвестный код валюты" }
        require(store.value.userCurrencies.none { it.code == normalized }) { "Валюта $normalized уже добавлена" }
        val currency = currencyRepository.ensure(normalized)
        store.update { it.copy(currencies = (it.currencies + currency).distinctBy { c -> c.code }.sortedBy { c -> c.code }) }
        val current = store.value.settings
        saveSettings(current.copy(currencyCodes = (current.currencyCodes + normalized).distinct().sorted()))
        store.message("Валюта $normalized добавлена, курс появится в течение суток")
    }

    fun removeCurrency(code: String) = runner.run("Не удалось убрать валюту") {
        val state = store.value
        require(code != state.base.code) { "Базовую валюту убрать нельзя" }
        val blocking = state.activeAccounts.filter { it.currency.code == code }
        require(blocking.isEmpty()) { "Валюта $code нужна счетам: ${blocking.joinToString { it.name }}. Сначала убери их в архив" }
        saveSettings(state.settings.copy(currencyCodes = state.settings.currencyCodes - code))
        store.message("Валюта $code убрана из списка")
    }

    fun changePassword(newPassword: String) = runner.run("Не удалось изменить пароль") {
        require(newPassword.length >= HomeDefaults.MIN_PASSWORD_LENGTH) { "Пароль не короче ${HomeDefaults.MIN_PASSWORD_LENGTH} символов" }
        auth.changePassword(newPassword)
        store.message("Пароль изменён")
    }

    fun deleteAccount() = runner.run("Не удалось удалить аккаунт", busy = true) {
        localStore.clear()
        auth.deleteAccount()
    }

    fun signOut() = runner.run("Не удалось выйти") {
        localStore.clear()
        auth.signOut()
    }

    fun completeOnboarding(result: OnboardingResult) = runner.run("Не удалось завершить настройку", busy = true) {
        val currencies = currencyRepository.getAll().toMutableList()
        (listOf(result.baseCode) + result.currencyCodes).map { it.trim().uppercase() }.distinct().forEach { code ->
            if (currencies.none { it.code == code }) currencies += currencyRepository.ensure(code)
        }
        val byCode = currencies.associateBy { it.code }
        result.accounts.forEachIndexed { index, account ->
            val currency = requireNotNull(byCode[account.currencyCode]) { "Нет валюты ${account.currencyCode}" }
            val isSavings = account.role == AccountRole.CUSHION
            val kind = when (account.role) {
                AccountRole.CUSHION -> AccountKind.SAVINGS
                AccountRole.FUND -> AccountKind.ACCOUNT
                AccountRole.LIFE -> if (account.name.contains(OnboardingDefaults.CASH, ignoreCase = true)) AccountKind.CASH else AccountKind.CARD
            }
            val opening = account.openingBalance?.let { Money(it.minor, currency) } ?: Money.zero(currency)
            accountRepository.add(account.name.trim(), currency, kind, isSavings, account.role == AccountRole.FUND, (index + 1) * HomeDefaults.SORT_STEP, opening)
        }
        val existing = store.value.categories
        CategoryKind.entries.forEach { kind ->
            result.categories.filter { it.checked && it.kind == kind }.forEachIndexed { index, category ->
                val name = category.name.trim()
                if (name.isEmpty() || existing.any { it.kind == kind && it.name.equals(name, ignoreCase = true) }) return@forEachIndexed
                val created = categoryRepository.addCategory(name, kind, (index + 1) * HomeDefaults.SORT_STEP)
                category.subcategories.forEach { categoryRepository.addSubcategory(created.id, it) }
            }
        }
        val settings = store.value.settings.copy(
            periodStartDay = result.periodStartDay,
            baseCurrencyCode = result.baseCode,
            incomeDays = cleanIncomeDays(result.incomeDays),
            onboardedAt = Clock.System.now(),
        )
        val saved = userSettingsRepository.save(settings)
        store.update { it.copy(settings = saved, period = SalaryCycle.periodContaining(today, saved.periodStartDay)) }
        loader.load()
    }

    private suspend fun saveSettings(settings: UserSettings) {
        val saved = userSettingsRepository.save(settings)
        store.update { it.copy(settings = saved) }
    }

    private fun cleanIncomeDays(days: List<Int>): List<Int> = days.filter { it in HomeDefaults.DAYS_OF_MONTH }.distinct().sorted()
}
