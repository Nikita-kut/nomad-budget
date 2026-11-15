package ru.nomadbudget.features

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nomadbudget.domain.repository.KeyValueStore

data class FeatureAccess(val stages: Map<String, Stage>, val isAdmin: Boolean)

interface FeatureRepository {
    fun cached(): FeatureAccess?
    suspend fun load(): FeatureAccess
}

class FeatureRepositoryImpl(private val client: SupabaseClient, private val store: KeyValueStore) : FeatureRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun cached(): FeatureAccess? =
        store.get(KEY)?.let { runCatching { json.decodeFromString<CachedAccess>(it) }.getOrNull() }?.toAccess()

    override suspend fun load(): FeatureAccess {
        val rows = client.from(TABLE).select(Columns.list("key", "stage")).decodeList<StageRow>()
        val admin = runCatching { client.postgrest.rpc(IS_ADMIN).decodeAs<Boolean>() }.getOrDefault(false)
        val cached = CachedAccess(rows.associate { it.key to it.stage }, admin)
        store.put(KEY, json.encodeToString(CachedAccess.serializer(), cached))
        return cached.toAccess()
    }

    @Serializable
    private data class StageRow(val key: String, val stage: String)

    @Serializable
    private data class CachedAccess(val stages: Map<String, String>, val admin: Boolean) {
        fun toAccess() = FeatureAccess(stages.mapNotNull { (k, v) -> Stage.of(v)?.let { k to it } }.toMap(), admin)
    }

    private companion object {
        const val TABLE = "feature_stages"
        const val IS_ADMIN = "is_admin"
        const val KEY = "feature_access"
    }
}

object DemoFeatureRepository : FeatureRepository {
    private val everything = FeatureAccess(Feature.entries.associate { it.key to Stage.ALL }, isAdmin = false)
    override fun cached() = everything
    override suspend fun load() = everything
}

object FeatureGate {
    var state: FeatureState by mutableStateOf(FeatureState.EVERYTHING)

    fun isOn(feature: Feature): Boolean = state.isOn(feature)

    fun isOn(module: AppModule): Boolean = state.isOn(module)
}
