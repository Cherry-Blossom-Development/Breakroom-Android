package com.example.breakroom.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import coil.compose.AsyncImage
import com.example.breakroom.data.ChatRepository
import com.example.breakroom.data.models.ChatMessage
import com.example.breakroom.data.models.ChatResult
import com.example.breakroom.network.RetrofitClient
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
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Load messages and observe flow
    LaunchedEffect(roomId) {
        isLoading = true
        chatRepository.joinRoom(roomId)
        chatRepository.loadMessages(roomId, limit = 30)
        isLoading = false
    }

    // Collect messages from repository
    LaunchedEffect(roomId) {
        chatRepository.getMessagesFlow(roomId).collect { msgs ->
            messages = msgs
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Messages area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    strokeWidth = 2.dp
                )
            } else if (messages.isEmpty()) {
                Text(
                    text = "No messages yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages) { message ->
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
