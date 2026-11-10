package ru.nomadbudget.data.mapper

import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import ru.nomadbudget.data.dto.BankRateSnapshotDto
import ru.nomadbudget.data.dto.BankRateWatchDto
import ru.nomadbudget.data.dto.BankRateWatchUpsertDto
import ru.nomadbudget.data.dto.TelegramSubscriberDto
import ru.nomadbudget.domain.model.BankRateCategory
import ru.nomadbudget.domain.model.BankRateSnapshot
import ru.nomadbudget.domain.model.BankRateWatch
import ru.nomadbudget.domain.model.BankRateWatchDraft
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.TelegramSubscriber
import kotlin.time.Instant

object BankRateWatchMapper {

    fun toDomain(dto: BankRateWatchDto, currencies: Map<String, Currency>): BankRateWatch? {
        val from = currencies[dto.fromCurrency] ?: return null
        val to = currencies[dto.toCurrency] ?: return null
        val state = dto.state
        return BankRateWatch(
            id = dto.id,
            provider = dto.provider,
            from = from,
            to = to,
            category = BankRateCategory.fromId(dto.category),
            target = dto.target,
            goodPercentile = dto.goodPercentile,
            improvePercent = dto.improvePercent,
            tradeFrom = dto.tradeFrom,
            tradeTo = dto.tradeTo,
            digestHour = dto.digestHour,
            quietFrom = dto.quietFrom,
            quietTo = dto.quietTo,
            timezone = dto.timezone,
            enabled = dto.enabled,
            linkCode = dto.linkCode,
            lastRate = state?.get(STATE_LAST)?.jsonPrimitive?.doubleOrNull,
            lastCheckedAt = state?.get(STATE_LAST_AT)?.jsonPrimitive?.content?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
    }

    fun toUpsert(draft: BankRateWatchDraft): BankRateWatchUpsertDto = BankRateWatchUpsertDto(
        provider = draft.provider,
        fromCurrency = draft.from.code,
        toCurrency = draft.to.code,
        category = draft.category.id,
        target = draft.target,
        goodPercentile = draft.goodPercentile,
        improvePercent = draft.improvePercent,
        tradeFrom = draft.tradeFrom,
        tradeTo = draft.tradeTo,
        digestHour = draft.digestHour,
        quietFrom = draft.quietFrom,
        quietTo = draft.quietTo,
        timezone = draft.timezone,
    )

    fun toDomain(dto: BankRateSnapshotDto): BankRateSnapshot? {
        val takenAt = runCatching { Instant.parse(dto.takenAt) }.getOrNull() ?: return null
        if (dto.buy <= 0.0) return null
        return BankRateSnapshot(BankRateCategory.fromId(dto.category), dto.buy, dto.sell, dto.cbr, takenAt, dto.inHours)
    }

    fun toDomain(dto: TelegramSubscriberDto): TelegramSubscriber = TelegramSubscriber(dto.id, dto.title?.takeIf { it.isNotBlank() } ?: "Подписчик")

    private const val STATE_LAST = "last"
    private const val STATE_LAST_AT = "last_at"
}
