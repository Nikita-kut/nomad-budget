package ru.nomadbudget.presentation.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.nomadbudget.domain.repository.AccessRepository
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.presentation.components.PrimaryButton

const val MIN_PASSWORD = 10
private const val MAX_PASSWORD_BYTES = 72

fun passwordProblem(password: String, repeat: String): String? = when {
    password.length < MIN_PASSWORD -> "Пароль — не короче $MIN_PASSWORD символов"
    password.encodeToByteArray().size > MAX_PASSWORD_BYTES -> "Пароль слишком длинный"
    repeat.isNotEmpty() && repeat != password -> "Пароли не совпадают"
    else -> null
}

object AccessTags {
    const val REGISTER_EMAIL = "register.email"
    const val REGISTER_PASSWORD = "register.password"
    const val REGISTER_REPEAT = "register.repeat"
    const val REGISTER_SUBMIT = "register.submit"
    const val ERROR = "access.error"
    const val RESET_OPEN = "reset.open"
    const val RESET_PASSWORD = "reset.password"
    const val RESET_REPEAT = "reset.repeat"
    const val RESET_SUBMIT = "reset.submit"
}

@Composable
private fun AccessFrame(title: String, subtitle: String, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun PasswordField(value: String, label: String, tag: String, isError: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun ErrorText(error: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(AccessTags.ERROR)) }
}

@Composable
fun RegisterScreen(code: String, access: AccessRepository, auth: AuthRepository, onCancel: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val problem = passwordProblem(password, repeat)

    AccessFrame(
        title = "Регистрация",
        subtitle = "Тебя пригласили в Nomad Budget. Укажи почту, на которую пришло приглашение, и придумай пароль.",
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Почта") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag(AccessTags.REGISTER_EMAIL),
        )
        PasswordField(password, "Пароль", AccessTags.REGISTER_PASSWORD) { password = it }
        PasswordField(repeat, "Ещё раз", AccessTags.REGISTER_REPEAT, isError = repeat.isNotEmpty() && repeat != password) { repeat = it }
        Text(
            if (password.isEmpty()) "Не короче $MIN_PASSWORD символов." else problem ?: "Пароль подходит.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ErrorText(error)
        PrimaryButton(
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        access.redeemInvite(code, email.trim(), password)
                        auth.signIn(email.trim(), password)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "Не получилось, попробуй ещё раз"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && email.isNotBlank() && problem == null && repeat == password,
            modifier = Modifier.fillMaxWidth().testTag(AccessTags.REGISTER_SUBMIT),
        ) { Text(if (busy) "Создаю учётку…" else "Зарегистрироваться") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("У меня уже есть учётка") }
    }
}

@Composable
fun ResetPasswordScreen(tokenHash: String, access: AccessRepository, auth: AuthRepository, onDone: () -> Unit) {
    var opened by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val problem = passwordProblem(password, repeat)

    fun run(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message?.takeIf { opened } ?: "Ссылка не сработала: она одноразовая и действует час. Попроси владельца прислать новую."
            } finally {
                busy = false
            }
        }
    }

    if (!opened) {
        AccessFrame(
            title = "Новый пароль",
            subtitle = "Это ссылка для входа без пароля от владельца приложения. Она сработает один раз. Нажми кнопку, потом придумай новый пароль.",
        ) {
            ErrorText(error)
            PrimaryButton(
                onClick = { run { access.openResetLink(tokenHash); opened = true } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag(AccessTags.RESET_OPEN),
            ) { Text(if (busy) "Проверяю ссылку…" else "Задать новый пароль") }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
    } else {
        AccessFrame(title = "Новый пароль", subtitle = "Ты вошёл. Придумай новый пароль — старый перестанет работать.") {
            PasswordField(password, "Новый пароль", AccessTags.RESET_PASSWORD) { password = it }
            PasswordField(repeat, "Ещё раз", AccessTags.RESET_REPEAT, isError = repeat.isNotEmpty() && repeat != password) { repeat = it }
            Text(problem ?: "Пароль подходит.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ErrorText(error)
            PrimaryButton(
                onClick = { run { auth.changePassword(password); onDone() } },
                enabled = !busy && problem == null && repeat == password,
                modifier = Modifier.fillMaxWidth().testTag(AccessTags.RESET_SUBMIT),
            ) { Text(if (busy) "Сохраняю…" else "Сохранить пароль") }
        }
    }
}
