package ru.nomadbudget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import ru.nomadbudget.di.appModule
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.AuthState
import ru.nomadbudget.presentation.auth.LoginScreen
import ru.nomadbudget.presentation.home.HomeScreen
import ru.nomadbudget.presentation.theme.AppTheme

@Composable
fun App() {
    KoinApplication(application = { modules(appModule) }) {
        AppTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Root()
            }
        }
    }
}

@Composable
private fun Root() {
    val auth = koinInject<AuthRepository>()
    val state by auth.state.collectAsState(initial = AuthState.LOADING)
    when (state) {
        AuthState.LOADING -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        AuthState.SIGNED_OUT -> LoginScreen(auth)
        AuthState.SIGNED_IN -> HomeScreen()
    }
}
