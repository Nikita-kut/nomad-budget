package ru.nomadbudget.presentation.tickets

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
import ru.nomadbudget.domain.repository.Ticket
import ru.nomadbudget.domain.repository.TicketRepository
import ru.nomadbudget.domain.repository.TicketStatus
import ru.nomadbudget.presentation.access.formatMoment
import ru.nomadbudget.presentation.components.CenteredLazyColumn
import ru.nomadbudget.presentation.more.SubScreenHeader
import ru.nomadbudget.presentation.theme.AppTheme

fun ticketLine(ticket: Ticket): String =
    "№${ticket.number} · ${ticket.kind.title.lowercase()} · ${formatMoment(ticket.createdAt)}" + (ticket.screen?.let { " · экран $it" } ?: "")

@Composable
fun statusColor(status: TicketStatus) = when (status) {
    TicketStatus.NEW -> MaterialTheme.colorScheme.onSurfaceVariant
    TicketStatus.IN_PROGRESS -> AppTheme.colors.warning
    TicketStatus.DONE -> AppTheme.colors.good
    TicketStatus.REJECTED -> AppTheme.colors.bad
}

@Composable
fun MyTicketsScreen(repository: TicketRepository, onNew: () -> Unit, onBack: (() -> Unit)?) {
    var tickets by remember { mutableStateOf<List<Ticket>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            tickets = repository.mine()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось загрузить обращения"
            tickets = emptyList()
        }
    }
    CenteredLazyColumn {
        if (onBack != null) item { SubScreenHeader("Мои обращения", onBack) }
        item {
            Text(
                "Ошибки, идеи и вопросы владельцу. Отвечать в приложении он не будет — следи за статусом.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { TextButton(onClick = onNew) { Text("Сообщить о проблеме") } }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        val list = tickets
        when {
            list == null -> item { Text("Загружаю…") }
            list.isEmpty() -> item { Text("Обращений пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> list.forEach { ticket ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(ticket.status.title, color = statusColor(ticket.status), fontWeight = FontWeight.Medium)
                            Text(ticketLine(ticket), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(ticket.body)
                        }
                    }
                }
            }
        }
    }
}
