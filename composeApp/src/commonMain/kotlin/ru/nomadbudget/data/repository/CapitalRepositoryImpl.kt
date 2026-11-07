package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import ru.nomadbudget.data.dto.CapitalGoalDto
import ru.nomadbudget.data.dto.CapitalGoalInsertDto
import ru.nomadbudget.data.dto.CapitalSettingsDto
import ru.nomadbudget.data.local.OfflineCache
import ru.nomadbudget.domain.logic.CapitalGoal
import ru.nomadbudget.domain.logic.CapitalSettings
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.repository.CapitalRepository

class CapitalRepositoryImpl(private val client: SupabaseClient, private val cache: OfflineCache) : CapitalRepository {

    override suspend fun getGoals(): List<CapitalGoal> =
        cache.throughOrDefault("capital_goals", ListSerializer(CapitalGoalDto.serializer()), emptyList()) {
            client.from(GOALS).select { order("target_date", Order.ASCENDING) }.decodeList<CapitalGoalDto>()
        }.map(::toDomain)

    override suspend fun addGoal(name: String, amount: Money, targetDate: LocalDate): CapitalGoal =
        client.from(GOALS)
            .insert(CapitalGoalInsertDto(name, amount.minor, targetDate.toString())) { select() }
            .decodeSingle<CapitalGoalDto>()
            .let(::toDomain)

    override suspend fun deleteGoal(id: String) {
        client.from(GOALS).delete { filter { eq("id", id) } }
    }

    override suspend fun getSettings(): CapitalSettings =
        cache.throughOrDefault("capital_settings", ListSerializer(CapitalSettingsDto.serializer()), emptyList()) {
            client.from(SETTINGS).select().decodeList<CapitalSettingsDto>()
        }.firstOrNull()?.let { CapitalSettings(it.cushionMonths, it.annualReturn) } ?: CapitalSettings()

    override suspend fun saveSettings(settings: CapitalSettings) {
        client.from(SETTINGS).upsert(CapitalSettingsDto(settings.cushionMonths, settings.annualReturn)) { onConflict = "user_id" }
    }

    private fun toDomain(dto: CapitalGoalDto) = CapitalGoal(dto.id, dto.name, Money.rub(dto.amountBase), LocalDate.parse(dto.targetDate))

    private companion object {
        const val GOALS = "capital_goals"
        const val SETTINGS = "capital_settings"
    }
}
