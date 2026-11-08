package ru.nomadbudget.presentation.entry

import ru.nomadbudget.presentation.components.SecondaryButton
import ru.nomadbudget.presentation.components.PrimaryButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.presentation.components.CategoryChoice
import ru.nomadbudget.presentation.components.CategoryPicker
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.presentation.components.AccountDropdown
import ru.nomadbudget.presentation.components.CurrencyChip
import ru.nomadbudget.presentation.components.DateField
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.InfoHint
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.plural
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.EntryDraft
import ru.nomadbudget.presentation.home.EntryType
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.domain.logic.EntryDefaults
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.home.transactionKind
import ru.nomadbudget.presentation.theme.AppTheme
import ru.nomadbudget.domain.model.Draft
import ru.nomadbudget.presentation.home.EntryPrefill
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.Money
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import ru.nomadbudget.presentation.components.AppIcons

private const val MAX_QUICK_CATEGORIES = 8

class EntryFormState(today: LocalDate) {
    var type by mutableStateOf(EntryType.EXPENSE)
    var date by mutableStateOf(today)
    var account by mutableStateOf<Account?>(null)
    var accountTouched by mutableStateOf(false)
    var toAccount by mutableStateOf<Account?>(null)
    var category by mutableStateOf<Category?>(null)
    var amountText by mutableStateOf("")
    var subcategory by mutableStateOf("")
    var note by mutableStateOf("")
    var debt by mutableStateOf<Debt?>(null)
    var debtEarly by mutableStateOf(false)
    var submitCount by mutableStateOf(0)
    var splitOn by mutableStateOf(false)
    var splitAccount by mutableStateOf<Account?>(null)
    var splitAmountText by mutableStateOf("")
    var showErrors by mutableStateOf(false)

    val isDirty: Boolean get() = amountText.isNotBlank() || note.isNotBlank() || subcategory.isNotBlank()

    fun resetAfterSubmit() {
        amountText = ""
        subcategory = ""
        note = ""
        debt = null
        debtEarly = false
        category = null
        showErrors = false
        splitOn = false
        splitAmountText = ""
        submitCount++
    }

    fun clear() {
        resetAfterSubmit()
        type = EntryType.EXPENSE
        category = null
        accountTouched = false
        toAccount = null
    }
}

@Composable
fun EntryScreen(
    state: HomeState,
    form: EntryFormState,
    onSubmit: (EntryDraft) -> Unit,
    onAddDraft: (String) -> Unit,
    onUseDraft: (Draft) -> Unit,
    onRemoveDraft: (String) -> Unit,
    onClearPrefill: () -> Unit,
    onOpenExchange: () -> Unit,
    onBack: () -> Unit,
) {
    val prefill = state.entryPrefill
    var confirmLarge by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!form.isDirty) {
            form.date = state.today
            form.category = null
        }
    }

    LaunchedEffect(prefill) {
        if (prefill != null) {
            form.type = EntryType.EXPENSE
            form.amountText = prefill.amountText.orEmpty()
            form.note = prefill.note
            prefill.categoryId?.let { id -> form.category = state.categoriesById[id] }
            prefill.subcategoryName?.let { form.subcategory = it }
            prefill.accountId?.let { id ->
                state.accountsById[id]?.let {
                    form.account = it
                    form.accountTouched = true
                }
            }
            prefill.debtId?.let { id -> form.debt = state.debts.firstOrNull { it.id == id } }
            prefill.date?.let { form.date = it }
        }
    }

    val kind = if (form.type == EntryType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.activeCategories.filter { it.kind == kind }.sortedBy { it.sortOrder }
    val selectedCategory = form.category?.takeIf { it.kind == kind }

    LaunchedEffect(form.type, state.activeAccounts) {
        if (!form.accountTouched || form.account == null) {
            val lastUsed = EntryDefaults.lastUsedAccountId(state.transactions, state.activeAccounts.map { it.id }.toSet(), form.type.transactionKind)
            form.account = lastUsed?.let(state.accountsById::get) ?: form.account ?: state.activeAccounts.firstOrNull()
        }
    }

    val account = form.account
    val currency = account?.currency
    val amount = currency?.let { MoneyFormat.parse(form.amountText, it) }
    val transferTargets = state.activeAccounts.filter { it.currency == currency && it.id != account?.id }
    val selectedTo = form.toAccount?.takeIf { it in transferTargets } ?: transferTargets.firstOrNull()
    val debtCategory = form.type == EntryType.EXPENSE && state.isDebtCategory(selectedCategory?.id)

    LaunchedEffect(form.subcategory, debtCategory) {
        if (debtCategory && form.debt == null && prefill?.debtId == null) state.debtBySubcategoryName(form.subcategory)?.let { form.debt = it }
        if (!debtCategory && prefill?.debtId == null) form.debt = null
    }

    val splitAccount = form.splitAccount?.takeIf { it.id != account?.id }
    val splitAmount = splitAccount?.let { MoneyFormat.parse(form.splitAmountText, it.currency) }
    val splitActive = form.type == EntryType.EXPENSE && form.splitOn && splitAccount != null && splitAmount != null && form.debt == null
    val canSubmit = !state.saving && account != null && amount != null && (!form.splitOn || splitActive || form.type != EntryType.EXPENSE) &&
        (form.type == EntryType.TRANSFER && selectedTo != null || form.type != EntryType.TRANSFER && selectedCategory != null)

    fun submit() {
        val acc = form.account ?: return
        val money = MoneyFormat.parse(form.amountText, acc.currency) ?: return
        onSubmit(
            EntryDraft(
                type = form.type,
                date = form.date,
                accountId = acc.id,
                amount = money,
                toAccountId = selectedTo?.id.takeIf { form.type == EntryType.TRANSFER },
                categoryId = selectedCategory?.id.takeIf { form.type != EntryType.TRANSFER },
                subcategoryName = if (form.type == EntryType.EXPENSE) form.subcategory else "",
                note = form.note,
                debtId = form.debt?.id.takeIf { debtCategory },
                debtEarly = form.debtEarly && form.debt != null && debtCategory,
                fromDraftId = prefill?.draftId,
                splitAccountId = form.splitAccount?.id?.takeIf { splitActive },
                splitAmount = splitAmount?.takeIf { splitActive },
            ),
        )
        form.resetAfterSubmit()
    }

    val missing = when {
        account == null -> "выбери счёт"
        amount == null -> "введи сумму"
        form.type == EntryType.TRANSFER && selectedTo == null -> "выбери, куда перевести"
        form.type != EntryType.TRANSFER && selectedCategory == null -> "выбери категорию"
        else -> null
    }
    val largeThreshold = EntryDefaults.largeAmountThreshold(state.transactions, state.today, form.type.transactionKind)
    fun trySubmit() {
        if (!canSubmit) {
            form.showErrors = true
            return
        }
        val base = amount?.let(state.rates::toBaseOrNull)
        if (base != null && base > largeThreshold) confirmLarge = true else submit()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CenteredLazyColumn(top = 4.dp) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(AppIcons.ArrowLeft, contentDescription = "Назад") }
                    Text("Новая запись", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenExchange) { Text("Обмен валюты") }
                    if (form.isDirty) TextButton(onClick = form::clear) { Text("Очистить") }
                }
            }
            item {
                EntryForm(
                    state = state,
                    form = form,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    amount = amount,
                    transferTargets = transferTargets,
                    selectedTo = selectedTo,
                    debtCategory = debtCategory,
                    onClearPrefill = onClearPrefill,
                    onOpenExchange = onOpenExchange,
                    onSubmit = ::trySubmit,
                )
            }
            item { SectionTitle("Входящие", hint = if (state.drafts.isEmpty()) "заметок нет" else plural(state.drafts.size, "заметка", "заметки", "заметок"), info = Hints.INBOX) }
            item { InboxCard(state.drafts, onAddDraft, onUseDraft, onRemoveDraft) }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
        ) {
        PrimaryButton(
            onClick = ::trySubmit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).height(52.dp),
        ) {
            Text(
                when {
                    state.saving -> "Сохранение…"
                    missing != null -> missing.replaceFirstChar { it.uppercase() }
                    form.type == EntryType.EXPENSE -> "Записать расход ${amount?.let { MoneyFormat.format(it) }.orEmpty()}"
                    form.type == EntryType.INCOME -> "Записать доход ${amount?.let { MoneyFormat.format(it) }.orEmpty()}"
                    else -> "Записать перевод ${amount?.let { MoneyFormat.format(it) }.orEmpty()}"
                },
                style = MaterialTheme.typography.titleSmall,
            )
        }
        }
    }
    if (confirmLarge) {
        AlertDialog(
            onDismissRequest = { confirmLarge = false },
            title = { Text("Необычно большая сумма") },
            text = {
                Text(
                    "${amount?.let { MoneyFormat.format(it) }.orEmpty()} — заметно больше обычных операций. Проверь, нет ли лишних нулей.",
                )
            },
            confirmButton = {
                PrimaryButton(onClick = {
                    confirmLarge = false
                    submit()
                }) { Text("Записать") }
            },
            dismissButton = { TextButton(onClick = { confirmLarge = false }) { Text("Исправить") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryForm(
    state: HomeState,
    form: EntryFormState,
    categories: List<Category>,
    selectedCategory: Category?,
    amount: Money?,
    transferTargets: List<Account>,
    selectedTo: Account?,
    debtCategory: Boolean,
    onClearPrefill: () -> Unit,
    onOpenExchange: () -> Unit,
    onSubmit: () -> Unit,
) {
    val amountTransformation = remember { ThousandsVisualTransformation() }
    val focus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    LaunchedEffect(form.submitCount) { runCatching { focus.requestFocus() } }
    val account = form.account
    val currency = account?.currency
    var showAllCategories by remember { mutableStateOf(false) }
    var addingSubcategory by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val yesterday = state.today.plus(-1, DateTimeUnit.DAY)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.entryPrefill?.let { PrefillBanner(it, onClearPrefill) }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf("Расход", "Доход", "Перевод")
                options.forEachIndexed { index, label ->
                    val entryType = EntryType.entries.getOrNull(index)
                    SegmentedButton(
                        selected = entryType != null && form.type == entryType,
                        onClick = { if (entryType == null) onOpenExchange() else form.type = entryType },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        icon = {},
                    ) { Text(label, maxLines = 1) }
                }
            }

            AccountDropdown(
                label = when (form.type) {
                    EntryType.EXPENSE -> "Со счёта"
                    EntryType.INCOME -> "На счёт"
                    EntryType.TRANSFER -> "Откуда"
                },
                accounts = state.activeAccounts,
                selected = account,
                state = state,
                onSelect = {
                    form.account = it
                    form.accountTouched = true
                },
            )
            if (form.type == EntryType.EXPENSE && account != null) {
                when (account.role) {
                    AccountRole.FUND -> Text("Трата из копилки «${account.name}»: в конверты месяца не попадёт", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.warning)
                    AccountRole.CUSHION -> Text("Трата из подушки: уменьшит отложенное в подушку за месяц", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.warning)
                    AccountRole.LIFE -> Unit
                }
            }

            OutlinedTextField(
                value = form.amountText,
                onValueChange = { form.amountText = ThousandsVisualTransformation.sanitize(it, currency?.minorUnits ?: 2) },
                label = { Text("Сумма") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium,
                visualTransformation = amountTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                trailingIcon = currency?.let { { CurrencyChip(it, state, Modifier.padding(end = 12.dp)) } },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).focusProperties { next = categoryFocus }.submitOnEnter(onSubmit),
            )
            if (amount != null && amount.currency != Currency.BASE) AmountHint(state, amount)
            if (form.type == EntryType.EXPENSE && form.debt == null) {
                if (!form.splitOn) {
                    TextButton(onClick = {
                        form.splitOn = true
                        if (form.splitAccount == null) form.splitAccount = state.activeAccounts.firstOrNull { it.isFund && it.id != account?.id } ?: state.activeAccounts.firstOrNull { it.id != account?.id }
                    }) { Text("+ часть с другого счёта") }
                } else {
                    Text("Вторая часть", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AccountDropdown(
                        label = "Со счёта",
                        accounts = state.activeAccounts.filter { it.id != account?.id },
                        selected = form.splitAccount,
                        state = state,
                        onSelect = { form.splitAccount = it },
                    )
                    OutlinedTextField(
                        value = form.splitAmountText,
                        onValueChange = { form.splitAmountText = ThousandsVisualTransformation.sanitize(it, form.splitAccount?.currency?.minorUnits ?: 2) },
                        label = { Text("Сумма второй части") },
                        singleLine = true,
                        visualTransformation = amountTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        trailingIcon = form.splitAccount?.currency?.let { { CurrencyChip(it, state, Modifier.padding(end = 12.dp)) } },
                        modifier = Modifier.fillMaxWidth().submitOnEnter(onSubmit),
                    )
                    val first = amount?.let(state.rates::toBaseOrNull)
                    val secondPart = form.splitAccount?.let { MoneyFormat.parse(form.splitAmountText, it.currency) }?.let(state.rates::toBaseOrNull)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (first != null && secondPart != null) "Итого ${MoneyFormat.format(first + secondPart, false)}: две операции с одной категорией" else "Запишется двумя операциями с одной категорией и датой",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { form.splitOn = false }) { Text("Убрать") }
                    }
                }
            }

            DateField(date = form.date, today = state.today, onChange = { form.date = it })
            if (form.date != state.today) {
                Text("Дата не сегодня: ${DateFormat.dayMonth(form.date)}, ${DateFormat.weekdayFull(form.date)}", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.warning)
            }
            if (form.type != EntryType.TRANSFER) {
                val choices = categoryChoices(state, categories, form.type)
                val selectedChoice = selectedCategory?.let { cat ->
                    when {
                        form.debt != null -> choices.firstOrNull { it.debt?.id == form.debt?.id }
                        form.subcategory.isNotBlank() -> choices.firstOrNull { it.category.id == cat.id && it.subcategory.equals(form.subcategory.trim(), ignoreCase = true) && it.debt == null }
                            ?: CategoryChoice(cat, form.subcategory.trim(), isNew = true)
                        else -> choices.firstOrNull { it.category.id == cat.id && it.subcategory == null }
                    }
                }
                CategoryPicker(
                    label = if (form.type == EntryType.INCOME) "Категория дохода" else "Категория и подкатегория",
                    choices = choices,
                    selected = selectedChoice,
                    onSelect = { choice ->
                        form.category = choice.category
                        form.subcategory = choice.subcategory.orEmpty()
                        form.debt = choice.debt
                        if (choice.debt == null) form.debtEarly = false
                        choice.debt?.let { d ->
                            if (form.amountText.isBlank() && account?.currency == d.currency) {
                                form.amountText = MoneyFormat.editText(d.plannedPayment)
                            }
                        }
                    },
                    allowCreate = form.type == EntryType.EXPENSE,
                    fieldModifier = Modifier.focusRequester(categoryFocus),
                    onEnterWhenClosed = onSubmit,
                    isError = form.showErrors && selectedCategory == null,
                    supporting = when {
                        form.showErrors && selectedCategory == null -> "выбери категорию"
                        selectedChoice?.isNew == true -> "новая подкатегория появится после записи"
                        else -> envelopeHint(state, selectedCategory, amount)
                    },
                )
                form.debt?.let { d ->
                    EarlyPaymentToggle(
                        checked = form.debtEarly,
                        onChange = { form.debtEarly = it },
                        preview = amount?.let { DebtCalculator.principalFor(d, it, form.debtEarly) },
                        differentCurrency = amount != null && amount.currency != d.currency,
                    )
                }
            }

            if (form.type == EntryType.TRANSFER) {
                AccountDropdown(
                    label = "Куда",
                    accounts = transferTargets,
                    selected = selectedTo,
                    state = state,
                    onSelect = { form.toAccount = it },
                )
                if (transferTargets.isEmpty()) {
                    Text("Нет второго счёта в этой валюте. Другая валюта — это обмен.", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.warning)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryButton(
                        onClick = {
                            val from = form.account
                            form.account = selectedTo
                            form.toAccount = from
                            form.accountTouched = true
                        },
                        enabled = selectedTo != null,
                    ) { Text("Поменять местами") }
                    state.activeAccounts.firstOrNull { it.role == AccountRole.CUSHION && it.currency == currency && it.id != account?.id }?.let { savings ->
                        SecondaryButton(onClick = { form.toAccount = savings }, enabled = savings.id != selectedTo?.id) { Text("В подушку") }
                    }
                    state.activeAccounts.filter { it.role == AccountRole.FUND && it.currency == currency && it.id != account?.id }.forEach { fund ->
                        SecondaryButton(onClick = { form.toAccount = fund }, enabled = fund.id != selectedTo?.id) { Text("В «${fund.name}»") }
                    }
                }
            }

            OutlinedTextField(
                value = form.note,
                onValueChange = { form.note = it },
                label = { Text("Заметка") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().submitOnEnter(onSubmit),
            )
        }
    }
}

@Composable
private fun AmountHint(state: HomeState, amount: Money?) {
    val text = when {
        amount == null -> "В рублях по курсу на сегодня: —"
        amount.currency == Currency.BASE -> "Счёт в рублях, курс не нужен"
        !state.rates.hasRate(amount.currency) -> "Нет курса для ${amount.currency.code}, записать нельзя"
        else -> {
            val perUnit = state.rates.basePerUnit(amount.currency)
            val unit = MoneyFormat.quoteUnit(perUnit)
            "≈ ${MoneyFormat.format(state.rates.toBase(amount))} по курсу ${MoneyFormat.rateText(amount.currency, Currency.BASE, perUnit * unit, unit)}"
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        InfoHint("Пересчёт в рубли", Hints.AMOUNT_BASE)
    }
}

@Composable
private fun PrefillBanner(prefill: EntryPrefill, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    prefill.debtId != null -> "Платёж по кредиту: проверь сумму и счёт"
                    prefill.draftId == null -> "Пропущенная трата на ${prefill.date?.let { DateFormat.dayMonth(it) }.orEmpty()}"
                    else -> "Из Входящих: «${prefill.note}»" + (prefill.amountText?.let { " $it" } ?: "")
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text("Отменить") }
        }
    }
}

@Composable
private fun InboxCard(
    drafts: List<Draft>,
    onAdd: (String) -> Unit,
    onUse: (Draft) -> Unit,
    onRemove: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Быстрая заметка") },
                    placeholder = { Text("такси 450") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    onClick = {
                        onAdd(text)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                ) { Text("В заметки") }
            }
            if (drafts.isEmpty()) {
                Text(
                    "Заметки с виджета и отсюда попадают в этот список. Нажми «Внести», чтобы превратить заметку в операцию.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            drafts.forEach { draft ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(draft.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            DateFormat.dayMonthTime(draft.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onUse(draft) }) { Text("Внести") }
                    IconButton(onClick = { onRemove(draft.id) }) {
                        Icon(AppIcons.Trash, contentDescription = "Удалить заметку")
                    }
                }
            }
        }
    }
}

@Composable
internal fun EarlyPaymentToggle(checked: Boolean, onChange: (Boolean) -> Unit, preview: Money?, differentCurrency: Boolean) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onChange)
            Text("Досрочное погашение", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            when {
                differentCurrency -> "Валюта счёта не совпадает с валютой кредита: остаток кредита не изменится"
                checked -> "Вся сумма уменьшает тело кредита, проценты не вычитаются" + (preview?.let { ". В тело: ${MoneyFormat.format(it, false)}" } ?: "")
                else -> "Обычный платёж: из суммы вычитаются проценты за месяц" + (preview?.let { ", в тело: ${MoneyFormat.format(it, false)}" } ?: "")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Modifier.submitOnEnter(onSubmit: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
        onSubmit()
        true
    } else {
        false
    }
}

private fun categoryChoices(state: HomeState, categories: List<Category>, type: EntryType): List<CategoryChoice> = buildList {
    categories.forEach { category ->
        add(CategoryChoice(category))
        if (type == EntryType.EXPENSE) {
            val debtNames = if (state.isDebtCategory(category.id)) state.openDebts.map { it.name.lowercase() }.toSet() else emptySet()
            if (debtNames.isNotEmpty()) state.openDebts.forEach { add(CategoryChoice(category, it.name, debt = it)) }
            state.activeSubcategories
                .filter { it.categoryId == category.id && it.name.lowercase() !in debtNames }
                .sortedBy { it.name.lowercase() }
                .forEach { add(CategoryChoice(category, it.name)) }
        }
    }
}

private fun envelopeHint(state: HomeState, category: Category?, amount: Money?): String? {
    val budget = category?.let { c -> state.month.envelopes.firstOrNull { it.category.id == c.id } } ?: return null
    if (!budget.hasPlan) return "без плана в этом месяце"
    val after = budget.remainingWhole - (amount?.let { state.rates.toBaseOrNull(it) }?.withoutFraction() ?: Money.zero(Currency.BASE))
    return "потрачено ${MoneyFormat.format(budget.fact, false)} из ${MoneyFormat.format(budget.planned, false)}" +
        if (amount != null) " · после записи ${if (after.isNegative) "перерасход ${MoneyFormat.format(-after, false)}" else "останется ${MoneyFormat.format(after, false)}"}" else ""
}
