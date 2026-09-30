package ru.nomadbudget.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ru.nomadbudget.domain.logic.BudgetStatus
import ru.nomadbudget.domain.model.Currency
import kotlin.math.abs

data class AppColors(
    val base: Color,
    val usd: Color,
    val extraCurrencies: List<Color>,
    val good: Color,
    val warning: Color,
    val bad: Color,
    val muted: Color,
) {
    fun currency(currency: Currency, ordinal: Int = 0): Color = when (currency.code) {
        Currency.BASE.code -> base
        Currency.USD.code -> usd
        else -> extraCurrencies[abs(ordinal) % extraCurrencies.size]
    }

    fun status(status: BudgetStatus): Color = when (status) {
        BudgetStatus.OK -> good
        BudgetStatus.WARNING -> warning
        BudgetStatus.OVER -> bad
    }
}

private val LightAppColors = AppColors(
    base = Color(0xFFB8443F),
    usd = Color(0xFF2F7D4F),
    extraCurrencies = listOf(Color(0xFFD97B1F), Color(0xFF8A4FA8), Color(0xFF1F7F8A), Color(0xFF4A5FA8)),
    good = Color(0xFF3A8F5C),
    warning = Color(0xFFC98A1C),
    bad = Color(0xFFC4463A),
    muted = Color(0xFF7C8683),
)

private val DarkAppColors = AppColors(
    base = Color(0xFFE57A72),
    usd = Color(0xFF6CBF8A),
    extraCurrencies = listOf(Color(0xFFF0A24A), Color(0xFFC29BE0), Color(0xFF6FC4CF), Color(0xFF8C9BD8)),
    good = Color(0xFF5DB57F),
    warning = Color(0xFFDDA64E),
    bad = Color(0xFFE0695D),
    muted = Color(0xFF7C8683),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F4E5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE9EC),
    onPrimaryContainer = Color(0xFF0F2A33),
    background = Color(0xFFF3F5F2),
    onBackground = Color(0xFF171C1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171C1B),
    surfaceVariant = Color(0xFFE9EDE8),
    onSurfaceVariant = Color(0xFF4E5856),
    outline = Color(0xFFB9C2BA),
    outlineVariant = Color(0xFFD6DCD6),
    error = Color(0xFFC4463A),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF6FB1C4),
    onPrimary = Color(0xFF0F1C21),
    primaryContainer = Color(0xFF1D3239),
    onPrimaryContainer = Color(0xFFCFE6EC),
    background = Color(0xFF131716),
    onBackground = Color(0xFFE6EAE7),
    surface = Color(0xFF1B201F),
    onSurface = Color(0xFFE6EAE7),
    surfaceVariant = Color(0xFF232928),
    onSurfaceVariant = Color(0xFFA9B3AF),
    outline = Color(0xFF3D4745),
    outlineVariant = Color(0xFF2D3533),
    error = Color(0xFFE0695D),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkAppColors else LightAppColors) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}
