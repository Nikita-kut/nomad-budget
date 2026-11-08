package ru.nomadbudget.domain.model

import ru.nomadbudget.domain.logic.CapitalSettings
import kotlin.time.Instant

data class UserSettings(
    val periodStartDay: Int = SalaryCycle.DEFAULT_START_DAY,
    val incomeDays: List<Int> = emptyList(),
    val baseCurrencyCode: String = Currency.BASE.code,
    val cushionMonths: Int = CapitalSettings.DEFAULT_CUSHION_MONTHS,
    val annualReturn: Double = 0.0,
    val onboardedAt: Instant? = null,
) {
    init {
        require(periodStartDay in SalaryCycle.startDays) { "Начало месяца: день от 1 до 28" }
        require(incomeDays.all { it in 1..31 }) { "День дохода: от 1 до 31" }
        require(cushionMonths in CapitalSettings.CUSHION_MONTHS_RANGE) { "Подушка: от 1 до 36 месяцев" }
        require(annualReturn in 0.0..CapitalSettings.MAX_ANNUAL_RETURN) { "Доходность от 0 до 100 %" }
    }

    val capital: CapitalSettings get() = CapitalSettings(cushionMonths, annualReturn)

    val isOnboarded: Boolean get() = onboardedAt != null

    fun withCapital(settings: CapitalSettings): UserSettings =
        copy(cushionMonths = settings.cushionMonths, annualReturn = settings.annualReturn)
}
