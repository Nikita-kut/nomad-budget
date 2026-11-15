package ru.nomadbudget.presentation.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.nomadbudget.core.config.AppConfig
import ru.nomadbudget.presentation.components.PrimaryButton
import ru.nomadbudget.presentation.theme.AppTheme

expect val updatesByReload: Boolean

expect fun reloadApp()

const val RELEASES_URL = "https://github.com/Nikita-kut/nomad-budget/releases/latest"

object UpdateTags {
    const val REQUIRED = "update.required"
    const val BANNER = "update.banner"
}

@Composable
private fun updateAction(): () -> Unit {
    val uri = LocalUriHandler.current
    return { if (updatesByReload) reloadApp() else uri.openUri(UpdateNotice.apkUrl ?: RELEASES_URL) }
}

@Composable
fun UpdateRequiredScreen(min: Version) {
    val update = updateAction()
    Box(modifier = Modifier.fillMaxSize().testTag(UpdateTags.REQUIRED), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.widthIn(max = 420.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Нужна новая версия", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Эта версия (${AppConfig.VERSION_NAME}) больше не работает с данными на сервере, нужна $min или новее. " +
                    if (updatesByReload) "Обнови страницу — данные на месте." else "Скачай новый APK и установи поверх — данные на месте.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(onClick = update) { Text(if (updatesByReload) "Обновить страницу" else "Скачать новую версию") }
        }
    }
}

@Composable
fun UpdateBanner() {
    val available = UpdateNotice.state as? UpdateState.Available ?: return
    val update = updateAction()
    Row(modifier = Modifier.padding(top = 4.dp).testTag(UpdateTags.BANNER), verticalAlignment = Alignment.CenterVertically) {
        Text("Вышла версия ${available.latest}", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.warning)
        TextButton(onClick = update) { Text(if (updatesByReload) "Обновить" else "Скачать", style = MaterialTheme.typography.labelSmall) }
    }
}
