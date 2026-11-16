package ru.nomadbudget.presentation.onboarding

import ru.nomadbudget.features.AppModule
import ru.nomadbudget.features.FeatureGate
import ru.nomadbudget.presentation.format.rememberAmountTransformation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.IsoCurrencies
import ru.nomadbudget.presentation.components.AddTile
import ru.nomadbudget.presentation.components.AppIcons
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.CurrencyRow
import ru.nomadbudget.presentation.components.CurrencySearchField
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.IncomeDaysEditor
import ru.nomadbudget.presentation.components.PrimaryButton
import ru.nomadbudget.presentation.components.SecondaryButton
import ru.nomadbudget.presentation.components.StartDayPicker
import ru.nomadbudget.presentation.components.Ui
import ru.nomadbudget.presentation.components.TagChip
import androidx.compose.ui.platform.testTag
import ru.nomadbudget.presentation.components.incomeDaysText
import ru.nomadbudget.presentation.components.startDayText
import ru.nomadbudget.presentation.format.LocalBaseCurrency
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.HomeState

private enum class Step(val title: String, val subtitle: String) {
    PERIOD("Когда начинается твой месяц бюджета", "От этого дня считаются план, конверты и итоги. Если доход приходит в разные дни или нерегулярно, оставь 1 число."),
    CURRENCIES("Валюта", "В ней считаются итоги, план и «Можно ещё потратить». Если деньги лежат в разных валютах, выбери «В нескольких» — итоги всё равно будут в одной основной."),
    ACCOUNTS("Где лежат деньги", "Роль счёта решает, куда он попадает: «На жизнь» — обычные траты, «Копилка» — деньги на крупное, «Резерв» — запас на чёрный день. Названия и роли можно поменять позже на экране «Счета»."),
    CATEGORIES("На что уходят деньги", "Это конверты месяца. Сними лишнее и добавь своё. Подкатегории появятся у стандартных категорий."),
    BALANCES("Сколько сейчас на счетах", "Можно пропустить и свериться позже кнопкой «Сверить остаток». Дальше — план на месяц и первая запись."),
}

class OnboardingState {
    var base: Currency by mutableStateOf(Currency.DEFAULT_BASE)
    var step: Int by mutableIntStateOf(0)
    var startDay: Int by mutableIntStateOf(1)
    var incomeDays: Set<Int> by mutableStateOf(emptySet())
    val currencyCodes: SnapshotStateList<String> = mutableStateListOf()
    val accounts: SnapshotStateList<OnboardingAccount> = mutableStateListOf<OnboardingAccount>().apply { addAll(OnboardingDefaults.accounts()) }
    val categories: SnapshotStateList<OnboardingCategory> = mutableStateListOf<OnboardingCategory>().apply { addAll(OnboardingDefaults.categories()) }
    var newCategory: String by mutableStateOf("")
    var newCategoryKind: CategoryKind by mutableStateOf(CategoryKind.EXPENSE)
    var pickingBase: Boolean by mutableStateOf(false)
    var multiCurrency: Boolean by mutableStateOf(false)

    private var accountSequence = 0

    fun syncAutoAccounts() {
        val wanted = if (multiCurrency) currencyCodes.toSet() else emptySet()
        accounts.removeAll { it.auto && it.currencyCode !in wanted }
        wanted.forEach { code ->
            if (accounts.none { it.currencyCode == code }) accounts += OnboardingDefaults.cashFor(code)
        }
    }

    fun changeBase(currency: Currency) {
        val old = base.code
        base = currency
        pickingBase = false
        currencyCodes.remove(currency.code)
        accounts.removeAll { it.auto && it.currencyCode == currency.code }
        accounts.indices.forEach { index ->
            val account = accounts[index]
            if (account.currencyCode == old) accounts[index] = account.copy(currencyCode = currency.code)
        }
    }

    fun addAccount() {
        accounts += OnboardingAccount("custom:${++accountSequence}", "", base.code, AccountRole.LIFE)
    }

    fun update(account: OnboardingAccount) {
        val index = accounts.indexOfFirst { it.key == account.key }
        if (index >= 0) accounts[index] = account.copy(auto = false)
    }

    fun remove(key: String) {
        accounts.removeAll { it.key == key }
    }

    fun toggleCategory(index: Int) {
        categories[index] = categories[index].copy(checked = !categories[index].checked)
    }

    fun addCategory() {
        val name = newCategory.trim()
        if (name.isEmpty() || categories.any { it.kind == newCategoryKind && it.name.equals(name, ignoreCase = true) }) return
        categories += OnboardingCategory(name, newCategoryKind)
        newCategory = ""
    }

    fun availableCurrencies(): List<String> = listOf(base.code) + currencyCodes

    fun result(): OnboardingResult = OnboardingResult(
        periodStartDay = startDay,
        baseCode = base.code,
        incomeDays = incomeDays.sorted(),
        currencyCodes = if (multiCurrency) currencyCodes.toList() else emptyList(),
        multiCurrency = multiCurrency,
        accounts = accounts
            .filter { it.name.isNotBlank() && (multiCurrency || !(it.auto && it.currencyCode != base.code)) }
            .map { if (multiCurrency) it else it.copy(currencyCode = base.code) }
            .map { account ->
                val currency = IsoCurrencies.find(account.currencyCode)?.toCurrency() ?: base
                OnboardingAccountResult(account.name.trim(), account.currencyCode, account.role, MoneyFormat.parse(account.balanceText, currency))
            },
        categories = categories.toList(),
    )
}

@Composable
fun OnboardingScreen(state: HomeState, messages: Flow<String>, onFinish: (OnboardingResult) -> Unit) {
    val form = remember { OnboardingState() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(messages) { messages.collect { snackbar.showSnackbar(it) } }
    val step = Step.entries[form.step]
    val last = form.step == Step.entries.lastIndex

    fun goTo(index: Int) {
        form.step = index.coerceIn(0, Step.entries.lastIndex)
        if (Step.entries[form.step] == Step.ACCOUNTS) form.syncAutoAccounts()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        CenteredLazyColumn(maxContentWidth = Ui.FormMaxWidth, top = 24.dp, bottom = 32.dp) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(padding)) {
                    Text("Nomad Budget · настройка, один раз", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Шаг ${form.step + 1} из ${Step.entries.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { (form.step + 1f) / Step.entries.size }, modifier = Modifier.fillMaxWidth())
                    Text(step.title, style = MaterialTheme.typography.headlineSmall)
                    Text(step.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Ui.CardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (step) {
                            Step.PERIOD -> PeriodStep(form)
                            Step.CURRENCIES -> CurrenciesStep(form)
                            Step.ACCOUNTS -> AccountsStep(form)
                            Step.CATEGORIES -> CategoriesStep(form)
                            Step.BALANCES -> BalancesStep(form)
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (form.step > 0) SecondaryButton(onClick = { goTo(form.step - 1) }, enabled = !state.saving) { Text("Назад") }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End)  {
                        if (!last) TextButton(onClick = { goTo(form.step + 1) }, enabled = !state.saving) { Text("Пропустить") }
                    }
                    PrimaryButton(onClick = { if (last) onFinish(form.result()) else goTo(form.step + 1) }, enabled = !state.saving) {
                        Text(
                            when {
                                state.saving -> "Создание…"
                                last -> "Готово"
                                else -> "Далее"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodStep(form: OnboardingState) {
    Text("Месяц начинается ${startDayText(form.startDay)}", fontWeight = FontWeight.Medium)
    StartDayPicker(selected = form.startDay, onSelect = { form.startDay = it })
    HorizontalDivider()
    Text("Дни, когда обычно приходит доход: ${incomeDaysText(form.incomeDays)}", fontWeight = FontWeight.Medium)
    Text("Необязательно. Подсказка для планирования, месяц не делит.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    IncomeDaysEditor(days = form.incomeDays, onChange = { form.incomeDays = it })
}

@Composable
private fun CurrenciesStep(form: OnboardingState) {
    if (canHaveSeveralCurrencies()) {
        Text("Где лежат твои деньги", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagChip("В одной валюте", onClick = { form.multiCurrency = false }, selected = !form.multiCurrency, modifier = Modifier.testTag(OnboardingTags.ONE_CURRENCY))
            TagChip("В нескольких", onClick = { form.multiCurrency = true }, selected = form.multiCurrency, modifier = Modifier.testTag(OnboardingTags.SEVERAL_CURRENCIES))
        }
        HorizontalDivider()
    }
    CurrencyRow(code = form.base.code, name = IsoCurrencies.nameOf(form.base.code), trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("базовая", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { form.pickingBase = !form.pickingBase }) { Text(if (form.pickingBase) "Отмена" else "Сменить") }
        }
    })
    if (form.pickingBase) {
        CurrencySearchField(exclude = setOf(form.base.code), onPick = { form.changeBase(it.toCurrency()) })
        Text(
            "Сменить базовую валюту можно и позже в настройках, но тогда все суммы пересчитаются по курсам. Курс новой базы к другим валютам подтянется в течение суток.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!form.multiCurrency) {
        Text("Все счета будут в этой валюте. Если позже появятся деньги в другой, включи «Мультивалютность»: «Настройки» → «Модули».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    form.currencyCodes.forEach { code ->
        HorizontalDivider()
        CurrencyRow(code = code, name = IsoCurrencies.nameOf(code), trailing = {
            IconButton(onClick = { form.currencyCodes.remove(code) }) { Icon(AppIcons.Close, contentDescription = "Убрать $code") }
        })
    }
    HorizontalDivider()
    CurrencySearchField(exclude = form.availableCurrencies().toSet(), onPick = { form.currencyCodes += it.code })
}

fun canHaveSeveralCurrencies(): Boolean = FeatureGate.state.copy(modulesOff = emptySet()).isOn(AppModule.CURRENCIES)

object OnboardingTags {
    const val ONE_CURRENCY = "onboarding.one_currency"
    const val SEVERAL_CURRENCIES = "onboarding.several_currencies"
}

fun onboardingRoles(): List<AccountRole> = AccountRole.entries.filter { role ->
    when (role) {
        AccountRole.FUND -> FeatureGate.isOn(AppModule.FUNDS)
        AccountRole.CUSHION -> FeatureGate.isOn(AppModule.CUSHION)
        else -> true
    }
}

@Composable
private fun AccountsStep(form: OnboardingState) {
    val roles = onboardingRoles()
    val multiCurrency = form.multiCurrency
    LaunchedEffect(roles, multiCurrency) {
        form.accounts.filter { it.role !in roles || (!multiCurrency && it.currencyCode != form.base.code) }.forEach {
            form.update(it.copy(role = if (it.role in roles) it.role else AccountRole.LIFE, currencyCode = if (multiCurrency) it.currencyCode else form.base.code))
        }
    }
    form.accounts.forEachIndexed { index, account ->
        if (index > 0) HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = account.name,
                    onValueChange = { form.update(account.copy(name = it)) },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { form.remove(account.key) }) { Icon(AppIcons.Trash, contentDescription = "Убрать счёт") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (multiCurrency) {
                    Dropdown("Валюта", form.availableCurrencies(), account.currencyCode, { it }, { form.update(account.copy(currencyCode = it)) }, modifier = Modifier.width(CURRENCY_WIDTH))
                }
                if (roles.size > 1) Dropdown("Роль", roles, account.role, ::roleLabel, { form.update(account.copy(role = it)) }, modifier = Modifier.weight(1f))
            }
        }
    }
    AddTile("Добавить счёт", onClick = form::addAccount)
}

@Composable
private fun CategoriesStep(form: OnboardingState) {
    CategoryKind.entries.forEach { kind ->
        Text(if (kind == CategoryKind.EXPENSE) "Расходы" else "Доходы", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        form.categories.forEachIndexed { index, category ->
            if (category.kind != kind) return@forEachIndexed
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = category.checked, onCheckedChange = { form.toggleCategory(index) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name)
                    if (category.subcategories.isNotEmpty()) {
                        Text(category.subcategories.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = form.newCategory,
            onValueChange = { form.newCategory = it },
            label = { Text("Своя категория") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Dropdown("Тип", CategoryKind.entries, form.newCategoryKind, { if (it == CategoryKind.EXPENSE) "расход" else "доход" }, { form.newCategoryKind = it }, modifier = Modifier.width(KIND_WIDTH))
        IconButton(onClick = form::addCategory, enabled = form.newCategory.isNotBlank()) { Icon(AppIcons.Add, contentDescription = "Добавить категорию") }
    }
}

@Composable
private fun BalancesStep(form: OnboardingState) {
    val transformation = rememberAmountTransformation()
    val named = form.accounts.filter { it.name.isNotBlank() }
    if (named.isEmpty()) {
        Text("Счетов нет, остатки вводить некуда. Вернись на шаг «Счета» или пропусти.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    named.forEachIndexed { index, account ->
        if (index > 0) HorizontalDivider()
        val currency = IsoCurrencies.find(account.currencyCode)?.toCurrency() ?: form.base
        OutlinedTextField(
            value = account.balanceText,
            onValueChange = { form.update(account.copy(balanceText = ThousandsVisualTransformation.sanitize(it, currency.minorUnits))) },
            label = { Text("${account.name}, ${account.currencyCode}") },
            placeholder = { Text("0") },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
    }
}

private fun roleLabel(role: AccountRole): String = when (role) {
    AccountRole.LIFE -> "На жизнь"
    AccountRole.FUND -> "Копилка"
    AccountRole.CUSHION -> "Резерв"
}

private val CURRENCY_WIDTH = 128.dp
private val KIND_WIDTH = 132.dp
