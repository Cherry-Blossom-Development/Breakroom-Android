package com.cherryblossomdev.breakroom.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.cherryblossomdev.breakroom.ModerationStore
import com.cherryblossomdev.breakroom.data.ChatRepository
import com.cherryblossomdev.breakroom.data.ModerationRepository
import com.cherryblossomdev.breakroom.data.models.ChatMessage
import com.cherryblossomdev.breakroom.data.models.ChatResult
import com.cherryblossomdev.breakroom.network.RetrofitClient
import com.cherryblossomdev.breakroom.ui.components.FlagDialog
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatRoomWidget(
    roomId: Int,
    chatRepository: ChatRepository,
    moderationRepository: ModerationRepository? = null,
    currentUserHandle: String = "",
    token: String? = null,
    onNavigateToProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var hasOlderMessages by remember { mutableStateOf(false) }
    var isLoadingOlderMessages by remember { mutableStateOf(false) }
    var oldestMessageDate by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var typingJob by remember { mutableStateOf<Job?>(null) }
    var typingUsersForRoom by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Dialog state
    var flaggingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editedText by remember { mutableStateOf("") }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var blockingMessage by remember { mutableStateOf<ChatMessage?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isUploading = true
                chatRepository.uploadImage(roomId, it, null)
                isUploading = false
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isUploading = true
                chatRepository.uploadVideo(roomId, it, null)
                isUploading = false
            }
        }
    }

    // Load messages and observe flow
    LaunchedEffect(roomId) {
        android.util.Log.d("ChatRoomWidget", "LaunchedEffect: joining room $roomId, socket connected=${chatRepository.isConnected()}")
        chatRepository.connect()
        isLoading = true
        hasOlderMessages = false
        oldestMessageDate = null
        chatRepository.joinRoom(roomId)
        chatRepository.loadMessages(roomId)
        hasOlderMessages = chatRepository.getHasOlderMessages(roomId)
        oldestMessageDate = chatRepository.getOldestMessageDate(roomId)
        isLoading = false
    }

    // Detect scroll to top (oldest messages) and load more.
    LaunchedEffect(roomId) {
        var hasScrolledDown = false
        snapshotFlow { scrollState.value }
            .collect { scrollValue ->
                if (scrollValue > 0) hasScrolledDown = true
                if (scrollValue == 0 && hasScrolledDown && hasOlderMessages && !isLoadingOlderMessages && !isLoading) {
                    hasScrolledDown = false
                    val before = oldestMessageDate ?: return@collect
                    isLoadingOlderMessages = true
                    chatRepository.loadMessages(roomId, before = before)
                    hasOlderMessages = chatRepository.getHasOlderMessages(roomId)
                    oldestMessageDate = chatRepository.getOldestMessageDate(roomId)
                    isLoadingOlderMessages = false
                }
            }
    }

    // Collect messages from repository
    LaunchedEffect(roomId) {
        chatRepository.getMessagesFlow(roomId).collect { msgs ->
            messages = msgs
        }
    }

    // Collect typing users for this room
    LaunchedEffect(roomId) {
        android.util.Log.d("ChatRoomWidget", "Starting typing collection for room $roomId")
        chatRepository.typingUsers.collect { typingMap ->
            val users = typingMap[roomId] ?: emptySet()
            android.util.Log.d("ChatRoomWidget", "typingUsers update for room $roomId: $users")
            typingUsersForRoom = users
        }
    }

    // Clean up typing indicator when widget is disposed or roomId changes
    DisposableEffect(roomId) {
        onDispose {
            typingJob?.cancel()
            chatRepository.stopTyping(roomId)
        }
    }

    // Auto-scroll to bottom when a new message arrives.
    // Key on last message id so prepending older messages never triggers this.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Flag dialog
    flaggingMessage?.let { msg ->
        if (token != null) {
            FlagDialog(
                token = token,
                contentType = "chat_message",
                contentId = msg.id,
                onDismiss = { flaggingMessage = null },
                onFlagged = { flaggingMessage = null }
            )
        }
    }

    // Edit dialog
    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = editedText
                        val message = msg
                        editingMessage = null
                        scope.launch {
                            chatRepository.editMessage(roomId, message.id, text)
                        }
                    },
                    enabled = editedText.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation dialog
    messageToDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete Message") },
            text = { Text("Are you sure you want to delete this message?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val message = msg
                        messageToDelete = null
                        scope.launch {
                            chatRepository.deleteMessage(roomId, message.id)
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Block user confirmation dialog
    blockingMessage?.let { msg ->
        val isAlreadyBlocked = ModerationStore.isBlocked(msg.user_id)
        AlertDialog(
            onDismissRequest = { blockingMessage = null },
            title = { Text(if (isAlreadyBlocked) "Unblock User" else "Block User") },
            text = { Text(if (isAlreadyBlocked) "Unblock @${msg.handle}?" else "Block @${msg.handle}? You won't see their messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val message = msg
                        blockingMessage = null
                        scope.launch {
                            if (isAlreadyBlocked) {
                                moderationRepository?.unblockUser(message.user_id)
                                ModerationStore.removeBlock(message.user_id)
                            } else {
                                moderationRepository?.blockUser(message.user_id)
                                ModerationStore.addBlock(message.user_id)
                            }
                        }
                    }
                ) { Text(if (isAlreadyBlocked) "Unblock" else "Block", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { blockingMessage = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Messages area — wraps to content height, capped at 350dp.
        // Using Column + verticalScroll so the box only takes as much space as
        // the messages actually need (no blank space when content < 350dp).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 350.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    strokeWidth = 2.dp
                )
            } else if (messages.isEmpty()) {
                Text(
                    text = "No messages yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(vertical = 8.dp)
                ) {
                    if (isLoadingOlderMessages) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Loading older messages...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    messages.forEach { message ->
                        val isOwn = message.handle == currentUserHandle
                        ChatMessageItem(
                            message = message,
                            isOwn = isOwn,
                            onFlag = if (!isOwn && token != null) {{ flaggingMessage = message }} else null,
                            onEdit = if (isOwn) {{ editingMessage = message; editedText = message.message ?: "" }} else null,
                            onDelete = if (isOwn) {{ messageToDelete = message }} else null,
                            onBlock = if (!isOwn) {{ blockingMessage = message }} else null,
                            onNavigateToProfile = { onNavigateToProfile(message.handle) }
                        )
                    }
                }
            }
        }

        // Typing indicator (exclude current user — they know they're typing)
        val othersTyping = typingUsersForRoom.filter { it != currentUserHandle }
        if (othersTyping.isNotEmpty()) {
            Text(
                text = "${othersTyping.joinToString(", ")} ${if (othersTyping.size == 1) "is" else "are"} typing...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        // Input area
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach button
                Box {
                    IconButton(
                        onClick = { showAttachMenu = !showAttachMenu },
                        enabled = !isUploading,
                        modifier = Modifier.size(36.dp).testTag("widget-media-button")
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Attach",
                                modifier = Modifier.size(18.dp)
                            )
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
                                imageLauncher.launch("image/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Video", fontSize = 14.sp) },
                            onClick = {
                                showAttachMenu = false
                                videoLauncher.launch("video/*")
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { newValue ->
                        messageText = newValue
                        typingJob?.cancel()
                        if (newValue.isNotEmpty()) {
                            chatRepository.startTyping(roomId)
                            typingJob = scope.launch {
                                delay(2000L)
                                chatRepository.stopTyping(roomId)
                            }
                        } else {
                            chatRepository.stopTyping(roomId)
                        }
                    },
                    placeholder = { Text("Message...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f).testTag("widget-message-input"),
                    maxLines = 2,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank() && !isSending) {
                            typingJob?.cancel()
                            chatRepository.stopTyping(roomId)
                            scope.launch {
                                isSending = true
                                val text = messageText
                                messageText = ""
                                when (chatRepository.sendMessage(roomId, text)) {
                                    is ChatResult.Success -> { /* Message sent */ }
                                    is ChatResult.Error -> {
                                        messageText = text // Restore on error
                                    }
                                }
                                isSending = false
                            }
                        }
                    },
                    enabled = messageText.isNotBlank() && !isSending,
                    modifier = Modifier.size(36.dp).testTag("widget-send-button")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    isOwn: Boolean = false,
    onFlag: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onBlock: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null
) {
    val isBlocked = ModerationStore.isBlocked(message.user_id)
    var menuExpanded by remember { mutableStateOf(false) }
    val errorColor = MaterialTheme.colorScheme.error
    val menuIconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Username, time, and meatball menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.handle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = if (onNavigateToProfile != null) Modifier.clickable { onNavigateToProfile() } else Modifier
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(message.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Message options",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { menuExpanded = true },
                        tint = menuIconTint
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (isOwn) {
                            onEdit?.let {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { menuExpanded = false; it() }
                                )
                            }
                            onDelete?.let {
                                DropdownMenuItem(
                                    text = { Text("Delete", color = errorColor) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = errorColor) },
                                    onClick = { menuExpanded = false; it() }
                                )
                            }
                        } else {
                            onFlag?.let {
                                DropdownMenuItem(
                                    text = { Text("Report", color = errorColor) },
                                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = errorColor) },
                                    onClick = { menuExpanded = false; it() }
                                )
                            }
                            onBlock?.let {
                                DropdownMenuItem(
                                    text = { Text(if (isBlocked) "Unblock User" else "Block User", color = errorColor) },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = errorColor) },
                                    onClick = { menuExpanded = false; it() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Message content
        if (!message.message.isNullOrBlank()) {
            Text(
                text = message.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Image if present
        message.image_path?.let { imagePath ->
            val imageUrl = "${RetrofitClient.BASE_URL}uploads/$imagePath"
            AsyncImage(
                model = imageUrl,
                contentDescription = "Image",
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .heightIn(max = 100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }

        // Video if present
        message.video_path?.let { videoPath ->
            val videoUrl = "${RetrofitClient.BASE_URL}api/uploads/$videoPath"
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setVideoPath(videoUrl)
                        val mediaController = MediaController(context)
                        mediaController.setAnchorView(this)
                        setMediaController(mediaController)
                    }
                },
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        Divider(
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

private fun formatTime(dateString: String): String {
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
