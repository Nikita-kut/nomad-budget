package ru.nomadbudget.android

import android.app.Application
import ru.nomadbudget.data.local.AppContextHolder

class NomadBudgetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
    }
}
