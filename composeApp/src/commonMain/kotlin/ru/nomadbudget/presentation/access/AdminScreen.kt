package ru.nomadbudget.presentation.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.nomadbudget.domain.repository.AccessRepository
import ru.nomadbudget.domain.repository.AccountRow
import ru.nomadbudget.domain.repository.InviteRow
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.components.ConfirmDialog
import ru.nomadbudget.presentation.components.PrimaryButton
import ru.nomadbudget.presentation.components.SectionTitle
import ru.nomadbudget.presentation.more.SubScreenHeader
import ru.nomadbudget.presentation.theme.AppTheme
import kotlin.time.Clock
import kotlin.time.Instant

fun formatMoment(at: Instant?): String {
    if (at == null) return "—"
    val t = at.toLocalDateTime(TimeZone.currentSystemDefault())
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${two(t.day)}.${two(t.month.ordinal + 1)}.${t.year} ${two(t.hour)}:${two(t.minute)}"
}

fun inviteStatus(invite: InviteRow, now: Instant): String = when {
    invite.usedAt != null -> "использовано ${formatMoment(invite.usedAt)}"
    invite.revokedAt != null -> "отозвано"
    invite.expiresAt <= now -> "истекло"
    else -> "ждёт до ${formatMoment(invite.expiresAt)}"
}

object AdminTags {
    const val INVITE_EMAIL = "admin.invite.email"
    const val INVITE_CREATE = "admin.invite.create"
    const val LINK = "admin.link"
}

private sealed interface AdminConfirm {
    data class Reset(val account: AccountRow) : AdminConfirm
    data class Block(val account: AccountRow) : AdminConfirm
}

@Composable
fun AdminScreen(access: AccessRepository, myUserId: String?, onBack: (() -> Unit)?) {
    var invites by remember { mutableStateOf<List<InviteRow>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<AccountRow>>(emptyList()) }
    var email by remember { mutableStateOf("") }
    var link by remember { mutableStateOf<Pair<String, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<AdminConfirm?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun run(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try {
                action()
                reload++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Не получилось"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(reload) {
        try {
            invites = access.invites()
            accounts = access.accounts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить"
        }
    }

    CenteredLazyColumn {
        if (onBack != null) item { SubScreenHeader("Админка", onBack) }
        item {
            Text(
                "Здесь только учётки и приглашения. Финансов других людей приложение тебе не показывает.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        item { SectionTitle("Пригласить", hint = "ссылка на 7 дней, только для этой почты") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Почта человека") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().testTag(AdminTags.INVITE_EMAIL),
                    )
                    PrimaryButton(
                        onClick = {
                            val target = email.trim()
                            run {
                                link = "Приглашение для $target" to access.createInvite(target)
                                email = ""
                            }
                        },
                        enabled = !busy && email.contains('@'),
                        modifier = Modifier.testTag(AdminTags.INVITE_CREATE),
                    ) { Text("Создать приглашение") }
                }
            }
        }
        link?.let { (title, url) ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, fontWeight = FontWeight.Medium)
                        SelectionContainer { Text(url, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(AdminTags.LINK)) }
                        Text("Ссылка показывается один раз. Отправь её человеку лично.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) { Text("Скопировать") }
                            TextButton(onClick = { link = null }) { Text("Скрыть") }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Приглашения", hint = "${invites.size}") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    if (invites.isEmpty()) Text("Приглашений пока нет", modifier = Modifier.padding(16.dp))
                    val now = Clock.System.now()
                    invites.forEachIndexed { index, invite ->
                        if (index > 0) HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(invite.email)
                                Text(inviteStatus(invite, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (invite.usedAt == null && invite.revokedAt == null && invite.expiresAt > now) {
                                TextButton(onClick = { run { access.revokeInvite(invite.id) } }, enabled = !busy) { Text("Отозвать") }
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Учётки", hint = "${accounts.size}") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    accounts.forEachIndexed { index, account ->
                        if (index > 0) HorizontalDivider()
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(account.email ?: "без почты", fontWeight = FontWeight.Medium)
                            Text(
                                "с ${formatMoment(account.createdAt)} · вход ${formatMoment(account.lastSignInAt)}" + if (account.blocked) " · заблокирован" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (account.blocked) AppTheme.colors.bad else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (account.id != myUserId) {
                                Row {
                                    TextButton(onClick = { confirm = AdminConfirm.Reset(account) }, enabled = !busy) { Text("Ссылка для входа") }
                                    if (account.blocked) {
                                        TextButton(onClick = { run { access.setBlocked(account.id, false) } }, enabled = !busy) { Text("Разблокировать") }
                                    } else {
                                        TextButton(onClick = { confirm = AdminConfirm.Block(account) }, enabled = !busy) { Text("Заблокировать", color = AppTheme.colors.bad) }
                                    }
                                }
                            } else {
                                Text("это ты", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    when (val current = confirm) {
        is AdminConfirm.Reset -> ConfirmDialog(
            title = "Ссылка для входа",
            text = "Ссылка впустит в учётку ${current.account.email} без пароля, после чего человек задаст новый. Кто её откроет первым — тот и войдёт, поэтому отправь её только самому человеку. Он увидит в своих настройках, что ты создал ссылку.",
            confirmText = "Создать ссылку",
            onDismiss = { confirm = null },
        ) {
            confirm = null
            run { link = "Ссылка для входа ${current.account.email}" to access.resetLink(current.account.id) }
        }
        is AdminConfirm.Block -> ConfirmDialog(
            title = "Заблокировать ${current.account.email}?",
            text = "Человек сразу потеряет доступ к своим данным и не сможет войти, бот перестанет ему писать. Данные не удаляются, разблокировать можно в любой момент. Он увидит запись о блокировке после разблокировки.",
            confirmText = "Заблокировать",
            onDismiss = { confirm = null },
        ) {
            confirm = null
            run { access.setBlocked(current.account.id, true) }
        }
        null -> Unit
    }
}
