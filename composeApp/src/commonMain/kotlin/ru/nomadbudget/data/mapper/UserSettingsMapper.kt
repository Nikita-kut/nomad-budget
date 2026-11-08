package ru.nomadbudget.data.mapper

import ru.nomadbudget.data.dto.UserSettingsDto
import ru.nomadbudget.domain.model.UserSettings
import kotlin.time.Instant

object UserSettingsMapper {

    fun toDomain(dto: UserSettingsDto): UserSettings = UserSettings(
        periodStartDay = dto.periodStartDay,
        incomeDays = dto.incomeDays.sorted(),
        baseCurrencyCode = dto.baseCurrency,
        cushionMonths = dto.cushionMonths,
        annualReturn = dto.annualReturn,
        onboardedAt = dto.onboardedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    )

    fun toDto(settings: UserSettings): UserSettingsDto = UserSettingsDto(
        periodStartDay = settings.periodStartDay,
        incomeDays = settings.incomeDays.sorted(),
        baseCurrency = settings.baseCurrencyCode,
        cushionMonths = settings.cushionMonths,
        annualReturn = settings.annualReturn,
        onboardedAt = settings.onboardedAt?.toString(),
    )
}
