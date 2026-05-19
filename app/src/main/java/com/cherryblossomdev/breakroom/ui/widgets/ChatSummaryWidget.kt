package com.cherryblossomdev.breakroom.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cherryblossomdev.breakroom.data.ChatRepository
import com.cherryblossomdev.breakroom.data.models.*
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
    onOpenRoom: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var queue by remember { mutableStateOf<List<ChatUnreadRoom>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var isLoadingRecent by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var allDone by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var recentRooms by remember { mutableStateOf<List<ChatRecentRoom>>(emptyList()) }

    // Rooms joined for the all-done view — tracked so we can leave them on refresh/dispose
    val joinedRecentIds = remember { mutableSetOf<Int>() }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val currentRoom: ChatUnreadRoom? =
        if (queue.isNotEmpty() && currentIndex < queue.size) queue[currentIndex] else null

    // Live socket updates — collect from repository's shared socket event flow
    LaunchedEffect(Unit) {
        chatRepository.socketEvents.collect { event ->
            if (event is SocketEvent.NewMessage) {
                if (allDone) {
                    // All-done view: update matching room row, move to bottom
                    val roomIdx = recentRooms.indexOfFirst { it.room_id == event.roomId }
                    if (roomIdx != -1) {
                        val updated = recentRooms[roomIdx].copy(
                            message = event.message.message,
                            handle = event.message.handle,
                            created_at = event.message.created_at,
                            unread_count = recentRooms[roomIdx].unread_count + 1
                        )
                        val newList = recentRooms.toMutableList()
                        newList.removeAt(roomIdx)
                        newList.add(updated)
                        recentRooms = newList
                        delay(80)
                        if (recentRooms.isNotEmpty()) listState.scrollToItem(recentRooms.size - 1)
                    }
                    if (event.message.handle != currentUserHandle) onNewMessage()
                } else {
                    // Active queue room: append message if it belongs to the current room
                    val activeRoom = if (queue.isNotEmpty() && currentIndex < queue.size) queue[currentIndex] else null
                    if (activeRoom != null && event.roomId == activeRoom.id) {
                        if (messages.none { it.id == event.message.id }) {
                            messages = messages + event.message
                            delay(80)
                            if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
                        }
                        if (event.message.handle != currentUserHandle) onNewMessage()
                    }
                }
            }
        }
    }

    // Leave all recent rooms when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            joinedRecentIds.forEach { chatRepository.leaveRoom(it) }
            joinedRecentIds.clear()
        }
    }

    fun leaveAllRecentRooms() {
        joinedRecentIds.forEach { chatRepository.leaveRoom(it) }
        joinedRecentIds.clear()
    }

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
        if (targetIdx < loadedMessages.size) listState.scrollToItem(targetIdx)
    }

    suspend fun loadRoomMessages(room: ChatUnreadRoom) {
        isLoadingMessages = true
        messages = emptyList()
        when (val result = chatRepository.loadMessagesForSummary(room.id)) {
            is ChatResult.Success -> {
                messages = result.data
                scrollAfterLoad(result.data, room)
            }
            is ChatResult.Error -> { /* show empty */ }
        }
        isLoadingMessages = false
    }

    suspend fun loadRecentRooms() {
        isLoadingRecent = true
        when (val result = chatRepository.getRecentRooms()) {
            is ChatResult.Success -> recentRooms = result.data
            is ChatResult.Error -> { /* silently fail — list stays empty */ }
        }
        isLoadingRecent = false
        // Join all rooms so new messages arrive via socket
        recentRooms.forEach {
            chatRepository.joinRoom(it.room_id)
            joinedRecentIds.add(it.room_id)
        }
        delay(80)
        if (recentRooms.isNotEmpty()) listState.scrollToItem(recentRooms.size - 1)
    }

    suspend fun refreshQueue() {
        leaveAllRecentRooms()
        isLoading = true
        error = null
        allDone = false
        queue = emptyList()
        messages = emptyList()
        recentRooms = emptyList()
        currentIndex = 0
        when (val result = chatRepository.getUnreadSummary()) {
            is ChatResult.Success -> {
                val q = result.data
                queue = q
                isLoading = false
                if (q.isEmpty()) {
                    allDone = true
                    loadRecentRooms()
                } else {
                    chatRepository.joinRoom(q[0].id)
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
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { scope.launch { refreshQueue() } }) {
                            Text("Retry")
                        }
                    }
                }
            }

            allDone -> {
                if (isLoadingRecent) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = SummaryAccentColor,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        if (recentRooms.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No messages in any room yet.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(recentRooms) { _, item ->
                                    RecentRoomItem(
                                        item = item,
                                        onOpen = { onOpenRoom(item.room_id) }
                                    )
                                }
                            }
                        }
                    }
                    Divider()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = { scope.launch { refreshQueue() } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("↺ Check for New Messages", fontSize = 12.sp)
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
                                        if (newMsg.id > 0) messages = messages + newMsg
                                        chatRepository.markRoomRead(currentRoom.id)
                                        delay(80)
                                        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
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
                                chatRepository.leaveRoom(currentRoom.id)
                                if (currentIndex < queue.size - 1) {
                                    currentIndex++
                                    val next = queue[currentIndex]
                                    chatRepository.joinRoom(next.id)
                                    loadRoomMessages(next)
                                } else {
                                    queue = emptyList()
                                    messages = emptyList()
                                    allDone = true
                                    loadRecentRooms()
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
private fun RecentRoomItem(
    item: ChatRecentRoom,
    onOpen: () -> Unit
) {
    val isUnread = item.unread_count > 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isUnread) Modifier.border(
                    width = 2.dp,
                    color = SummaryAccentColor,
                    shape = RoundedCornerShape(6.dp)
                ) else Modifier
            ),
        color = if (isUnread) SummaryAccentColor.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        shadowElevation = if (isUnread) 0.dp else 0.5.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            // Room name + unread badge + timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "# ${item.room_name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isUnread) {
                        Surface(
                            color = SummaryAccentColor,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${item.unread_count} new",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                Text(
                    text = formatRecentTime(item.created_at),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Handle + message text + Open button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.handle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    item.message?.let { text ->
                        if (text.isNotBlank()) {
                            Text(
                                text = text,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Open →", fontSize = 10.sp)
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

// Relative time for active queue room messages ("5m ago", "2h ago", "May 18")
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

// Absolute date+time for the recent rooms all-done view ("May 18, 2:30 PM")
private fun formatRecentTime(iso: String): String {
    val ms = parseSummaryTimestamp(iso) ?: return ""
    return SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(ms))
}
