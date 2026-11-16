package ru.nomadbudget.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun GroupTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp),
    )
}

@Composable
fun SectionTitle(title: String, hint: String? = null, modifier: Modifier = Modifier, info: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            info?.let { InfoHint(title = title, text = it) }
        }
        hint?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InfoHint(title: String, text: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(INFO_HINT_SIZE)) {
        Icon(
            AppIcons.Info,
            contentDescription = "Что это",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { HintText(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Понятно") } },
        )
    }
}

enum class InfoTone { IN, OUT, NEUTRAL }

data class InfoLine(val label: String, val amount: String, val tone: InfoTone = InfoTone.NEUTRAL, val detail: String? = null)

@Composable
fun InfoDialog(title: String, lines: List<InfoLine>, total: InfoLine?, note: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    lines.forEachIndexed { index, line ->
                        if (index > 0) HorizontalDivider()
                        InfoLineRow(line, bold = false)
                    }
                    total?.let {
                        HorizontalDivider(thickness = 2.dp)
                        InfoLineRow(it, bold = true)
                    }
                }
                note?.let { HintText(it, lead = false) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
    )
}

@Composable
private fun InfoLineRow(line: InfoLine, bold: Boolean) {
    val colors = AppTheme.colors
    val amountColor = when (line.tone) {
        InfoTone.IN -> colors.good
        InfoTone.OUT -> colors.bad
        InfoTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                line.label,
                modifier = Modifier.weight(1f),
                style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                line.amount,
                style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
            )
        }
        line.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun HintText(text: String, lead: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val split = if (lead) LEAD_END.find(text)?.range?.last?.plus(1) else null
    val first = split?.let { text.substring(0, it).trim() }
    val rest = split?.let { text.substring(it).trim() } ?: text
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        first?.let { Text(highlighted(it, scheme.primary), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface) }
        if (rest.isNotEmpty()) Text(highlighted(rest, scheme.primary), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}

private val LEAD_END = Regex("""[.!?](?=\s+[А-ЯЁA-Z«])""")
private val TERM = Regex("""«[^»]{1,40}»""")
private val NUMBER = Regex("""(?<![A-Za-zА-Яа-яЁё\d])[−+-]?\d[\d  ]*(?:[.,]\d+)?(?:\s?(?:–|-)\s?\d+)?\s?(?:%|₽|\$|мес\.?|месяц[а-я]*)?""")

private fun highlighted(text: String, accent: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        TERM.findAll(text).forEach { addStyle(androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.Medium), it.range.first, it.range.last + 1) }
        NUMBER.findAll(text).filter { it.value.isNotBlank() }.forEach { addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1) }
    }

@Composable
fun TagChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun currencyColor(currency: Currency, state: HomeState): Color = AppTheme.colors.currency(currency, state.base, state.currencyOrdinal(currency))

@Composable
fun CurrencyChip(currency: Currency, state: HomeState, modifier: Modifier = Modifier) {
    val color = currencyColor(currency, state)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(currency.code, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CurrencyAmount(text: String, style: TextStyle, fontWeight: FontWeight? = null, modifier: Modifier = Modifier) {
    Text(text, style = style, fontWeight = fontWeight, color = amountColor(text), modifier = modifier, maxLines = 1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDropdown(
    label: String,
    accounts: List<Account>,
    selected: Account?,
    state: HomeState,
    onSelect: (Account) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedBalance = selected?.let { state.balances[it.id] }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = selected?.let { account -> { CurrencyChip(account.currency, state) } },
            suffix = if (selected?.isSavings == true) ({ SavingsTag() }) else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = selectedBalance?.let { balance ->
                {
                    Text(
                        "сейчас ${MoneyFormat.format(balance)}",
                        color = if (balance.isNegative) AppTheme.colors.bad else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CurrencyChip(account.currency, state)
                            AccountName(account, modifier = Modifier.weight(1f))
                            state.balances[account.id]?.let { balance ->
                                Text(
                                    MoneyFormat.format(balance),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (balance.isNegative) AppTheme.colors.bad else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(account)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String,
    items: List<T>,
    selected: T?,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(itemLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun SavingsTag(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            "резерв",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
        )
    }
}

@Composable
fun AccountName(
    account: Account?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            account?.name ?: "?",
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (account?.isSavings == true) SavingsTag()
        if (account?.isFund == true) FundTag()
    }
}

@Composable
fun AccountsLine(
    state: HomeState,
    fromId: String,
    toId: String? = null,
    prefix: String? = null,
    note: String? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight: FontWeight? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        prefix?.let { Text("$it ·", style = style, color = color, fontWeight = fontWeight, maxLines = 1) }
        AccountName(state.accountsById[fromId], style = style, color = color, fontWeight = fontWeight)
        toId?.let { id ->
            ArrowGlyph(color)
            AccountName(state.accountsById[id], style = style, color = color, fontWeight = fontWeight)
        }
        note?.takeIf { it.isNotBlank() }?.let {
            Text(
                "· $it",
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
fun ArrowGlyph(color: Color = MaterialTheme.colorScheme.onSurfaceVariant, size: androidx.compose.ui.unit.Dp = 14.dp) {
    Icon(AppIcons.ArrowRight, contentDescription = null, tint = color, modifier = Modifier.size(size))
}

@Composable
fun amountColor(text: String): Color =
    if (text.startsWith("−") || text.startsWith("-")) AppTheme.colors.bad else MaterialTheme.colorScheme.onSurface

enum class PillTone { GOOD, WARNING, BAD, NEUTRAL }

@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val fg = when (tone) {
        PillTone.GOOD -> colors.good
        PillTone.WARNING -> colors.warning
        PillTone.BAD -> colors.bad
        PillTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(fg.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun FundTag(modifier: Modifier = Modifier) {
    Text(
        "копилка",
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.warning,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(AppTheme.colors.warning.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

val INFO_HINT_SIZE = 32.dp
