package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlin.time.Instant
import kotlinx.serialization.builtins.ListSerializer
import ru.nomadbudget.data.dto.BaseChangeDto
import ru.nomadbudget.data.dto.ChangeBaseParams
import ru.nomadbudget.data.dto.UserSettingsDto
import ru.nomadbudget.data.local.OfflineCache
import ru.nomadbudget.data.mapper.UserSettingsMapper
import ru.nomadbudget.domain.model.BaseChange
import ru.nomadbudget.domain.model.UserSettings
import ru.nomadbudget.domain.repository.UserSettingsRepository

class UserSettingsRepositoryImpl(private val client: SupabaseClient, private val cache: OfflineCache) : UserSettingsRepository {

    private val serializer = ListSerializer(UserSettingsDto.serializer())

    override fun cached(): UserSettings? = cache.peek(KEY, serializer)?.firstOrNull()?.let(UserSettingsMapper::toDomain)

    override suspend fun get(): UserSettings = cache.through(KEY, serializer) {
        client.from(TABLE).select().decodeList<UserSettingsDto>()
    }.firstOrNull()?.let(UserSettingsMapper::toDomain) ?: UserSettings()

    override suspend fun save(settings: UserSettings): UserSettings {
        val dto = UserSettingsMapper.toDto(settings)
        val saved = client.from(TABLE).upsert(dto) {
            onConflict = "user_id"
            select()
        }.decodeSingle<UserSettingsDto>()
        cache.put(KEY, serializer, listOf(saved))
        return UserSettingsMapper.toDomain(saved)
    }

    override suspend fun lastBaseChange(): BaseChange? = client.from(CHANGES).select(Columns.list("from_currency", "to_currency", "changed_at", "reverted_at")) {
        order("changed_at", Order.DESCENDING)
        limit(1)
    }.decodeList<BaseChangeDto>().firstOrNull()?.let {
        BaseChange(it.fromCurrency, it.toCurrency, Instant.parse(it.changedAt), it.revertedAt != null)
    }

    override suspend fun changeBase(code: String) {
        client.postgrest.rpc(CHANGE_BASE, ChangeBaseParams(code))
    }

    override suspend fun revertBase() {
        client.postgrest.rpc(REVERT_BASE)
    }

    private companion object {
        const val CHANGES = "base_currency_changes"
        const val CHANGE_BASE = "change_base_currency"
        const val REVERT_BASE = "revert_base_currency"
        const val TABLE = "user_settings"
        const val KEY = "user_settings"
    }
}
