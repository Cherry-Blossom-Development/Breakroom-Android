package com.cherryblossomdev.breakroom.ui.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@Composable
fun ChatSummaryWidget(
    chatRepository: ChatRepository,
    currentUserHandle: String,
    onNewMessage: () -> Unit = {},
    onOpenRoom: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // rooms sorted ASC by last-message time: left = oldest, right = newest
    var rooms by remember { mutableStateOf<List<ChatRecentRoom>>(emptyList()) }
    var currentRoomId by remember { mutableStateOf<Int?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var messageText by remember { mutableStateOf("") }
    var rightGlowing by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val currentRoom: ChatRecentRoom? = rooms.find { it.room_id == currentRoomId }
    val currentIdx: Int = rooms.indexOfFirst { it.room_id == currentRoomId }
    val canLeft = currentIdx > 0
    val canRight = currentIdx < rooms.size - 1

    val rightArrowBg by animateColorAsState(
        targetValue = if (rightGlowing) Color(0xFFECC94B).copy(alpha = 0.7f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (rightGlowing) 0 else 2000),
        label = "rightGlow"
    )

    fun triggerRightGlow() {
        rightGlowing = true
        scope.launch {
            delay(2000)
            rightGlowing = false
        }
    }

    suspend fun loadMessages(roomId: Int) {
        isLoadingMessages = true
        messages = emptyList()
        when (val result = chatRepository.loadMessagesForSummary(roomId)) {
            is ChatResult.Success -> {
                messages = result.data
                isLoadingMessages = false  // LazyColumn must be visible before scrollToItem
                if (result.data.isNotEmpty()) {
                    delay(80)
                    listState.scrollToItem(result.data.size - 1)
                }
            }
            is ChatResult.Error -> {
                isLoadingMessages = false
            }
        }
    }

    suspend fun loadRooms() {
        isLoading = true
        error = null
        when (val result = chatRepository.getRecentRooms()) {
            is ChatResult.Success -> {
                val data = result.data  // already ASC sorted by server
                rooms = data
                if (data.isNotEmpty()) {
                    val startRoom = data.last()  // most recently active = rightmost
                    currentRoomId = startRoom.room_id
                    // Join all rooms so socket delivers messages for any of them
                    data.forEach { chatRepository.joinRoom(it.room_id) }
                    isLoading = false
                    loadMessages(startRoom.room_id)
                } else {
                    isLoading = false
                }
            }
            is ChatResult.Error -> {
                error = result.message
                isLoading = false
            }
        }
    }

    // Live socket updates
    LaunchedEffect(Unit) {
        chatRepository.socketEvents.collect { event ->
            if (event is SocketEvent.NewMessage) {
                val roomIdx = rooms.indexOfFirst { it.room_id == event.roomId }
                if (roomIdx == -1) return@collect

                val wasAtEnd = roomIdx == rooms.size - 1
                val isCurrent = event.roomId == currentRoomId

                // Re-sort: move updated room to rightmost position
                val updatedRoom = rooms[roomIdx].copy(created_at = event.message.created_at)
                val newRooms = rooms.toMutableList()
                newRooms.removeAt(roomIdx)
                newRooms.add(updatedRoom)
                rooms = newRooms

                // Glow the right arrow if a different room moved up in the order
                if (!isCurrent && !wasAtEnd) triggerRightGlow()

                if (isCurrent) {
                    if (messages.none { it.id == event.message.id }) {
                        messages = messages + event.message
                        delay(80)
                        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
                    }
                }

                if (event.message.handle != currentUserHandle) onNewMessage()
            }
        }
    }

    // Leave all rooms when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            rooms.forEach { chatRepository.leaveRoom(it.room_id) }
        }
    }

    LaunchedEffect(Unit) {
        loadRooms()
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
                        OutlinedButton(onClick = { scope.launch { loadRooms() } }) { Text("Retry") }
                    }
                }
            }

            rooms.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No chat rooms available.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            else -> {
                // Carousel sub-header
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Left arrow
                        IconButton(
                            onClick = {
                                if (canLeft) scope.launch {
                                    val prev = rooms[currentIdx - 1]
                                    currentRoomId = prev.room_id
                                    loadMessages(prev.room_id)
                                }
                            },
                            enabled = canLeft,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Previous room",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Room name
                        Text(
                            text = "# ${currentRoom?.room_name ?: "…"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Position label
                        if (rooms.size > 1) {
                            Text(
                                text = "${currentIdx + 1} / ${rooms.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Right arrow with glow
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(rightArrowBg, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    if (canRight) scope.launch {
                                        val next = rooms[currentIdx + 1]
                                        currentRoomId = next.room_id
                                        loadMessages(next.room_id)
                                    }
                                },
                                enabled = canRight,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Next room",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Divider()

                // Messages area
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isLoadingMessages -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    CarouselMessageItem(
                                        message = msg,
                                        isOwn = msg.handle == currentUserHandle
                                    )
                                }
                            }
                        }
                    }
                }

                Divider()

                // Reply input
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
                        enabled = !isSending && currentRoom != null,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            val text = messageText.trim()
                            if (text.isBlank() || isSending || currentRoom == null) return@Button
                            scope.launch {
                                isSending = true
                                val savedText = text
                                messageText = ""
                                when (val result = chatRepository.sendMessage(currentRoom.room_id, savedText)) {
                                    is ChatResult.Success -> {
                                        val newMsg = result.data
                                        if (messages.none { it.id == newMsg.id }) {
                                            messages = messages + newMsg
                                            delay(80)
                                            if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
                                        }
                                    }
                                    is ChatResult.Error -> {
                                        messageText = savedText
                                    }
                                }
                                isSending = false
                            }
                        },
                        enabled = messageText.trim().isNotBlank() && !isSending && currentRoom != null,
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
                }
            }
        }
    }
}

@Composable
private fun CarouselMessageItem(
    message: ChatMessage,
    isOwn: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isOwn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 0.5.dp
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
                    text = formatCarouselTime(message.created_at),
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

private fun parseCarouselTimestamp(iso: String): Long? {
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

private fun formatCarouselTime(iso: String): String {
    val ms = parseCarouselTimestamp(iso) ?: return ""
    val d = Date(ms)
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().also { it.time = d }
    return if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
               cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
        SimpleDateFormat("h:mm a", Locale.US).format(d)
    } else {
        SimpleDateFormat("MMM d, h:mm a", Locale.US).format(d)
    }
}
