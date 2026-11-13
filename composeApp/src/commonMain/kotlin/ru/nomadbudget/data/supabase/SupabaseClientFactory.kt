package ru.nomadbudget.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import ru.nomadbudget.core.config.AppConfig

object SupabaseClientFactory {

    fun create(): SupabaseClient = createSupabaseClient(
        supabaseUrl = AppConfig.SUPABASE_URL,
        supabaseKey = AppConfig.SUPABASE_KEY,
    ) {
        install(Auth) {
            if (!AppConfig.IS_PROD) {
                sessionManager = SettingsSessionManager(key = AppConfig.STORAGE_PREFIX + "session")
            }
        }
        install(Postgrest)
    }
}
