package ru.nomadbudget.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.nomadbudget.App
import ru.nomadbudget.data.local.AppContextHolder
import ru.nomadbudget.demo.DemoMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppContextHolder.init(applicationContext)
        DemoMode.enabled = intent?.getBooleanExtra(EXTRA_DEMO, false) == true
        setContent {
            App()
        }
    }

    private companion object {
        const val EXTRA_DEMO = "demo"
    }
}
