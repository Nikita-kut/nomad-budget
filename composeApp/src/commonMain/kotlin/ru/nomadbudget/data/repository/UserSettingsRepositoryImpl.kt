package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.builtins.ListSerializer
import ru.nomadbudget.data.dto.UserSettingsDto
import ru.nomadbudget.data.local.OfflineCache
import ru.nomadbudget.data.mapper.UserSettingsMapper
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

    private companion object {
        const val TABLE = "user_settings"
        const val KEY = "user_settings"
    }
}
