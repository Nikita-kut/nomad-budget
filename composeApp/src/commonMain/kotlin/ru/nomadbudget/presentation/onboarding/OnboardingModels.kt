package ru.nomadbudget.presentation.onboarding

import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money

data class OnboardingAccount(
    val key: String,
    val name: String,
    val currencyCode: String,
    val role: AccountRole,
    val auto: Boolean = false,
    val balanceText: String = "",
)

data class OnboardingCategory(
    val name: String,
    val kind: CategoryKind,
    val checked: Boolean = true,
    val subcategories: List<String> = emptyList(),
)

data class OnboardingResult(
    val periodStartDay: Int,
    val incomeDays: List<Int>,
    val currencyCodes: List<String>,
    val accounts: List<OnboardingAccountResult>,
    val categories: List<OnboardingCategory>,
)

data class OnboardingAccountResult(
    val name: String,
    val currencyCode: String,
    val role: AccountRole,
    val openingBalance: Money?,
)

object OnboardingDefaults {

    const val CARD = "Карта"
    const val CASH = "Наличные"
    const val CUSHION = "Подушка"

    fun accounts(): List<OnboardingAccount> = listOf(
        OnboardingAccount("card", CARD, Currency.BASE.code, AccountRole.LIFE),
        OnboardingAccount("cash", CASH, Currency.BASE.code, AccountRole.LIFE),
        OnboardingAccount("cushion", CUSHION, Currency.BASE.code, AccountRole.CUSHION),
    )

    fun cashFor(code: String): OnboardingAccount = OnboardingAccount("cash:$code", "$CASH $code", code, AccountRole.LIFE, auto = true)

    fun categories(): List<OnboardingCategory> = listOf(
        OnboardingCategory("Еда", CategoryKind.EXPENSE, subcategories = listOf("продукты", "кафе", "доставка")),
        OnboardingCategory("Транспорт", CategoryKind.EXPENSE, subcategories = listOf("такси", "общественный транспорт", "топливо")),
        OnboardingCategory("Жильё", CategoryKind.EXPENSE, subcategories = listOf("аренда", "коммунальные услуги")),
        OnboardingCategory("Здоровье", CategoryKind.EXPENSE, subcategories = listOf("аптека", "врач", "спорт")),
        OnboardingCategory("Связь", CategoryKind.EXPENSE, subcategories = listOf("мобильная связь", "интернет", "подписки")),
        OnboardingCategory("Развлечения", CategoryKind.EXPENSE),
        OnboardingCategory("Путешествия", CategoryKind.EXPENSE),
        OnboardingCategory("Подарки", CategoryKind.EXPENSE),
        OnboardingCategory("Разное", CategoryKind.EXPENSE),
        OnboardingCategory("Кредиты", CategoryKind.EXPENSE),
        OnboardingCategory("Зарплата", CategoryKind.INCOME),
        OnboardingCategory("Прочий доход", CategoryKind.INCOME),
    )
}
