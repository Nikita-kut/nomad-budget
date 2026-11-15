package ru.nomadbudget.presentation.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String?): Version? {
            val parts = raw?.trim()?.removePrefix("v")?.split('.') ?: return null
            if (parts.size != 3) return null
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            return Version(numbers[0], numbers[1], numbers[2])
        }
    }
}

data class VersionInfo(val min: Version?, val latest: Version?, val apkUrl: String?)

sealed interface UpdateState {
    data object Fine : UpdateState
    data class Available(val latest: Version) : UpdateState
    data class Required(val min: Version) : UpdateState
}

fun updateState(current: Version?, info: VersionInfo?): UpdateState {
    if (current == null || info == null || current == Version(0, 0, 0)) return UpdateState.Fine
    info.min?.let { if (current < it) return UpdateState.Required(it) }
    info.latest?.let { if (current < it) return UpdateState.Available(it) }
    return UpdateState.Fine
}

interface AppVersionRepository {
    suspend fun load(): VersionInfo?
}

class AppVersionRepositoryImpl(private val client: SupabaseClient) : AppVersionRepository {
    override suspend fun load(): VersionInfo? = runCatching {
        client.from("app_config").select(Columns.list("min_version", "latest_version", "apk_url")).decodeList<ConfigRow>().firstOrNull()
            ?.let { VersionInfo(Version.parse(it.min), Version.parse(it.latest), it.apkUrl) }
    }.getOrNull()

    @Serializable
    private data class ConfigRow(
        @SerialName("min_version") val min: String,
        @SerialName("latest_version") val latest: String,
        @SerialName("apk_url") val apkUrl: String? = null,
    )
}

object DemoAppVersionRepository : AppVersionRepository {
    override suspend fun load(): VersionInfo? = null
}

object UpdateNotice {
    var state: UpdateState by androidx.compose.runtime.mutableStateOf(UpdateState.Fine)
    var apkUrl: String? by androidx.compose.runtime.mutableStateOf(null)
}
