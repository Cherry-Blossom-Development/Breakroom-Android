package com.cherryblossomdev.breakroom.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cherryblossomdev.breakroom.data.ChatRepository
import com.cherryblossomdev.breakroom.data.models.ChatMessage
import com.cherryblossomdev.breakroom.data.models.ChatResult
import com.cherryblossomdev.breakroom.data.models.ChatUnreadRoom
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val SummaryAccentColor = Color(0xFF1565C0)

@Composable
fun ChatSummaryWidget(
    chatRepository: ChatRepository,
    currentUserHandle: String,
    onNewMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var queue by remember { mutableStateOf<List<ChatUnreadRoom>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var allDone by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val currentRoom: ChatUnreadRoom? =
        if (queue.isNotEmpty() && currentIndex < queue.size) queue[currentIndex] else null

    suspend fun scrollAfterLoad(loadedMessages: List<ChatMessage>, room: ChatUnreadRoom) {
        if (loadedMessages.isEmpty()) return
        delay(80)
        val cutoffMs = room.last_read_at?.let { parseSummaryTimestamp(it) }
        val unreadIdx = when {
            cutoffMs == null -> 0
            else -> loadedMessages.indexOfFirst {
                (parseSummaryTimestamp(it.created_at) ?: Long.MIN_VALUE) > cutoffMs
            }
        }
        val targetIdx = when {
            unreadIdx > 0 -> maxOf(0, unreadIdx - 1)
            else -> loadedMessages.size - 1
        }
        if (targetIdx < loadedMessages.size) {
            listState.scrollToItem(targetIdx)
        }
    }

    suspend fun loadRoomMessages(room: ChatUnreadRoom) {
        isLoadingMessages = true
        messages = emptyList()
        when (val result = chatRepository.loadMessagesForSummary(room.id)) {
            is ChatResult.Success -> {
                messages = result.data
                scrollAfterLoad(result.data, room)
            }
            is ChatResult.Error -> { /* show empty messages */ }
        }
        isLoadingMessages = false
    }

    suspend fun refreshQueue() {
        isLoading = true
        error = null
        allDone = false
        queue = emptyList()
        messages = emptyList()
        currentIndex = 0
        when (val result = chatRepository.getUnreadSummary()) {
            is ChatResult.Success -> {
                val q = result.data
                queue = q
                isLoading = false
                if (q.isEmpty()) {
                    allDone = true
                } else {
                    loadRoomMessages(q[0])
                }
            }
            is ChatResult.Error -> {
                error = result.message
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshQueue()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = SummaryAccentColor,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = error ?: "Error",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { scope.launch { refreshQueue() } }) {
                            Text("Retry")
                        }
                    }
                }
            }

            allDone -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SummaryAccentColor,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No New Messages",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { scope.launch { refreshQueue() } }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh")
                        }
                    }
                }
            }

            currentRoom != null -> {
                // Sub-header: room name + position label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "# ${currentRoom.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${currentIndex + 1} of ${queue.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Divider()

                // Messages area
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isLoadingMessages -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = SummaryAccentColor,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        messages.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No messages yet.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            val cutoffMs = currentRoom.last_read_at?.let { parseSummaryTimestamp(it) }
                            val firstUnreadIdx = when {
                                cutoffMs == null -> 0
                                else -> messages.indexOfFirst {
                                    (parseSummaryTimestamp(it.created_at) ?: Long.MIN_VALUE) > cutoffMs
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(messages) { idx, msg ->
                                    if (idx == firstUnreadIdx && firstUnreadIdx >= 0) {
                                        SummaryUnreadDivider()
                                    }
                                    SummaryMessageItem(
                                        message = msg,
                                        isNew = firstUnreadIdx >= 0 && idx >= firstUnreadIdx
                                    )
                                }
                            }
                        }
                    }
                }

                Divider()

                // Footer: reply input + Send + Next
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 52.dp),
                        placeholder = { Text("Reply…", fontSize = 12.sp) },
                        singleLine = true,
                        enabled = !isSending,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            val text = messageText.trim()
                            if (text.isBlank() || isSending) return@Button
                            scope.launch {
                                isSending = true
                                val savedText = text
                                messageText = ""
                                when (val result = chatRepository.sendMessage(currentRoom.id, savedText)) {
                                    is ChatResult.Success -> {
                                        val newMsg = result.data
                                        if (newMsg.id > 0) {
                                            messages = messages + newMsg
                                        }
                                        chatRepository.markRoomRead(currentRoom.id)
                                        delay(80)
                                        if (messages.isNotEmpty()) {
                                            listState.scrollToItem(messages.size - 1)
                                        }
                                    }
                                    is ChatResult.Error -> {
                                        messageText = savedText
                                    }
                                }
                                isSending = false
                            }
                        },
                        enabled = messageText.trim().isNotBlank() && !isSending,
                        colors = ButtonDefaults.buttonColors(containerColor = SummaryAccentColor),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Send", fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                chatRepository.markRoomRead(currentRoom.id)
                                if (currentIndex < queue.size - 1) {
                                    currentIndex++
                                    loadRoomMessages(queue[currentIndex])
                                } else {
                                    queue = emptyList()
                                    messages = emptyList()
                                    allDone = true
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Next →", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryUnreadDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Divider(
            modifier = Modifier.weight(1f),
            color = SummaryAccentColor.copy(alpha = 0.5f)
        )
        Text(
            text = "NEW MESSAGES",
            modifier = Modifier.padding(horizontal = 8.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = SummaryAccentColor,
            letterSpacing = 0.5.sp
        )
        Divider(
            modifier = Modifier.weight(1f),
            color = SummaryAccentColor.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SummaryMessageItem(
    message: ChatMessage,
    isNew: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isNew) SummaryAccentColor.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        shadowElevation = if (isNew) 0.dp else 0.5.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.handle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatSummaryTime(message.created_at),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            message.message?.let { text ->
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

private fun parseSummaryTimestamp(iso: String): Long? {
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    )
    for (fmt in formats) {
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        try {
            return fmt.parse(iso)?.time
        } catch (_: Exception) {}
    }
    return null
}

private fun formatSummaryTime(iso: String): String {
    val ms = parseSummaryTimestamp(iso) ?: return ""
    val diffMs = System.currentTimeMillis() - ms
    val diffMins = (diffMs / 60_000).toInt()
    val diffHours = (diffMs / 3_600_000).toInt()
    return when {
        diffMins < 60 -> "${diffMins}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(ms))
    }
}
