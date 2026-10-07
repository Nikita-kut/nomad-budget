package ru.nomadbudget.presentation.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import ru.nomadbudget.domain.model.AccountKind
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.CategoryKind
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Debt
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.presentation.components.AccountDropdown
import ru.nomadbudget.presentation.components.CurrencyChip
import ru.nomadbudget.presentation.components.DateField
import ru.nomadbudget.presentation.components.Dropdown
import ru.nomadbudget.presentation.components.EmptyHint
import ru.nomadbudget.presentation.components.Hints
import ru.nomadbudget.presentation.components.InfoHint
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.components.TagChip
import ru.nomadbudget.presentation.format.DateFormat
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.format.ThousandsVisualTransformation
import ru.nomadbudget.presentation.home.EntryDraft
import ru.nomadbudget.presentation.home.EntryType
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.home.JournalDay
import ru.nomadbudget.presentation.theme.AppTheme
import ru.nomadbudget.domain.model.Draft
import ru.nomadbudget.presentation.home.EntryPrefill
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import ru.nomadbudget.domain.model.DebtCalculator
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.presentation.components.AccountsLine
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import ru.nomadbudget.domain.model.sumIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import ru.nomadbudget.domain.model.CorrectionCategory
import ru.nomadbudget.presentation.more.SubScreenHeader
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

private const val MAX_SUGGESTIONS = 8
private const val MAX_TEMPLATES = 6
private const val MAX_QUICK_CATEGORIES = 8
private const val RECENT_DAYS = 90

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

    val isDirty: Boolean get() = amountText.isNotBlank() || note.isNotBlank() || subcategory.isNotBlank()

    fun resetAfterSubmit() {
        amountText = ""
        subcategory = ""
        note = ""
        debt = null
        debtEarly = false
    }

    fun clear() {
        resetAfterSubmit()
        type = EntryType.EXPENSE
        category = null
        accountTouched = false
        toAccount = null
    }
}

private data class EntryTemplate(val label: String, val expense: Transaction.Expense)

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

    LaunchedEffect(prefill) {
        if (prefill != null) {
            form.type = EntryType.EXPENSE
            form.amountText = prefill.amountText.orEmpty()
            form.note = prefill.note
        }
    }

    val kind = if (form.type == EntryType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.activeCategories.filter { it.kind == kind }
    val selectedCategory = form.category?.takeIf { it.kind == kind } ?: rankedCategories(state, kind).firstOrNull() ?: categories.firstOrNull()

    LaunchedEffect(form.type, selectedCategory?.id, state.activeAccounts) {
        if (!form.accountTouched || form.account == null) {
            form.account = preferredAccount(state, form.type, selectedCategory?.id) ?: form.account ?: state.activeAccounts.firstOrNull()
        }
    }

    val account = form.account
    val currency = account?.currency
    val amount = currency?.let { MoneyFormat.parse(form.amountText, it) }
    val transferTargets = state.activeAccounts.filter { it.currency == currency && it.id != account?.id }
    val selectedTo = form.toAccount?.takeIf { it in transferTargets } ?: transferTargets.firstOrNull()
    val debtCategory = form.type == EntryType.EXPENSE && state.isDebtCategory(selectedCategory?.id)

    LaunchedEffect(form.subcategory, debtCategory) {
        if (debtCategory && form.debt == null) state.debtBySubcategoryName(form.subcategory)?.let { form.debt = it }
        if (!debtCategory) form.debt = null
    }

    val canSubmit = !state.saving && account != null && amount != null &&
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
            ),
        )
        form.resetAfterSubmit()
    }

    val largeThreshold = largeAmountThreshold(state, form.type)
    fun trySubmit() {
        if (!canSubmit) return
        val base = amount?.let(state.rates::toBaseOrNull)
        if (base != null && base > largeThreshold) confirmLarge = true else submit()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubScreenHeader("Новая запись", onBack)
                    Spacer(modifier = Modifier.weight(1f))
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
            item { SectionTitle("Входящие", hint = if (state.drafts.isEmpty()) "заметок нет" else "${state.drafts.size} заметок", info = Hints.INBOX) }
            item { InboxCard(state.drafts, onAddDraft, onUseDraft, onRemoveDraft) }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
        ) {
        Button(
            onClick = ::trySubmit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).height(52.dp),
        ) {
            Text(
                when {
                    state.saving -> "Сохраняем…"
                    amount == null -> "Введи сумму"
                    form.type == EntryType.EXPENSE -> "Записать расход ${MoneyFormat.format(amount)}"
                    form.type == EntryType.INCOME -> "Записать доход ${MoneyFormat.format(amount)}"
                    else -> "Записать перевод ${MoneyFormat.format(amount)}"
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
                Button(onClick = {
                    confirmLarge = false
                    submit()
                }) { Text("Записать") }
            },
            dismissButton = { TextButton(onClick = { confirmLarge = false }) { Text("Исправить") } },
        )
    }
}

private fun rankedCategories(state: HomeState, kind: CategoryKind): List<Category> {
    val since = state.today.plus(-RECENT_DAYS, DateTimeUnit.DAY)
    val counts = state.transactions
        .filter { it.date >= since }
        .mapNotNull {
            when (it) {
                is Transaction.Expense -> it.categoryId
                is Transaction.Income -> it.categoryId
                is Transaction.Transfer, is Transaction.Exchange -> null
            }
        }
        .groupingBy { it }
        .eachCount()
    return state.activeCategories
        .filter { it.kind == kind && it.name != CorrectionCategory.NAME }
        .sortedWith(compareByDescending<Category> { counts[it.id] ?: 0 }.thenBy { it.sortOrder })
}

private fun preferredAccount(state: HomeState, type: EntryType, categoryId: String?): Account? {
    val active = state.activeAccounts.associateBy { it.id }
    val sorted = state.transactions.sortedByDescending { it.date }
    val accountId = when (type) {
        EntryType.EXPENSE -> sorted.filterIsInstance<Transaction.Expense>().let { list ->
            list.firstOrNull { it.categoryId == categoryId && it.accountId in active }?.accountId ?: list.firstOrNull { it.accountId in active }?.accountId
        }
        EntryType.INCOME -> sorted.filterIsInstance<Transaction.Income>().let { list ->
            list.firstOrNull { it.categoryId == categoryId && it.accountId in active }?.accountId ?: list.firstOrNull { it.accountId in active }?.accountId
        }
        EntryType.TRANSFER -> sorted.filterIsInstance<Transaction.Transfer>().firstOrNull { it.fromAccountId in active }?.fromAccountId
    }
    return accountId?.let(active::get)
}

private fun recentTemplates(state: HomeState): List<EntryTemplate> =
    state.transactions
        .filterIsInstance<Transaction.Expense>()
        .filter { it.debtId == null && state.accountsById[it.accountId]?.isArchived == false }
        .sortedByDescending { it.date }
        .distinctBy { Triple(it.categoryId, it.subcategoryId, it.accountId) }
        .take(MAX_TEMPLATES)
        .map { expense ->
            val name = state.subcategoryName(expense.subcategoryId) ?: state.categoryName(expense.categoryId)
            EntryTemplate("$name ${MoneyFormat.format(expense.amount, false)}", expense)
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
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val account = form.account
    val currency = account?.currency
    val suggestions = selectedCategory?.let { cat ->
        state.subcategories
            .filter { it.categoryId == cat.id && (form.subcategory.isBlank() || it.name.contains(form.subcategory, ignoreCase = true)) }
            .map { it.name }
            .distinct()
            .take(MAX_SUGGESTIONS)
    }.orEmpty()
    val kind = if (form.type == EntryType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val quickCategories = rankedCategories(state, kind).take(MAX_QUICK_CATEGORIES).let { top ->
        if (selectedCategory != null && selectedCategory !in top) top + selectedCategory else top
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.entryPrefill?.let { PrefillBanner(it, onClearPrefill) }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf("Расход", "Доход", "Перевод", "Обмен")
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val from = form.account
                            form.account = selectedTo
                            form.toAccount = from
                            form.accountTouched = true
                        },
                        enabled = selectedTo != null,
                    ) { Text("Поменять местами") }
                    state.activeAccounts.firstOrNull { it.isSavings && it.currency == currency && it.id != account?.id }?.let { savings ->
                        OutlinedButton(onClick = { form.toAccount = savings }, enabled = savings.id != selectedTo?.id) { Text("В накопления") }
                    }
                }
            }

            OutlinedTextField(
                value = form.amountText,
                onValueChange = { form.amountText = ThousandsVisualTransformation.sanitize(it, currency?.minorUnits ?: 2) },
                label = { Text("Сумма") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                visualTransformation = amountTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                trailingIcon = currency?.let { { CurrencyChip(it, state, Modifier.padding(end = 12.dp)) } },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).submitOnEnter(onSubmit),
            )
            AmountHint(state, amount)

            if (form.type == EntryType.EXPENSE) {
                val templates = recentTemplates(state)
                if (templates.isNotEmpty()) {
                    Text("Недавние", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        templates.forEach { template ->
                            TagChip(text = template.label, onClick = {
                                val expense = template.expense
                                form.category = state.categoriesById[expense.categoryId]
                                form.subcategory = state.subcategoryName(expense.subcategoryId).orEmpty()
                                form.account = state.accountsById[expense.accountId]
                                form.accountTouched = true
                                form.amountText = MoneyFormat.format(expense.amount).filter { it.isDigit() || it == ',' }
                            })
                        }
                    }
                }
            }

            if (form.type != EntryType.TRANSFER) {
                Text("Категория", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickCategories.forEach { category ->
                        TagChip(text = category.name, selected = category.id == selectedCategory?.id, onClick = { form.category = category })
                    }
                }
                if (categories.size > quickCategories.size) {
                    Dropdown(
                        label = "Все категории",
                        items = categories,
                        selected = selectedCategory,
                        itemLabel = Category::name,
                        onSelect = { form.category = it },
                    )
                }
            }

            if (form.type == EntryType.EXPENSE) {
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { name ->
                            TagChip(text = name, selected = name.equals(form.subcategory, ignoreCase = true), onClick = { form.subcategory = name })
                        }
                    }
                }
                OutlinedTextField(
                    value = form.subcategory,
                    onValueChange = { form.subcategory = it },
                    label = { Text("Подкатегория") },
                    placeholder = { Text("выбери выше или впиши новую") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (debtCategory) {
                Dropdown(
                    label = "Кредит, если это платёж по нему",
                    items = listOf<Debt?>(null) + state.openDebts,
                    selected = form.debt,
                    itemLabel = { it?.let { d -> "${d.name} · ${MoneyFormat.format(d.monthlyPayment, false)}" } ?: "не платёж по кредиту" },
                    onSelect = { selected ->
                        form.debt = selected
                        if (selected == null) form.debtEarly = false
                        selected?.let { d ->
                            form.subcategory = d.name
                            if (form.amountText.isBlank() && account?.currency == d.currency && !form.debtEarly) {
                                form.amountText = (d.monthlyPayment.minor / d.currency.minorFactor).toString()
                            }
                        }
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

            DateField(date = form.date, today = state.today, onChange = { form.date = it })

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
private fun AmountHint(state: HomeState, amount: ru.nomadbudget.domain.model.Money?) {
    val text = when {
        amount == null -> "В рублях по курсу на сегодня: —"
        amount.currency == Currency.BASE -> "Счёт в рублях, курс не нужен"
        !state.rates.hasRate(amount.currency) -> "Нет курса для ${amount.currency.code}, записать нельзя"
        else -> "≈ ${MoneyFormat.format(state.rates.toBase(amount))} по курсу ${MoneyFormat.formatRate(state.rates.basePerUnit(amount.currency))} ₽ за 1 ${amount.currency.code}"
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
                "Из Входящих: «${prefill.note}»" + (prefill.amountText?.let { " $it" } ?: ""),
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
                    placeholder = { Text("кофе 60к") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = {
                        onAdd(text)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                ) { Text("В список") }
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
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить заметку")
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

internal fun largeAmountThreshold(state: HomeState, type: EntryType): Money {
    val since = state.today.plus(-LARGE_LOOKBACK_DAYS, DateTimeUnit.DAY)
    val largest = state.transactions
        .filter { it.date >= since }
        .filter {
            when (type) {
                EntryType.EXPENSE -> it is Transaction.Expense
                EntryType.INCOME -> it is Transaction.Income
                EntryType.TRANSFER -> it is Transaction.Transfer
            }
        }
        .maxOfOrNull { it.amountBase.minor } ?: 0L
    return Money.rub(maxOf(largest * LARGE_FACTOR, LARGE_FLOOR_MINOR))
}

private const val LARGE_LOOKBACK_DAYS = 180
private const val LARGE_FACTOR = 5L
private const val LARGE_FLOOR_MINOR = 10_000_000L
