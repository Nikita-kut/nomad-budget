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
import androidx.compose.material.icons.filled.Edit
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
import ru.nomadbudget.demo.DemoMode
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
import ru.nomadbudget.presentation.planning.PlanningScreen
import ru.nomadbudget.presentation.rates.RatesScreen
import ru.nomadbudget.presentation.theme.AppTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.navigation.NavDestination.Companion.hierarchy
import ru.nomadbudget.presentation.journal.JournalScreen
import ru.nomadbudget.presentation.entry.EntryFormState
import ru.nomadbudget.shareTextFile
import ru.nomadbudget.domain.logic.CsvExporter
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

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

@Serializable
object PlanningRoute

@Serializable
object JournalRoute

private data class Tab(
    val route: Any,
    val title: String,
    val icon: ImageVector,
    val primary: Boolean,
    val isSelected: (NavHostController) -> Boolean,
)

private val WIDE_LAYOUT_MIN_WIDTH = 840.dp
private val CONTENT_MAX_WIDTH = 760.dp
private val WIDE_CONTENT_MAX_WIDTH = 1240.dp
private val TWO_COLUMN_MIN_WIDTH = 1100.dp

@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val entryForm = remember { EntryFormState(state.today) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(viewModel) {
        viewModel.navigateToEntry.collect { navController.navigate(EntryRoute) { launchSingleTop = true } }
    }
    LaunchedEffect(viewModel) {
        viewModel.undoRequests.collect { request ->
            launch {
                val result = snackbar.showSnackbar(request.text, actionLabel = "Отменить", duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(request.id) else viewModel.commitDelete(request.id)
            }
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.reloadDrafts()
        onPauseOrDispose {}
    }

    val tabs = remember {
        listOf(
            Tab(MonthRoute, "Месяц", Icons.Filled.CalendarMonth, primary = true) { it.isOn<MonthRoute>() },
            Tab(JournalRoute, "Журнал", Icons.AutoMirrored.Filled.ReceiptLong, primary = true) { it.isOn<JournalRoute>() },
            Tab(PlanningRoute, "План", Icons.Filled.EditCalendar, primary = true) { it.isOn<PlanningRoute>() },
            Tab(AccountsRoute, "Счета", Icons.Filled.AccountBalanceWallet, primary = true) { it.isOn<AccountsRoute>() },
            Tab(ExchangeRoute, "Обмен", Icons.Filled.CurrencyExchange, primary = false) { it.isOn<ExchangeRoute>() },
            Tab(DebtsRoute, "Кредиты", Icons.Filled.CreditCard, primary = false) { it.isOn<DebtsRoute>() },
            Tab(RatesRoute, "Курсы", Icons.AutoMirrored.Filled.ShowChart, primary = false) { it.isOn<RatesRoute>() },
            Tab(ChartsRoute, "Графики", Icons.Filled.BarChart, primary = false) { it.isOn<ChartsRoute>() },
            Tab(MoreRoute, "Ещё", Icons.Filled.MoreHoriz, primary = true) {
                it.isOn<MoreRoute>() || it.isOn<CategoriesRoute>() || it.isOn<BalanceCheckRoute>() ||
                    it.isOn<RatesRoute>() || it.isOn<ChartsRoute>() || it.isOn<ExchangeRoute>() || it.isOn<DebtsRoute>()
            },
        )
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) {
                    false
                } else {
                    when (event.key) {
                        Key.N -> navController.navigate(EntryRoute) { launchSingleTop = true }
                        Key.J -> navController.switchTo(JournalRoute)
                        Key.P -> navController.switchTo(PlanningRoute)
                        Key.M -> navController.switchTo(MonthRoute)
                        Key.A -> navController.switchTo(AccountsRoute)
                        Key.Escape -> navController.popBackStack()
                        else -> return@onKeyEvent false
                    }
                    true
                }
            },
    ) {
        val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
        val twoColumns = maxWidth >= TWO_COLUMN_MIN_WIDTH
        val backStack by navController.currentBackStackEntryAsState()
        val onEntry = backStack != null && navController.isOn<EntryRoute>()
        val openEntry = { navController.navigate(EntryRoute) { launchSingleTop = true } }
        val back = fun() { navController.popBackStack() }
        val contentMaxWidth = if (twoColumns && backStack != null && navController.isOn<MonthRoute>()) WIDE_CONTENT_MAX_WIDTH else CONTENT_MAX_WIDTH

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { if (!onEntry || wide) HeaderBar(state, viewModel, wide) },
            floatingActionButton = {
                if (!wide && !onEntry) {
                    FloatingActionButton(onClick = openEntry) { Icon(Icons.Filled.Add, contentDescription = "Новая запись") }
                }
            },
            bottomBar = {
                if (!wide && !onEntry) {
                    NavigationBar {
                        tabs.filter { it.primary }.forEach { tab ->
                            NavigationBarItem(
                                selected = backStack != null && tab.isSelected(navController),
                                onClick = { navController.switchTo(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.title) },
                                label = { Text(tab.title, maxLines = 1) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail(
                        header = {
                            FloatingActionButton(onClick = openEntry, modifier = Modifier.padding(vertical = 8.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = "Новая запись")
                            }
                        },
                    ) {
                        tabs.forEach { tab ->
                            NavigationRailItem(
                                selected = backStack != null && (if (tab.route == MoreRoute) navController.isOn<MoreRoute>() || navController.isOn<CategoriesRoute>() || navController.isOn<BalanceCheckRoute>() else tab.isSelected(navController)),
                                onClick = { navController.switchTo(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.title) },
                                label = { Text(tab.title) },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxSize()) {
                        NavHost(navController = navController, startDestination = MonthRoute) {
                            composable<MonthRoute> {
                                MonthScreen(
                                    state = state,
                                    onSetItemPlanned = viewModel::setPlannedForSubcategoryName,
                                    onRemoveItem = viewModel::removePlanLine,
                                    onSaveCategoryPlan = viewModel::saveCategoryPlan,
                                    onCopyPlan = viewModel::copyPlanFromPreviousPeriod,
                                    onOpenPlanning = { navController.switchTo(PlanningRoute) },
                                    onRetry = viewModel::load,
                                    twoColumns = twoColumns,
                                    onOpenAccounts = { navController.switchTo(AccountsRoute) },
                                    onOpenEntry = openEntry,
                                )
                            }
                            composable<JournalRoute> {
                                JournalScreen(
                                    state = state,
                                    onDelete = viewModel::deleteTransaction,
                                    onUpdate = viewModel::updateTransaction,
                                    onRepeat = viewModel::repeatTransaction,
                                )
                            }
                            composable<PlanningRoute> {
                                PlanningScreen(
                                    state = state,
                                    onBack = null,
                                    onSaveCategoryPlan = viewModel::saveCategoryPlan,
                                    onCopyPlan = viewModel::copyPlanFromPreviousPeriod,
                                    onSetSavingsTarget = viewModel::setSavingsTarget,
                                )
                            }
                            composable<EntryRoute> {
                                EntryScreen(
                                    state = state,
                                    form = entryForm,
                                    onSubmit = viewModel::addEntry,
                                    onAddDraft = viewModel::addDraft,
                                    onUseDraft = viewModel::useDraft,
                                    onRemoveDraft = viewModel::removeDraft,
                                    onClearPrefill = viewModel::clearPrefill,
                                    onOpenExchange = {
                                        navController.popBackStack()
                                        navController.navigate(ExchangeRoute)
                                    },
                                    onBack = back,
                                )
                            }
                            composable<ExchangeRoute> {
                                ExchangeScreen(
                                    state = state,
                                    onSubmit = viewModel::addExchange,
                                    onDelete = viewModel::deleteTransaction,
                                    onBack = if (wide) null else back,
                                )
                            }
                            composable<AccountsRoute> {
                                AccountsScreen(
                                    state = state,
                                    onAdd = viewModel::addAccount,
                                    onRename = viewModel::renameAccount,
                                    onMove = viewModel::moveAccount,
                                    onArchive = viewModel::archiveAccount,
                                    onOpenDebts = { navController.navigate(DebtsRoute) },
                                    onOpenExchange = { navController.navigate(ExchangeRoute) },
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
                                    onExchange = { navController.navigate(ExchangeRoute) },
                                    onDebts = { navController.navigate(DebtsRoute) },
                                    showSecondary = !wide,
                                    onExport = {
                                        shareTextFile(
                                            "nomad-budget-${state.today}.csv",
                                            CsvExporter.export(state.transactions, state.accounts, state.categories, state.subcategories),
                                        )
                                    },
                                    onSignOut = viewModel::signOut,
                                )
                            }
                            composable<CategoriesRoute> {
                                CategoriesScreen(
                                    state = state,
                                    onBack = back,
                                    onAddCategory = viewModel::addCategory,
                                    onRenameCategory = viewModel::renameCategory,
                                    onArchiveCategory = viewModel::archiveCategory,
                                    onAddSubcategory = viewModel::addSubcategoryWithPlan,
                                    onRenameSubcategory = viewModel::renameSubcategory,
                                    onDeleteSubcategory = viewModel::deleteSubcategory,
                                )
                            }
                            composable<BalanceCheckRoute> {
                                BalanceCheckScreen(state = state, onBack = back, onCheck = viewModel::checkBalance)
                            }
                            composable<DebtsRoute> {
                                DebtsScreen(
                                    state = state,
                                    onBack = if (wide) null else back,
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
        popUpTo(graph.startDestinationId)
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
                    if (DemoMode.enabled) DemoBadge()
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PeriodNavigator(state, viewModel, modifier = Modifier.weight(1f))
                    if (DemoMode.enabled) DemoBadge()
                    RefreshButton(state, viewModel)
                }
                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Всего", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(MoneyFormat.format(state.totalBase, false), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        Text(
                            "на жизнь ${MoneyFormat.format(state.operationalBase, false)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
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
        RefreshButton(state, viewModel)
        PeriodNavigator(state, viewModel)
    }
}

@Composable
private fun RefreshButton(state: HomeState, viewModel: HomeViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = viewModel::refresh, enabled = !state.loading && !state.refreshing) {
            if (state.refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить данные")
            }
        }
        state.lastSyncedAt?.let {
            Text(DateFormat.timeOnly(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PeriodNavigator(state: HomeState, viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::showPreviousPeriod) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
        }
        Column(
            modifier = Modifier.weight(1f, fill = false).clip(MaterialTheme.shapes.small).clickable(onClick = viewModel::showCurrentPeriod).padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(DateFormat.monthName(state.period.start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                listOfNotNull(DateFormat.periodRange(state.period), state.dayNumber?.let { "день $it из ${state.period.lengthDays}" }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun DemoBadge() {
    Surface(color = MaterialTheme.colorScheme.tertiary, shape = MaterialTheme.shapes.small) {
        Text(
            "ДЕМО",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
