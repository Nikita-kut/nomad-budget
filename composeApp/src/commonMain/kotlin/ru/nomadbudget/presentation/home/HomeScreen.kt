package ru.nomadbudget.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.presentation.accounts.AccountsScreen
import ru.nomadbudget.presentation.charts.ChartsScreen
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.InfoHint
import ru.nomadbudget.presentation.components.currencyColor
import ru.nomadbudget.presentation.entry.EntryScreen
import ru.nomadbudget.presentation.exchange.ExchangeScreen
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.month.MonthScreen
import ru.nomadbudget.presentation.more.BalanceCheckScreen
import ru.nomadbudget.presentation.more.CategoriesScreen
import ru.nomadbudget.presentation.more.DebtsScreen
import ru.nomadbudget.presentation.more.MoreScreen
import ru.nomadbudget.presentation.rates.RatesScreen
import ru.nomadbudget.presentation.theme.AppTheme

@Serializable
object MonthRoute

@Serializable
object EntryRoute

@Serializable
object ExchangeRoute

@Serializable
object AccountsRoute

@Serializable
object RatesRoute

@Serializable
object ChartsRoute

@Serializable
object MoreRoute

@Serializable
object CategoriesRoute

@Serializable
object BalanceCheckRoute

@Serializable
object DebtsRoute

private data class Tab(
    val route: Any,
    val title: String,
    val icon: ImageVector,
    val primary: Boolean,
    val isSelected: (NavHostController) -> Boolean,
)

private val WIDE_LAYOUT_MIN_WIDTH = 840.dp
private val CONTENT_MAX_WIDTH = 760.dp

@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val navController = rememberNavController()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(viewModel) {
        viewModel.navigateToEntry.collect { navController.switchTo(EntryRoute) }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.reloadDrafts()
        onPauseOrDispose {}
    }

    val tabs = remember {
        listOf(
            Tab(MonthRoute, "Месяц", Icons.Filled.DateRange, primary = true) { it.isOn<MonthRoute>() },
            Tab(EntryRoute, "Ввод", Icons.Filled.AddCircle, primary = true) { it.isOn<EntryRoute>() },
            Tab(ExchangeRoute, "Обмен", Icons.AutoMirrored.Filled.Send, primary = true) { it.isOn<ExchangeRoute>() },
            Tab(AccountsRoute, "Счета", Icons.Filled.AccountBox, primary = true) { it.isOn<AccountsRoute>() },
            Tab(DebtsRoute, "Кредиты", Icons.Filled.Build, primary = true) { it.isOn<DebtsRoute>() },
            Tab(RatesRoute, "Курсы", Icons.Filled.Info, primary = false) { it.isOn<RatesRoute>() },
            Tab(ChartsRoute, "Графики", Icons.Filled.Star, primary = false) { it.isOn<ChartsRoute>() },
            Tab(MoreRoute, "Ещё", Icons.Filled.Settings, primary = true) {
                it.isOn<MoreRoute>() || it.isOn<CategoriesRoute>() || it.isOn<BalanceCheckRoute>()
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
        val backStack by navController.currentBackStackEntryAsState()

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { HeaderBar(state, viewModel, wide) },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        tabs.filter { it.primary }.forEach { tab ->
                            NavigationBarItem(
                                selected = backStack != null && (tab.isSelected(navController) ||
                                    tab.route == MoreRoute && (navController.isOn<RatesRoute>() || navController.isOn<ChartsRoute>())),
                                onClick = { navController.switchTo(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.title) },
                                label = { Text(tab.title) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            NavigationRailItem(
                                selected = backStack != null && tab.isSelected(navController),
                                onClick = { navController.switchTo(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.title) },
                                label = { Text(tab.title) },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize()) {
                        NavHost(navController = navController, startDestination = MonthRoute) {
                            composable<MonthRoute> {
                                MonthScreen(
                                    state = state,
                                    onSetPlanned = viewModel::setPlanned,
                                    onSetItemPlanned = viewModel::setPlannedForSubcategoryName,
                                    onRemoveItem = viewModel::removePlanLine,
                                    onCopyPlan = viewModel::copyPlanFromPreviousPeriod,
                                    onRetry = viewModel::load,
                                )
                            }
                            composable<EntryRoute> {
                                EntryScreen(
                                    state = state,
                                    onSubmit = viewModel::addEntry,
                                    onDelete = viewModel::deleteTransaction,
                                    onUpdate = viewModel::updateTransaction,
                                    onAddDraft = viewModel::addDraft,
                                    onUseDraft = viewModel::useDraft,
                                    onRemoveDraft = viewModel::removeDraft,
                                    onClearPrefill = viewModel::clearPrefill,
                                )
                            }
                            composable<ExchangeRoute> {
                                ExchangeScreen(state = state, onSubmit = viewModel::addExchange, onDelete = viewModel::deleteTransaction)
                            }
                            composable<AccountsRoute> {
                                AccountsScreen(
                                    state = state,
                                    onAdd = viewModel::addAccount,
                                    onRename = viewModel::renameAccount,
                                    onMove = viewModel::moveAccount,
                                    onArchive = viewModel::archiveAccount,
                                )
                            }
                            composable<RatesRoute> { RatesScreen(state = state) }
                            composable<ChartsRoute> { ChartsScreen(state = state) }
                            composable<MoreRoute> {
                                MoreScreen(
                                    state = state,
                                    onCategories = { navController.navigate(CategoriesRoute) },
                                    onBalanceCheck = { navController.navigate(BalanceCheckRoute) },
                                    onRates = { navController.navigate(RatesRoute) },
                                    onCharts = { navController.navigate(ChartsRoute) },
                                    showAnalytics = !wide,
                                    onSignOut = viewModel::signOut,
                                )
                            }
                            composable<CategoriesRoute> {
                                CategoriesScreen(
                                    state = state,
                                    onBack = navController::popBackStack,
                                    onAddCategory = viewModel::addCategory,
                                    onRenameCategory = viewModel::renameCategory,
                                    onArchiveCategory = viewModel::archiveCategory,
                                    onAddSubcategory = viewModel::addSubcategoryWithPlan,
                                    onRenameSubcategory = viewModel::renameSubcategory,
                                    onDeleteSubcategory = viewModel::deleteSubcategory,
                                )
                            }
                            composable<BalanceCheckRoute> {
                                BalanceCheckScreen(state = state, onBack = navController::popBackStack, onCheck = viewModel::checkBalance)
                            }
                            composable<DebtsRoute> {
                                DebtsScreen(
                                    state = state,
                                    onBack = null,
                                    onSave = viewModel::saveDebt,
                                    onClose = viewModel::closeDebt,
                                    onPlanIntoMonth = viewModel::planDebtsIntoMonth,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private inline fun <reified T : Any> NavHostController.isOn(): Boolean =
    currentBackStackEntry?.destination?.hasRoute<T>() == true

private fun NavHostController.switchTo(route: Any) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.startDestinationId) { saveState = true }
    }
}

@Composable
private fun HeaderBar(state: HomeState, viewModel: HomeViewModel, wide: Boolean) {
    Surface(tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Nomad Budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.loading) {
                        LinearProgressIndicator(modifier = Modifier.weight(1f))
                    } else {
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TotalsInline(state)
                        }
                        Text(
                            "на жизнь ${MoneyFormat.format(state.operationalBase, false)} · накопления ${MoneyFormat.format(state.totalBase - state.operationalBase, false)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PeriodSwitcher(state, viewModel)
                }
                RatesWarning(state)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Nomad Budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    PeriodSwitcher(state, viewModel)
                }
                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TotalsInline(state, Modifier.weight(1f))
                    }
                    Text(
                        "на жизнь ${MoneyFormat.format(state.operationalBase, false)} · накопления ${MoneyFormat.format(state.totalBase - state.operationalBase, false)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    RatesWarning(state)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun TotalsInline(state: HomeState, cardModifier: Modifier = Modifier) {
    TotalCard(Currency.BASE, MoneyFormat.format(state.totalBase, showFraction = false), state, cardModifier)
    state.foreignCurrencies.filter(state.rates::hasRate).forEach { currency ->
        TotalCard(currency, MoneyFormat.format(state.rates.fromBase(state.totalBase, currency), showFraction = false), state, cardModifier)
    }
}

@Composable
private fun RatesWarning(state: HomeState) {
    if (state.offline || state.pendingCount > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                buildString {
                    if (state.offline) append("Офлайн · данные от ${state.cachedAt?.let(DateFormat::dayMonthTime) ?: "—"}")
                    if (state.pendingCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("ждут отправки: ${state.pendingCount}")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.warning,
            )
            InfoHint("Офлайн", Hints.OFFLINE)
        }
    }
    val warning = when {
        state.currenciesWithoutRate.isNotEmpty() ->
            "Нет курса для ${state.currenciesWithoutRate.joinToString { it.code }}: заполни таблицу курсов"
        state.rates.rateFor(Currency.USD)?.source == RateSource.MANUAL ->
            "Курсы по умолчанию: таблица курсов ещё пуста"
        else -> null
    }
    warning?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.warning, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PeriodSwitcher(state: HomeState, viewModel: HomeViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = viewModel::refresh, enabled = !state.loading && !state.refreshing) {
                if (state.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить данные")
                }
            }
            state.lastSyncedAt?.let {
                Text(
                    DateFormat.timeOnly(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = viewModel::showPreviousPeriod) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
        }
        TextButton(onClick = viewModel::showCurrentPeriod) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.period.title(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = state.dayNumber?.let { "день $it из ${state.period.lengthDays}" } ?: "${state.period.lengthDays} дней",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = viewModel::showNextPeriod) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
        }
    }
}

@Composable
private fun TotalCard(currency: Currency, value: String, state: HomeState, modifier: Modifier = Modifier) {
    val color = currencyColor(currency, state)
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (currency == Currency.BASE) "Всего" else "в ${currency.code}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        }
    }
}
