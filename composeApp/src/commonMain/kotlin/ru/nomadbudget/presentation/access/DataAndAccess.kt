package ru.nomadbudget.presentation.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import ru.nomadbudget.domain.repository.AccessRepository
import ru.nomadbudget.domain.repository.OwnerAction

const val PRIVACY_TEXT = "Твои данные хранятся на сервере владельца приложения (Supabase). " +
    "Приложение не показывает владельцу твои счета, операции и планы: в админке видны только почта, дата регистрации, последний вход и блокировка. " +
    "Технически у владельца сервера есть доступ к базе и к ночным резервным копиям. Копии зашифрованы и хранятся 30 дней. " +
    "Если ты забудешь пароль, владелец может создать ссылку для входа — это будет записано ниже. " +
    "Выгрузить операции в CSV можно на экране «Ещё», удалить учётку со всеми данными — здесь, в настройках. После удаления данные исчезнут из резервных копий через 30 дней. " +
    "Заблокировать вход владелец может, удалить твои данные — нет."

fun ownerActionText(action: String): String = when (action) {
    "invite_redeemed" -> "Учётка создана по приглашению"
    "reset_link_created" -> "Владелец создал ссылку для входа без пароля"
    "blocked" -> "Владелец заблокировал вход"
    "unblocked" -> "Владелец разблокировал вход"
    else -> "Действие владельца"
}

@Composable
fun DataAndAccessCard(access: AccessRepository, onOpenAdmin: () -> Unit) {
    var actions by remember { mutableStateOf<List<OwnerAction>?>(null) }
    var admin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            admin = access.isAdmin()
            actions = access.ownerActions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            actions = emptyList()
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(PRIVACY_TEXT, style = MaterialTheme.typography.bodySmall)
            Text("Что владелец делал с твоей учёткой", fontWeight = FontWeight.Medium)
            when {
                actions == null -> Text("Загружаю…", style = MaterialTheme.typography.bodySmall)
                actions.orEmpty().isEmpty() -> Text("Ничего", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> actions.orEmpty().forEach {
                    Text("${formatMoment(it.at)} — ${ownerActionText(it.action)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (admin) TextButton(onClick = onOpenAdmin) { Text("Открыть админку") }
        }
    }
}
