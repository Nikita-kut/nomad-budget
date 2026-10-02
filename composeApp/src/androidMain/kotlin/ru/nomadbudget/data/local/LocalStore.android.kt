package ru.nomadbudget.data.local

import android.content.Context
import android.content.SharedPreferences

object AppContextHolder {
    lateinit var context: Context
        private set

    fun init(applicationContext: Context) {
        context = applicationContext
    }
}

actual class LocalStore actual constructor() {

    private val prefs: SharedPreferences by lazy {
        AppContextHolder.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun get(key: String): String? = prefs.getString(key, null)

    actual fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val PREFS_NAME = "nomad_budget_local"
    }
}
