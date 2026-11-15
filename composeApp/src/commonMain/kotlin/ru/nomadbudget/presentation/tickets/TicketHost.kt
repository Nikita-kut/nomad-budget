package ru.nomadbudget.presentation.tickets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.nomadbudget.core.config.AppConfig
import ru.nomadbudget.domain.repository.KeyValueStore
import ru.nomadbudget.domain.repository.TicketDraft
import ru.nomadbudget.domain.repository.TicketKind
import ru.nomadbudget.domain.repository.TicketRepository
import ru.nomadbudget.platformName
import ru.nomadbudget.presentation.components.PrimaryButton
import ru.nomadbudget.presentation.format.AmountMask

const val TICKET_BUTTON_KEY = "ticket_button_hidden"
const val MAX_TICKET_BODY = 2000

object TicketTags {
    const val BUTTON = "ticket.button"
    const val MARK_LAYER = "ticket.mark"
    const val NO_MARK = "ticket.nomark"
    const val BODY = "ticket.body"
    const val SEND = "ticket.send"
    const val ATTACH = "ticket.attach"
    const val SHOW_AMOUNTS = "ticket.amounts"
    const val BLUR = "ticket.blur"
    const val RESULT = "ticket.result"
}

class TicketLauncher {
    var requested by mutableStateOf(false)
    fun open() {
        requested = true
    }
}

private sealed interface Step {
    data object Idle : Step
    data object Marking : Step
    data object Capturing : Step
    data class Form(val raw: ImageBitmap?, val mark: Offset?) : Step
}

fun techInfo(screen: String?, size: IntSize): Map<String, String> = buildMap {
    put("platform", platformName())
    put("version", AppConfig.VERSION_NAME)
    put("env", AppConfig.ENVIRONMENT)
    screen?.let { put("screen", it) }
    put("window", "${size.width}x${size.height}")
}

@Composable
fun TicketHost(
    screen: String?,
    repository: TicketRepository,
    store: KeyValueStore,
    launcher: TicketLauncher,
    enabled: Boolean,
    buttonPadding: androidx.compose.foundation.layout.PaddingValues,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    var step by remember { mutableStateOf<Step>(Step.Idle) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val buttonHidden = store.get(TICKET_BUTTON_KEY) == "1"

    suspend fun capture(showAmounts: Boolean): ImageBitmap? {
        val before = AmountMask.hidden
        return try {
            AmountMask.hidden = !showAmounts
            withFrameNanos { }
            withFrameNanos { }
            layer.toImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            AmountMask.hidden = before
        }
    }

    fun captureAt(mark: Offset?) {
        scope.launch {
            step = Step.Capturing
            val raw = capture(showAmounts = false)
            step = Step.Form(raw, mark)
        }
    }

    if (launcher.requested && step == Step.Idle) {
        launcher.requested = false
        if (enabled) step = Step.Marking
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { size = it }) {
        Box(
            modifier = Modifier.fillMaxSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) { content() }

        if (enabled && step == Step.Idle && !buttonHidden) {
            SmallFloatingActionButton(
                onClick = { step = Step.Marking },
                modifier = Modifier.align(Alignment.BottomEnd).padding(buttonPadding).testTag(TicketTags.BUTTON),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) { Text("?", fontWeight = FontWeight.Bold) }
        }

        if (step == Step.Marking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .testTag(TicketTags.MARK_LAYER)
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            if (size.width > 0 && size.height > 0) {
                                captureAt(Offset(tap.x / size.width, tap.y / size.height))
                            }
                        }
                    },
            ) {
                Card(modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Нажми на место, где проблема", fontWeight = FontWeight.Medium)
                        Row {
                            TextButton(onClick = { captureAt(null) }, modifier = Modifier.testTag(TicketTags.NO_MARK)) { Text("Без отметки") }
                            TextButton(onClick = { step = Step.Idle }) { Text("Отмена") }
                        }
                    }
                }
            }
        }

        val form = step as? Step.Form
        if (form != null) {
            TicketDialog(
                raw = form.raw,
                mark = form.mark,
                tech = techInfo(screen, size),
                recapture = { show -> capture(show) },
                onSend = { draft -> repository.create(draft.copy(screen = screen)) },
                onClose = { step = Step.Idle },
            )
        }
    }
}

@Composable
fun TicketDialog(
    raw: ImageBitmap?,
    mark: Offset?,
    tech: Map<String, String>,
    recapture: suspend (Boolean) -> ImageBitmap?,
    onSend: suspend (TicketDraft) -> Long,
    onClose: () -> Unit,
) {
    var kind by remember { mutableStateOf(TicketKind.BUG) }
    var body by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf(raw != null) }
    var showAmounts by remember { mutableStateOf(false) }
    var blur by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(raw) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val preview = remember(source, blur, mark) { source?.let { composeScreenshot(it, mark, blur) } }

    if (sent != null) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Обращение №$sent отправлено") },
            text = { Text("Статус — в «Ещё» → «Мои обращения».", modifier = Modifier.testTag(TicketTags.RESULT)) },
            confirmButton = { TextButton(onClick = onClose) { Text("Готово") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Сообщить о проблеме") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TicketKind.entries.forEach { option ->
                        FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option.title) })
                    }
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(MAX_TICKET_BODY) },
                    label = { Text("Что случилось или что хочется") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag(TicketTags.BODY),
                )
                if (raw != null) {
                    CheckRow("Приложить скриншот", attach, TicketTags.ATTACH) { attach = it }
                    if (attach) {
                        preview?.let {
                            Image(it, contentDescription = "Скриншот", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp))
                        }
                        CheckRow("Показать суммы на скриншоте", showAmounts, TicketTags.SHOW_AMOUNTS) { value ->
                            showAmounts = value
                            scope.launch { recapture(value)?.let { source = it } }
                        }
                        CheckRow(if (mark != null) "Размыть всё, кроме отмеченного места" else "Размыть весь скриншот", blur, TicketTags.BLUR) { blur = it }
                    }
                }
                Text(
                    "Вместе с текстом уйдёт: ${tech.values.joinToString(" · ")}. Суммы — только если ты их показал на скриншоте.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            PrimaryButton(
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val png = if (attach) preview?.let(::pngWithinLimit) else null
                            sent = onSend(TicketDraft(kind, body, null, mark?.x, mark?.y, tech, png))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Не отправилось, попробуй ещё раз"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && body.isNotBlank(),
                modifier = Modifier.testTag(TicketTags.SEND),
            ) { Text(if (busy) "Отправляю…" else "Отправить") }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Отмена") } },
    )
}

@Composable
private fun CheckRow(text: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
