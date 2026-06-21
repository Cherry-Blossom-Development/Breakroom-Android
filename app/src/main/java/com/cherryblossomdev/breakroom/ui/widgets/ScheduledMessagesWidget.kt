package com.cherryblossomdev.breakroom.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherryblossomdev.breakroom.data.models.ScheduledMessage
import com.cherryblossomdev.breakroom.data.models.ScheduledMessagesResponse
import com.cherryblossomdev.breakroom.network.RetrofitClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun ScheduledMessagesWidget(
    token: String,
    onNavigateToScheduledMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messages by remember { mutableStateOf<List<ScheduledMessage>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val response = RetrofitClient.breakroomApiService.getScheduledMessages("Bearer $token")
            if (response.isSuccessful) {
                messages = response.body()?.scheduled_messages ?: emptyList()
            } else {
                error = "Failed to load"
            }
        } catch (e: Exception) {
            error = "Failed to load"
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        when {
            messages == null && error == null -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            error != null -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            messages!!.isEmpty() -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No messages scheduled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages!!) { msg ->
                        ScheduledMessageWidgetItem(msg)
                    }
                }
            }
        }

        Divider()
        TextButton(
            onClick = onNavigateToScheduledMessages,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create new Message")
        }
    }
}

@Composable
private fun ScheduledMessageWidgetItem(message: ScheduledMessage) {
    val amberColor = Color(0xFFED8936)
    val statusColor = when {
        message.is_editing == 1 -> MaterialTheme.colorScheme.error
        message.status == "warning_sent" -> amberColor
        message.status == "confirmed" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val statusLabel = when {
        message.is_editing == 1 -> "Editing paused"
        message.status == "warning_sent" -> "Sending soon"
        message.status == "confirmed" -> "Confirmed"
        else -> "Scheduled"
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${message.room_name ?: message.room_id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatWidgetScheduledAt(message.scheduled_at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = message.message_text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatWidgetScheduledAt(scheduledAt: String): String {
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val normalized = scheduledAt.replace(Regex("\\.\\d+"), "").removeSuffix("Z")
        val date = fmt.parse(normalized) ?: return scheduledAt
        val cal = Calendar.getInstance().apply { time = date }
        val now = Calendar.getInstance()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        val prefix = when {
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Today"
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) + 1 -> "Tomorrow"
            else -> SimpleDateFormat("MMM d", Locale.US).format(date)
        }
        "$prefix at ${timeFmt.format(date)}"
    } catch (_: Exception) {
        scheduledAt
    }
}
