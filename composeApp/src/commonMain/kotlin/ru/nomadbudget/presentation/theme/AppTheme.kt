package ru.nomadbudget.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import nomad_budget.composeapp.generated.resources.Res
import nomad_budget.composeapp.generated.resources.inter_regular
import nomad_budget.composeapp.generated.resources.inter_medium
import nomad_budget.composeapp.generated.resources.inter_semibold
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ru.nomadbudget.domain.logic.BudgetStatus
import ru.nomadbudget.domain.model.Currency
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.nomadbudget.data.local.KeyValueStore

data class AppColors(
    val base: Color,
    val usd: Color,
    val extraCurrencies: List<Color>,
    val good: Color,
    val warning: Color,
    val bad: Color,
    val muted: Color,
    val save: Color,
    val hero: List<Color>,
    val onHero: Color,
    val heroMuted: Color,
    val glow: List<Color>,
) {
    fun currency(currency: Currency, ordinal: Int = 0): Color = when (currency.code) {
        Currency.BASE.code -> base
        Currency.USD.code -> usd
        else -> extraCurrencies[abs(ordinal) % extraCurrencies.size]
    }

    fun status(status: BudgetStatus): Color = when (status) {
        BudgetStatus.OK -> good
        BudgetStatus.DONE -> good
        BudgetStatus.WARNING -> warning
        BudgetStatus.OVER_SLIGHTLY -> warning
        BudgetStatus.OVER -> bad
    }
}

private val LightAppColors = AppColors(
    base = Color(0xFF4673FF),
    usd = Color(0xFF12A36B),
    extraCurrencies = listOf(Color(0xFFF08A24), Color(0xFF9C6BFF), Color(0xFF0BA5B8), Color(0xFFE5487D)),
    good = Color(0xFF12A36B),
    warning = Color(0xFFE08A00),
    bad = Color(0xFFE5484D),
    muted = Color(0xFF9DADBE),
    save = Color(0xFF7B5CFF),
    hero = listOf(Color(0xFF4673FF), Color(0xFF7E6BFF), Color(0xFF2E5BE8)),
    onHero = Color(0xFFFFFFFF),
    heroMuted = Color(0xCCFFFFFF),
    glow = listOf(Color(0xFFF0E7FD), Color(0xFFCCE9FF)),
)

private val DarkAppColors = AppColors(
    base = Color(0xFF7D9BFF),
    usd = Color(0xFF3DD68C),
    extraCurrencies = listOf(Color(0xFFFFA94D), Color(0xFFB49AFF), Color(0xFF4FD1E0), Color(0xFFFF7AA2)),
    good = Color(0xFF3DD68C),
    warning = Color(0xFFFFB547),
    bad = Color(0xFFFF6B6B),
    muted = Color(0xFF687287),
    save = Color(0xFFB49AFF),
    hero = listOf(Color(0xFF3F67F0), Color(0xFF7A5CF5), Color(0xFF213A89)),
    onHero = Color(0xFFFFFFFF),
    heroMuted = Color(0xCCFFFFFF),
    glow = listOf(Color(0xFF2B2550), Color(0xFF1B3358)),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4673FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3EBFF),
    onPrimaryContainer = Color(0xFF16307F),
    background = Color(0xFFF0F4F9),
    onBackground = Color(0xFF131923),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF131923),
    surfaceVariant = Color(0xFFEAEFF6),
    onSurfaceVariant = Color(0xFF4E6174),
    outline = Color(0xFF9DADBE),
    outlineVariant = Color(0xFFDEE5F0),
    error = Color(0xFFE5484D),
    secondary = Color(0xFF7B5CFF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EBFF),
    onSecondaryContainer = Color(0xFF16307F),
    tertiary = Color(0xFF9C6BFF),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0E7FD),
    onTertiaryContainer = Color(0xFF3A1E80),
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF5F7FB),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF1D2028),
    inverseOnSurface = Color(0xFFF5F6F7),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF6E8FFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF213A89),
    onPrimaryContainer = Color(0xFFD6E0FF),
    background = Color(0xFF14161C),
    onBackground = Color(0xFFF5F6F7),
    surface = Color(0xFF1D2028),
    onSurface = Color(0xFFF5F6F7),
    surfaceVariant = Color(0xFF2C313C),
    onSurfaceVariant = Color(0xFF8D94A5),
    outline = Color(0xFF525A6D),
    outlineVariant = Color(0xFF353B48),
    error = Color(0xFFFF6B6B),
    secondary = Color(0xFFB49AFF),
    onSecondary = Color(0xFF1D1240),
    secondaryContainer = Color(0xFF263A7A),
    onSecondaryContainer = Color(0xFFD6E0FF),
    tertiary = Color(0xFFB49AFF),
    onTertiary = Color(0xFF1D1240),
    tertiaryContainer = Color(0xFF372B4D),
    onTertiaryContainer = Color(0xFFE6DCFF),
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFF3E4454),
    surfaceContainerLowest = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF181B22),
    surfaceContainer = Color(0xFF1D2028),
    surfaceContainerHigh = Color(0xFF262A34),
    surfaceContainerHighest = Color(0xFF1F232C),
    inverseSurface = Color(0xFFF5F6F7),
    inverseOnSurface = Color(0xFF1D2028),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
private fun appTypography(): Typography {
    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_semibold, FontWeight.Bold),
    )
    val plain = Typography()
    fun TextStyle.inter() = copy(fontFamily = inter)
    val base = Typography(
        displayLarge = plain.displayLarge.inter(), displayMedium = plain.displayMedium.inter(), displaySmall = plain.displaySmall.inter(),
        headlineLarge = plain.headlineLarge.inter(), headlineMedium = plain.headlineMedium.inter(), headlineSmall = plain.headlineSmall.inter(),
        titleLarge = plain.titleLarge.inter(), titleMedium = plain.titleMedium.inter(), titleSmall = plain.titleSmall.inter(),
        bodyLarge = plain.bodyLarge.inter(), bodyMedium = plain.bodyMedium.inter(), bodySmall = plain.bodySmall.inter(),
        labelLarge = plain.labelLarge.inter(), labelMedium = plain.labelMedium.inter(), labelSmall = plain.labelSmall.inter(),
    )
    fun TextStyle.tight(weight: FontWeight? = null, spacing: Double = -0.2) = copy(fontWeight = weight ?: fontWeight, letterSpacing = spacing.sp)
    return base.copy(
        displaySmall = base.displaySmall.tight(FontWeight.SemiBold, -0.8),
        headlineLarge = base.headlineLarge.tight(FontWeight.SemiBold, -0.6),
        headlineMedium = base.headlineMedium.tight(FontWeight.SemiBold, -0.5),
        headlineSmall = base.headlineSmall.tight(FontWeight.SemiBold, -0.4),
        titleLarge = base.titleLarge.tight(FontWeight.SemiBold, -0.3),
        titleMedium = base.titleMedium.tight(FontWeight.SemiBold, -0.1),
        titleSmall = base.titleSmall.tight(FontWeight.SemiBold, 0.0),
        labelLarge = base.labelLarge.tight(FontWeight.Medium, 0.0),
        labelMedium = base.labelMedium.tight(FontWeight.Medium, 0.1),
        labelSmall = base.labelSmall.tight(FontWeight.Medium, 0.2),
    )
}

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

class ThemeSettings(private val store: KeyValueStore?) {
    private var override by mutableStateOf(store?.get(KEY)?.let { it == DARK })

    fun isDark(system: Boolean): Boolean = override ?: system

    fun toggle(system: Boolean) {
        val next = !isDark(system)
        override = next
        store?.put(KEY, if (next) DARK else LIGHT)
    }

    companion object {
        const val KEY = "theme"
        private const val DARK = "dark"
        private const val LIGHT = "light"
    }
}

val LocalThemeSettings = staticCompositionLocalOf { ThemeSettings(null) }

@Composable
fun AppTheme(settings: ThemeSettings = LocalThemeSettings.current, content: @Composable () -> Unit) {
    val dark = settings.isDark(isSystemInDarkTheme())
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkAppColors else LightAppColors) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, shapes = AppShapes, typography = appTypography(), content = content)
    }
}
