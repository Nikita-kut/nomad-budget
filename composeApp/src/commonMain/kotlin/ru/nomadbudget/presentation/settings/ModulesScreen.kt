package ru.nomadbudget.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.nomadbudget.features.AppModule
import ru.nomadbudget.features.Feature
import ru.nomadbudget.features.FeatureState
import ru.nomadbudget.features.enabledModules
import ru.nomadbudget.features.withModule
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.ConfirmDialog
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.more.SubScreenHeader

fun moduleData(module: AppModule, state: HomeState): String? = when (module) {
    AppModule.DEBTS -> state.openDebts.takeIf { it.isNotEmpty() }?.let { "открытых кредитов: ${it.size}" }
    AppModule.CUSHION -> state.cushionAccounts.takeIf { it.isNotEmpty() }?.let { "счетов подушки: ${it.size}" }
    AppModule.FUNDS -> state.fundAccounts.takeIf { it.isNotEmpty() }?.let { "копилок: ${it.size}" }
    AppModule.CAPITAL -> state.capitalGoals.takeIf { it.isNotEmpty() }?.let { "целей: ${it.size}" }
    AppModule.CHARTS -> null
    AppModule.CURRENCIES -> state.foreignCurrencies.takeIf { it.isNotEmpty() }?.let { "валют кроме базовой: ${it.size}" }
    AppModule.BANK_WATCH -> state.bankWatch.watch?.let { "слежение за курсом включено" }
    AppModule.INBOX -> state.drafts.takeIf { it.isNotEmpty() }?.let { "заметок во «Входящих»: ${it.size}" }
}

private val PLAN_MODULES = setOf(AppModule.DEBTS, AppModule.FUNDS, AppModule.CUSHION)

private fun requiresClosureOf(module: AppModule): Set<AppModule> = ru.nomadbudget.features.requiresClosure(module)

object ModulesTags {
    fun module(module: AppModule) = "modules.${module.key}"
    fun block(feature: Feature) = "modules.block.${feature.key}"
}

@Composable
fun ModulesScreen(
    state: HomeState,
    features: FeatureState,
    onSave: (modulesOff: Set<String>, blocksOff: Set<String>) -> Unit,
    onBack: (() -> Unit)?,
) {
    val modulesOff = state.settings.modulesOff
    val blocksOff = state.settings.blocksOff
    val enabled = enabledModules(modulesOff)
    var confirmOff by remember { mutableStateOf<Pair<AppModule, String>?>(null) }
    val available = AppModule.entries.filter { module -> Feature.entries.any { it.module == module && features.stageAllows(it) } }

    CenteredLazyColumn {
        if (onBack != null) item { SubScreenHeader("Модули", onBack) }
        item {
            Text(
                "Выключенный модуль только прячется: данные остаются, цифры месяца и плана не меняются. Включить обратно можно в любой момент.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        available.forEach { module ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val on = module in enabled
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(module.title, fontWeight = FontWeight.Medium)
                                Text(module.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (module.requires.isNotEmpty()) {
                                    Text("нужен модуль ${module.requires.joinToString { it.title }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = on,
                                onCheckedChange = { turnOn ->
                                    val data = (listOf(module) + AppModule.entries.filter { module in requiresClosureOf(it) && it in enabled })
                                        .mapNotNull { m -> moduleData(m, state)?.let { "${m.title}: $it" } }
                                        .joinToString("; ")
                                        .ifEmpty { null }
                                    if (!turnOn && data != null) confirmOff = module to data else onSave(withModule(modulesOff, module, turnOn), blocksOff)
                                },
                                modifier = Modifier.testTag(ModulesTags.module(module)),
                            )
                        }
                        val blocks = Feature.entries.filter { it.module == module && features.stageAllows(it) }
                        if (on && blocks.size > 1) {
                            HorizontalDivider()
                            blocks.forEach { block ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = block.key !in blocksOff,
                                        onCheckedChange = { show -> onSave(modulesOff, if (show) blocksOff - block.key else blocksOff + block.key) },
                                        modifier = Modifier.testTag(ModulesTags.block(block)),
                                    )
                                    Text(block.title, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmOff?.let { (module, data) ->
        ConfirmDialog(
            title = "Выключить «${module.title}»?",
            text = "В модуле есть данные ($data). Они не удалятся и продолжат участвовать в расчётах" +
                (if (module in PLAN_MODULES) ": в плане появится строка «Скрытые модули». " else ". ") +
                (AppModule.entries.filter { module in it.requires }.takeIf { it.isNotEmpty() }?.let { "Вместе с ним выключится: ${it.joinToString { m -> m.title }}." } ?: ""),
            confirmText = "Выключить",
            onDismiss = { confirmOff = null },
        ) {
            confirmOff = null
            onSave(withModule(modulesOff, module, false), blocksOff)
        }
    }
}
