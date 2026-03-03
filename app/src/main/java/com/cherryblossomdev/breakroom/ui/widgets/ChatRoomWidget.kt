package com.cherryblossomdev.breakroom.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.cherryblossomdev.breakroom.data.ChatRepository
import com.cherryblossomdev.breakroom.data.models.ChatMessage
import com.cherryblossomdev.breakroom.data.models.ChatResult
import com.cherryblossomdev.breakroom.network.RetrofitClient
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatRoomWidget(
    roomId: Int,
    chatRepository: ChatRepository,
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
    var suppressScrollToBottom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

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
        isLoading = true
        hasOlderMessages = false
        oldestMessageDate = null
        chatRepository.joinRoom(roomId)
        chatRepository.loadMessages(roomId)
        hasOlderMessages = chatRepository.getHasOlderMessages(roomId)
        oldestMessageDate = chatRepository.getOldestMessageDate(roomId)
        isLoading = false
    }

    // Detect scroll to top and load older messages
    LaunchedEffect(roomId) {
        var hasScrolledDown = false
        snapshotFlow { scrollState.value }
            .collect { scrollValue ->
                if (scrollValue > 100) hasScrolledDown = true
                if (scrollValue == 0 && hasScrolledDown && hasOlderMessages && !isLoadingOlderMessages && !isLoading) {
                    hasScrolledDown = false
                    val before = oldestMessageDate ?: return@collect
                    isLoadingOlderMessages = true
                    suppressScrollToBottom = true
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

    // Auto-scroll to bottom when new messages arrive (skip when prepending older ones)
    LaunchedEffect(messages.size) {
        if (suppressScrollToBottom) {
            suppressScrollToBottom = false
        } else if (messages.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
            // Messages area — grows to fit content, capped at 350dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 350.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
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
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            ChatMessageItem(message = message)
                        }
                    }
                }
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
                        modifier = Modifier.size(36.dp)
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
                    onValueChange = { messageText = it },
                    placeholder = { Text("Message...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank() && !isSending) {
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
                    modifier = Modifier.size(36.dp)
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
private fun ChatMessageItem(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Username and time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message.handle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = formatTime(message.created_at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
