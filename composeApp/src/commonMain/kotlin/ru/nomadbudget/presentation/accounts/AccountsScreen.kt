package ru.nomadbudget.presentation.accounts

import ru.nomadbudget.presentation.components.SecondaryButton
import ru.nomadbudget.presentation.components.PrimaryButton
import ru.nomadbudget.presentation.components.TileRow
import ru.nomadbudget.presentation.components.StatTile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.presentation.planning.FundSettingsDialog
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.RateSource
import ru.nomadbudget.presentation.components.CurrencyAmount
import ru.nomadbudget.presentation.components.CurrencyChip
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.KeyValueRow
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.components.AccountName
import ru.nomadbudget.presentation.theme.AppTheme
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.domain.logic.BalanceCalculator
import ru.nomadbudget.domain.logic.ExchangeAnalyzer
import ru.nomadbudget.presentation.components.TextDialog
import androidx.compose.foundation.layout.height
import ru.nomadbudget.presentation.components.AppIcons
import ru.nomadbudget.presentation.home.NewAccountDraft
import ru.nomadbudget.presentation.components.AddTile
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import ru.nomadbudget.presentation.components.Ui

@Composable
fun AccountsScreen(
    state: HomeState,
    onAdd: (NewAccountDraft) -> Unit,
    onRename: (String, String) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onArchive: (String) -> Unit,
    onSetRole: (String, AccountRole) -> Unit = { _, _ -> },
    onSetFund: (String, Money?, Money?, Int?) -> Unit = { _, _, _, _ -> },
    onCheckBalance: () -> Unit = {},
    twoColumns: Boolean = false,
    onOpenDebts: () -> Unit = {},
    onOpenExchange: () -> Unit = {},
) {
    val daily = state.operationalAccounts
    val funds = state.fundAccounts
    val savings = state.cushionAccounts
    var adding by remember { mutableStateOf(false) }
    var changingRole by remember { mutableStateOf<Account?>(null) }
    var editingFund by remember { mutableStateOf<Account?>(null) }
    var archiving by remember { mutableStateOf<Account?>(null) }
    var renaming by remember { mutableStateOf<Account?>(null) }
    var editMode by remember { mutableStateOf(false) }

    CenteredLazyColumn {
        item { TotalsCard(state, twoColumns) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(onClick = onOpenExchange, modifier = Modifier.weight(1f)) { Text("Обменять валюту") }
                SecondaryButton(onClick = onOpenDebts, modifier = Modifier.weight(1f)) {
                    Text(if (state.openDebts.isEmpty()) "Кредиты" else "Кредиты: ${state.openDebts.size}")
                }
            }
        }
        val lifeTitle: @Composable () -> Unit = {
            Row(modifier = Modifier.fillMaxWidth().height(TITLE_HEIGHT), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("На жизнь", hint = if (editMode) "стрелки меняют порядок" else "карты и наличные", modifier = Modifier.weight(1f), info = Hints.ACCOUNTS_TOTAL)
                TextButton(onClick = { editMode = !editMode }) { Text(if (editMode) "Готово" else "Изменить") }
            }
        }
        val lifeCard: @Composable () -> Unit = {
            AccountsCard(daily, state, totalLabel = "Итого на жизнь", editMode = editMode, onRename = { renaming = it }, onMove = onMove, onArchive = { archiving = it }, onRole = { changingRole = it })
        }
        val fundsCard: @Composable () -> Unit = {
            AccountsCard(funds, state, totalLabel = "Итого в копилках", editMode = editMode, onRename = { renaming = it }, onMove = onMove, onArchive = { archiving = it }, onRole = { changingRole = it }, onOpen = { editingFund = it }, empty = "Копилок нет. Сделай копилкой счёт, на котором копишь на аренду или подарки: «Изменить» → тип счёта.")
        }
        val cushionCard: @Composable () -> Unit = {
            AccountsCard(savings, state, totalLabel = "Итого в подушке", editMode = editMode, onRename = { renaming = it }, onMove = onMove, onArchive = { archiving = it }, onRole = { changingRole = it })
        }
        item { lifeTitle() }
        run {
            item { lifeCard() }
            item { SectionTitle("Копилки", hint = "под крупные регулярные траты", info = Hints.FUNDS) }
            item { fundsCard() }
            item { SectionTitle("Подушка", hint = "запас на чёрный день") }
            item { cushionCard() }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui.Gap), verticalAlignment = Alignment.CenterVertically) {
                AddTile("Добавить счёт", onClick = { adding = true }, modifier = Modifier.weight(1f))
                SecondaryButton(onClick = onCheckBalance, modifier = Modifier.weight(1f)) { Text("Сверить остаток") }
            }
        }
        item { SectionTitle("Курсы", hint = rateHint(state), info = Hints.RATES) }
        item { RatesCard(state) }
    }

    if (adding) {
        AddAccountDialog(currencies = state.currencies, onDismiss = { adding = false }) { draft ->
            onAdd(draft)
            adding = false
        }
    }
    changingRole?.let { account ->
        RoleDialog(account, onDismiss = { changingRole = null }) { role ->
            onSetRole(account.id, role)
            changingRole = null
        }
    }
    editingFund?.let { account ->
        FundSettingsDialog(account, onDismiss = { editingFund = null }) { monthly, goal, day ->
            onSetFund(account.id, monthly, goal, day)
            editingFund = null
        }
    }
    renaming?.let { account ->
        TextDialog(title = "Переименовать счёт", initial = account.name, onDismiss = { renaming = null }) { name ->
            onRename(account.id, name)
            renaming = null
        }
    }
    archiving?.let { account ->
        val balance = state.balances[account.id]
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text("Убрать «${account.name}» в архив?") },
            text = {
                Text(
                    buildString {
                        append("Счёт исчезнет из списков и итогов, операции по нему останутся в истории.")
                        if (balance != null && !balance.isZero) {
                            append(" На счёте сейчас ${MoneyFormat.format(balance)}: сначала переведи деньги на другой счёт или сверь остаток в ноль.")
                        }
                    },
                )
            },
            confirmButton = {
                PrimaryButton(onClick = { onArchive(account.id); archiving = null }, enabled = balance?.isZero != false) { Text("В архив") }
            },
            dismissButton = { TextButton(onClick = { archiving = null }) { Text("Отмена") } },
        )
    }
}

private fun rateHint(state: HomeState): String {
    val fromApi = state.rates.all.firstOrNull { it.source == RateSource.API } ?: return "ещё не загружены"
    return buildString {
        append("${fromApi.sourceName} · за ${DateFormat.dayMonth(fromApi.date)}")
        fromApi.fetchedAt?.let { append(" · получен ${DateFormat.dayMonthTime(it)}") }
    }
}

@Composable
private fun AccountsCard(
    accounts: List<Account>,
    state: HomeState,
    totalLabel: String,
    editMode: Boolean,
    onRename: (Account) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onArchive: (Account) -> Unit,
    onRole: (Account) -> Unit = {},
    onOpen: ((Account) -> Unit)? = null,
    empty: String = "Счетов нет",
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (accounts.isEmpty()) EmptyHint(empty)
        accounts.forEachIndexed { index, account ->
            if (index > 0) HorizontalDivider()
            AccountRow(
                account = account,
                state = state,
                editMode = editMode,
                canMoveUp = index > 0,
                canMoveDown = index < accounts.lastIndex,
                onRename = { onRename(account) },
                onMoveUp = { onMove(account.id, true) },
                onMoveDown = { onMove(account.id, false) },
                onArchive = { onArchive(account) },
                onRole = { onRole(account) },
                onOpen = onOpen?.let { open -> { open(account) } },
            )
        }
        if (accounts.isEmpty()) return@Card
        HorizontalDivider()
        val total = BalanceCalculator.totalInBase(accounts, state.balances, state.rates)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(totalLabel, fontWeight = FontWeight.SemiBold)
            Text(MoneyFormat.format(total, false), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    state: HomeState,
    editMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit,
    onRole: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val balance = state.balances.getValue(account.id)
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onOpen != null && !editMode) Modifier.clickable(onClick = onOpen) else Modifier).padding(start = 14.dp, end = if (editMode) 4.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editMode) {
            Column {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(AppIcons.ChevronUp, contentDescription = "Выше") }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(AppIcons.ChevronDown, contentDescription = "Ниже") }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            AccountName(account, fontWeight = FontWeight.Medium)
            if (account.isFund) {
                val parts = listOfNotNull(
                    account.fundMonthly?.let { "взнос ${MoneyFormat.format(it, false)} в месяц" },
                    account.fundGoal?.let { goal -> account.fundDay?.let { d -> "оплата ${MoneyFormat.format(goal, false)} $d-го" } ?: "цель ${MoneyFormat.format(goal, false)}" },
                )
                Text(
                    parts.joinToString(" · ").ifEmpty { "нажми, чтобы задать взнос" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CurrencyChip(account.currency, state)
                if (!account.name.contains(kindLabel(account.kind), ignoreCase = true) && !account.isSavings) {
                    Text(kindLabel(account.kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (balance.isNegative) {
                    Text("в минусе", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.bad, fontWeight = FontWeight.SemiBold)
                }
            }
            state.costBasis.averageCost[account.id]?.let { quote ->
                Text(MoneyFormat.averageCostText(quote), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            CurrencyAmount(MoneyFormat.format(balance), MaterialTheme.typography.bodyLarge, FontWeight.SemiBold)
            if (account.currency != Currency.BASE) {
                val inBase = state.rates.toBaseOrNull(balance)
                Text(
                    if (inBase != null) "≈ ${MoneyFormat.format(inBase, false)}" else "нет курса",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (editMode) {
            IconButton(onClick = onRename) {
                Icon(AppIcons.Edit, contentDescription = "Переименовать", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRole) {
                Icon(AppIcons.Swap, contentDescription = "Тип счёта", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onArchive) {
                Icon(AppIcons.Trash, contentDescription = "В архив", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddAccountDialog(currencies: List<Currency>, onDismiss: () -> Unit, onConfirm: (NewAccountDraft) -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(Currency.BASE) }
    var role by remember { mutableStateOf(AccountRole.LIFE) }
    var opening by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    val transformation = remember { ThousandsVisualTransformation() }
    val openingMoney = opening.takeIf { it.isNotBlank() }?.let { MoneyFormat.parse(it, currency) }
    val monthlyMoney = monthly.takeIf { it.isNotBlank() }?.let { MoneyFormat.parse(it, currency) }
    val goalMoney = goal.takeIf { it.isNotBlank() }?.let { MoneyFormat.parse(it, currency) }
    val dayValue = day.toIntOrNull()
    val valid = name.isNotBlank() &&
        (opening.isBlank() || openingMoney != null) &&
        (monthly.isBlank() || monthlyMoney != null) &&
        (goal.isBlank() || goalMoney != null) &&
        (day.isBlank() || dayValue in 1..31)
    @Composable
    fun MoneyField(value: String, onChange: (String) -> Unit, label: String, hint: String? = null) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(ThousandsVisualTransformation.sanitize(it, currency.minorUnits)) },
            label = { Text(label) },
            placeholder = hint?.let { { Text(it) } },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый счёт") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RolePicker(role) { role = it }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    placeholder = {
                        Text(
                            when (role) {
                                AccountRole.LIFE -> "карта, наличные…"
                                AccountRole.FUND -> "на аренду, на отпуск…"
                                AccountRole.CUSHION -> "накопительный счёт, вклад…"
                            },
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Dropdown("Валюта", currencies, currency, Currency::code, { currency = it })
                MoneyField(opening, { opening = it }, "Сейчас на счёте, ${currency.code}", "0")
                if (role == AccountRole.FUND) {
                    MoneyField(monthly, { monthly = it }, "Взнос в месяц, ${currency.code}", "необязательно")
                    MoneyField(goal, { goal = it }, "Ближайшая оплата, ${currency.code}", "необязательно")
                    OutlinedTextField(
                        value = day,
                        onValueChange = { day = it.filter(Char::isDigit).take(2) },
                        label = { Text("День оплаты") },
                        placeholder = { Text("необязательно") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Взнос попадёт в план каждого месяца, к дню оплаты в копилке должно набраться столько, сколько указано в «Ближайшей оплате».",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (role == AccountRole.CUSHION) {
                    Text(
                        "Сколько откладывать в подушку каждый месяц, задаётся в «Плане», строка «Себе».",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                onClick = {
                    onConfirm(
                        NewAccountDraft(
                            name = name,
                            currency = currency,
                            role = role,
                            openingBalance = openingMoney,
                            fundMonthly = monthlyMoney.takeIf { role == AccountRole.FUND },
                            fundGoal = goalMoney.takeIf { role == AccountRole.FUND },
                            fundDay = dayValue.takeIf { role == AccountRole.FUND },
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun kindLabel(kind: AccountKind): String = when (kind) {
    AccountKind.CARD -> "карта"
    AccountKind.ACCOUNT -> "счёт"
    AccountKind.CASH -> "наличные"
    AccountKind.SAVINGS -> "накопительный"
    AccountKind.INVESTMENT -> "инвестиции"
}

@Composable
private fun RatesCard(state: HomeState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            state.foreignCurrencies.forEach { currency ->
                if (state.rates.hasRate(currency)) {
                    KeyValueRow(currency.code, MoneyFormat.quoteText(ExchangeAnalyzer.baseQuote(currency, state.rates.basePerUnit(currency))))
                } else {
                    KeyValueRow(currency.code, "нет курса")
                }
            }
            state.foreignCurrencies
                .filter { it != Currency.USD && state.rates.hasRate(it) }
                .forEach { currency ->
                    KeyValueRow(
                        "${Currency.USD.code} → ${currency.code}, кросс-курс",
                        MoneyFormat.quoteText(ExchangeAnalyzer.pairQuote(Currency.USD, currency, state.rates.cross(Currency.USD, currency))),
                    )
                }
        }
    }
}


@Composable
private fun TotalsCard(state: HomeState, wide: Boolean) {
    val others = state.foreignCurrencies.filter(state.rates::hasRate).map { MoneyFormat.format(state.rates.fromBase(state.totalBase, it), false) }
    TileRow(
        wide,
        listOf(
            { m -> StatTile("Всего", MoneyFormat.format(state.totalBase, false), if (others.isEmpty()) "все счета по текущему курсу" else "≈ " + others.joinToString(" · "), modifier = m, accent = true) },
            { m -> StatTile("На жизнь", MoneyFormat.format(state.operationalBase, false), "карты и наличные для обычных трат", modifier = m) },
            { m -> StatTile("Копилки", MoneyFormat.format(state.fundsBalance, false), "на крупные регулярные траты", modifier = m) },
            { m -> StatTile("Подушка", MoneyFormat.format(state.savingsBalance, false), "запас на чёрный день", modifier = m) },
        ),
    )
}

@Composable
private fun RolePicker(selected: AccountRole, onSelect: (AccountRole) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            AccountRole.LIFE to ("На жизнь" to "карты и наличные, из них идут обычные траты"),
            AccountRole.FUND to ("Копилка" to "копишь на крупную регулярную трату и платишь её отсюда"),
            AccountRole.CUSHION to ("Подушка" to "запас на чёрный день, переводы сюда — «Себе»"),
        ).forEach { (role, texts) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(role) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == role, onClick = { onSelect(role) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(texts.first, fontWeight = FontWeight.Medium)
                    Text(texts.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RoleDialog(account: Account, onDismiss: () -> Unit, onConfirm: (AccountRole) -> Unit) {
    var role by remember { mutableStateOf(account.role) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Тип счёта «${account.name}»") },
        text = { RolePicker(role) { role = it } },
        confirmButton = { PrimaryButton(onClick = { onConfirm(role) }, enabled = role != account.role) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private val TITLE_HEIGHT = 40.dp
