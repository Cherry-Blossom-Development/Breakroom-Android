package com.cherryblossomdev.breakroom.ui.widgets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    var showAttachMenu by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var pendingUploadRoomId by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val roomId = pendingUploadRoomId ?: return@let
            scope.launch {
                isUploading = true
                chatRepository.uploadImage(roomId, it, null)
                isUploading = false
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val roomId = pendingUploadRoomId ?: return@let
            scope.launch {
                isUploading = true
                chatRepository.uploadVideo(roomId, it, null)
                isUploading = false
            }
        }
    }

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

    // reverseLayout=true means index 0 = newest (rendered at bottom); scroll to 0 = scroll to bottom
    suspend fun loadMessages(roomId: Int) {
        isLoadingMessages = true
        messages = emptyList()
        when (val result = chatRepository.loadMessagesForSummary(roomId)) {
            is ChatResult.Success -> {
                messages = result.data
                isLoadingMessages = false
                if (result.data.isNotEmpty()) {
                    delay(80)
                    listState.scrollToItem(0)
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

                if (!isCurrent && !wasAtEnd) triggerRightGlow()

                if (isCurrent) {
                    if (messages.none { it.id == event.message.id }) {
                        messages = messages + event.message
                        delay(80)
                        listState.scrollToItem(0)
                    }
                }

                if (event.message.handle != currentUserHandle) onNewMessage()
            }
        }
    }

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

                        Text(
                            text = "# ${currentRoom?.room_name ?: "…"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (rooms.size > 1) {
                            Text(
                                text = "${currentIdx + 1} / ${rooms.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

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

                // Messages area — reverseLayout=true so newest is at the bottom (index 0)
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
                                    "No messages yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                state = listState,
                                reverseLayout = true,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(messages.reversed(), key = { it.id }) { msg ->
                                    CarouselMessageItem(message = msg)
                                }
                            }
                        }
                    }
                }

                // Input bar — matches ChatRoomWidget style
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(
                                onClick = { showAttachMenu = !showAttachMenu },
                                enabled = !isUploading && currentRoom != null,
                                modifier = Modifier.size(36.dp)
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Add, contentDescription = "Attach", modifier = Modifier.size(18.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Image", fontSize = 14.sp) },
                                    onClick = {
                                        showAttachMenu = false
                                        pendingUploadRoomId = currentRoomId
                                        imageLauncher.launch("image/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Video", fontSize = 14.sp) },
                                    onClick = {
                                        showAttachMenu = false
                                        pendingUploadRoomId = currentRoomId
                                        videoLauncher.launch("video/*")
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Message…", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isSending && currentRoom != null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                val text = messageText.trim()
                                if (text.isBlank() || isSending || currentRoom == null) return@IconButton
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
                                                listState.scrollToItem(0)
                                            }
                                        }
                                        is ChatResult.Error -> {
                                            messageText = savedText
                                        }
                                    }
                                    isSending = false
                                }
                            },
                            enabled = messageText.isNotBlank() && !isSending && currentRoom != null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarouselMessageItem(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.handle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = formatCarouselTime(message.created_at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!message.message.isNullOrBlank()) {
            Text(
                text = message.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Divider(
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    }
}

private fun formatCarouselTime(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString) ?: return ""
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val outputFormat = if (date.after(today)) {
            SimpleDateFormat("h:mm a", Locale.US)
        } else {
            SimpleDateFormat("M/d h:mm a", Locale.US)
        }
        outputFormat.format(date)
    } catch (e: Exception) {
        ""
    }
}
