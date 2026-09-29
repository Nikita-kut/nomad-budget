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
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import ru.nomadbudget.presentation.entry.EntryScreen
import ru.nomadbudget.presentation.exchange.ExchangeScreen
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.month.MonthScreen
import ru.nomadbudget.presentation.more.BalanceCheckScreen
import ru.nomadbudget.presentation.more.CategoriesScreen
import ru.nomadbudget.presentation.more.MoreScreen
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
object MoreRoute

@Serializable
object CategoriesRoute

@Serializable
object BalanceCheckRoute

private data class Tab(val route: Any, val title: String, val icon: ImageVector, val isSelected: (NavHostController) -> Boolean)

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

    val tabs = remember {
        listOf(
            Tab(MonthRoute, "Месяц", Icons.Filled.DateRange) { it.isOn<MonthRoute>() },
            Tab(EntryRoute, "Ввод", Icons.Filled.AddCircle) { it.isOn<EntryRoute>() },
            Tab(ExchangeRoute, "Обмен", Icons.Filled.Refresh) { it.isOn<ExchangeRoute>() },
            Tab(AccountsRoute, "Счета", Icons.Filled.AccountBox) { it.isOn<AccountsRoute>() },
            Tab(MoreRoute, "Ещё", Icons.Filled.Settings) { it.isOn<MoreRoute>() || it.isOn<CategoriesRoute>() || it.isOn<BalanceCheckRoute>() },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
        val backStack by navController.currentBackStackEntryAsState()

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { HeaderBar(state, viewModel) },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = backStack != null && tab.isSelected(navController),
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
                                    onCopyPlan = viewModel::copyPlanFromPreviousPeriod,
                                    onRetry = viewModel::load,
                                )
                            }
                            composable<EntryRoute> {
                                EntryScreen(state = state, onSubmit = viewModel::addEntry, onDelete = viewModel::deleteTransaction)
                            }
                            composable<ExchangeRoute> {
                                ExchangeScreen(state = state, onSubmit = viewModel::addExchange, onDelete = viewModel::deleteTransaction)
                            }
                            composable<AccountsRoute> {
                                AccountsScreen(state = state)
                            }
                            composable<MoreRoute> {
                                MoreScreen(
                                    state = state,
                                    onCategories = { navController.navigate(CategoriesRoute) },
                                    onBalanceCheck = { navController.navigate(BalanceCheckRoute) },
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
                                    onAddSubcategory = viewModel::addSubcategory,
                                    onRenameSubcategory = viewModel::renameSubcategory,
                                    onDeleteSubcategory = viewModel::deleteSubcategory,
                                )
                            }
                            composable<BalanceCheckRoute> {
                                BalanceCheckScreen(state = state, onBack = navController::popBackStack, onCheck = viewModel::checkBalance)
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
private fun HeaderBar(state: HomeState, viewModel: HomeViewModel) {
    Surface(tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = CONTENT_MAX_WIDTH),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Nomad Budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
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
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TotalCard(Currency.BASE, MoneyFormat.format(state.totalBase, showFraction = false), Modifier.weight(1f))
                    state.foreignCurrencies.filter(state.rates::hasRate).forEach { currency ->
                        TotalCard(currency, MoneyFormat.format(state.rates.fromBase(state.totalBase, currency), showFraction = false), Modifier.weight(1f))
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
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.warning,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun TotalCard(currency: Currency, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = if (currency == Currency.BASE) "Всего · ${currency.code}" else "в ${currency.code}",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.currency(currency),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
