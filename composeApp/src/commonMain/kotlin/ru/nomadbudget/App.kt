package ru.nomadbudget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.KoinApplication
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import ru.nomadbudget.demo.DemoMode
import ru.nomadbudget.demo.demoModule
import ru.nomadbudget.di.DEVICE_STORE
import ru.nomadbudget.di.appModule
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.AuthState
import ru.nomadbudget.domain.repository.KeyValueStore
import ru.nomadbudget.presentation.auth.LoginScreen
import ru.nomadbudget.presentation.format.AmountPrivacy
import ru.nomadbudget.presentation.format.LocalAmountPrivacy
import ru.nomadbudget.presentation.home.HomeScreen
import ru.nomadbudget.presentation.theme.AppTheme
import ru.nomadbudget.presentation.theme.LocalThemeSettings
import ru.nomadbudget.presentation.theme.ThemeSettings

@Composable
fun App() {
    KoinApplication(application = { modules(if (DemoMode.enabled) demoModule else appModule) }) {
        val koin = getKoin()
        val store = remember { koin.getOrNull<KeyValueStore>(DEVICE_STORE) ?: koin.get<KeyValueStore>() }
        val settings = remember { ThemeSettings(store) }
        val privacy = remember { AmountPrivacy(store) }
        CompositionLocalProvider(LocalThemeSettings provides settings, LocalAmountPrivacy provides privacy) {
            AppTheme(settings) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root() {
    val auth = koinInject<AuthRepository>()
    val state by auth.state.collectAsState(initial = AuthState.Loading)
    when (val current = state) {
        AuthState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is AuthState.SignedOut -> LoginScreen(auth, notice = if (current.sessionExpired) SESSION_EXPIRED else null)
        is AuthState.SignedIn -> HomeScreen()
    }
}

private const val SESSION_EXPIRED = "Сессия истекла, войди снова"
