package com.example.breakroom.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.breakroom.data.models.*
import com.example.breakroom.network.RetrofitClient
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val roomListState by viewModel.roomListState.collectAsState()
    val chatRoomState by viewModel.chatRoomState.collectAsState()
    val inputState by viewModel.inputState.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()

    // Show room or room list based on selection
    if (chatRoomState.room != null) {
        ChatRoomContent(
            state = chatRoomState,
            inputState = inputState,
            currentUserId = viewModel.run { 0 }, // We'll get this from ViewModel
            onBack = viewModel::leaveRoom,
            onMessageTextChange = viewModel::updateMessageText,
            onSendMessage = viewModel::sendMessage,
            onSelectImage = viewModel::setSelectedImage,
            onLoadMore = viewModel::loadMoreMessages,
            modifier = modifier
        )
    } else {
        RoomListContent(
            state = roomListState,
            onRoomSelected = viewModel::selectRoom,
            onRoomLongPress = viewModel::showRoomOptions,
            onAcceptInvite = viewModel::acceptInvite,
            onDeclineInvite = viewModel::declineInvite,
            onCreateRoom = viewModel::showCreateRoomDialog,
            onRetry = viewModel::connectAndLoad,
            isRoomOwner = viewModel::isRoomOwner,
            modifier = modifier
        )
    }

    // Create room dialog
    if (dialogState.showCreateRoom) {
        CreateRoomDialog(
            onDismiss = viewModel::hideCreateRoomDialog,
            onCreate = viewModel::createRoom
        )
    }

    // Room options dialog
    if (dialogState.showRoomOptions && dialogState.selectedRoomForOptions != null) {
        RoomOptionsDialog(
            room = dialogState.selectedRoomForOptions!!,
            onDismiss = viewModel::hideRoomOptions,
            onUpdate = { name, description ->
                viewModel.updateRoom(dialogState.selectedRoomForOptions!!.id, name, description)
            },
            onDelete = {
                viewModel.deleteRoom(dialogState.selectedRoomForOptions!!.id)
            }
        )
    }

    // Error snackbar
    val error = roomListState.error ?: chatRoomState.error
    if (error != null) {
        LaunchedEffect(error) {
            // Auto-clear error after showing
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomListContent(
    state: RoomListUiState,
    onRoomSelected: (ChatRoom) -> Unit,
    onRoomLongPress: (ChatRoom) -> Unit,
    onAcceptInvite: (Int) -> Unit,
    onDeclineInvite: (Int) -> Unit,
    onCreateRoom: () -> Unit,
    onRetry: () -> Unit,
    isRoomOwner: (ChatRoom) -> Boolean,
    modifier: Modifier = Modifier
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRoom) {
                Icon(Icons.Default.Add, contentDescription = "Create Room")
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Connection status bar
            ConnectionStatusBar(state.connectionState)

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.rooms.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onRetry) {
                                Text("Retry")
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pending invites section
                        if (state.invites.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Pending Invites",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(state.invites, key = { it.room_id }) { invite ->
                                InviteItem(
                                    invite = invite,
                                    onAccept = { onAcceptInvite(invite.room_id) },
                                    onDecline = { onDeclineInvite(invite.room_id) }
                                )
                            }
                            item {
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }

                        // Rooms section
                        item {
                            Text(
                                text = "Rooms",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(state.rooms, key = { it.id }) { room ->
                            RoomItem(
                                room = room,
                                isOwner = isRoomOwner(room),
                                onClick = { onRoomSelected(room) },
                                onLongClick = { onRoomLongPress(room) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusBar(state: SocketConnectionState) {
    val (backgroundColor, text) = when (state) {
        SocketConnectionState.CONNECTED -> null to null
        SocketConnectionState.CONNECTING -> MaterialTheme.colorScheme.primaryContainer to "Connecting..."
        SocketConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiaryContainer to "Reconnecting..."
        SocketConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer to "Connection error"
        SocketConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant to "Disconnected"
    }

    if (text != null && backgroundColor != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RoomItem(
    room: ChatRoom,
    isOwner: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Room icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = room.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isOwner) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Owner",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                room.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isOwner) {
                IconButton(onClick = onLongClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Room options"
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteItem(
    invite: ChatInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = invite.room_name,
                    style = MaterialTheme.typography.titleMedium
                )
                invite.invited_by_handle?.let { inviter ->
                    Text(
                        text = "Invited by $inviter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            IconButton(onClick = onDecline) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Decline",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            IconButton(onClick = onAccept) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Accept",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomContent(
    state: ChatRoomUiState,
    inputState: MessageInputState,
    currentUserId: Int,
    onBack: () -> Unit,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSelectImage: (Uri?) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // Load more when scrolling to top
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisible ->
                if (firstVisible < 5 && state.hasMoreMessages && !state.isLoadingMore && !state.isLoadingMessages) {
                    onLoadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.room?.name ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                state = inputState,
                onTextChange = onMessageTextChange,
                onSend = onSendMessage,
                onSelectImage = onSelectImage
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Loading indicator for initial load
            if (state.isLoadingMessages && state.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Loading more indicator
                    if (state.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isOwn = message.user_id == currentUserId
                        )
                    }
                }
            }

            // Typing indicator
            if (state.typingUsers.isNotEmpty()) {
                TypingIndicator(typingUsers = state.typingUsers)
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isOwn: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwn) 16.dp else 4.dp,
                bottomEnd = if (isOwn) 4.dp else 16.dp
            ),
            color = if (isOwn)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isOwn) {
                    Text(
                        text = message.handle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Image if present
                message.image_path?.let { imagePath ->
                    val imageUrl = if (imagePath.startsWith("http")) {
                        imagePath
                    } else {
                        "${RetrofitClient.BASE_URL}uploads/$imagePath"
                    }
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Message text
                message.message?.let { text ->
                    if (text.isNotEmpty()) {
                        Text(
                            text = text,
                            color = if (isOwn)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Timestamp
                Text(
                    text = formatTime(message.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOwn)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    state: MessageInputState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onSelectImage: (Uri?) -> Unit
) {
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> onSelectImage(uri) }

    Surface(
        tonalElevation = 2.dp
    ) {
        Column {
            // Selected image preview
            state.selectedImageUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .height(80.dp)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { onSelectImage(null) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            "Remove image",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Default.Add, "Attach image")
                }

                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Type a message...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onSend,
                    enabled = !state.isSending && (state.text.isNotBlank() || state.selectedImageUri != null)
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Filled.Send, "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator(typingUsers: List<String>) {
    val text = when (typingUsers.size) {
        1 -> "${typingUsers[0]} is typing..."
        2 -> "${typingUsers[0]} and ${typingUsers[1]} are typing..."
        else -> "${typingUsers.size} people are typing..."
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Simple dots animation could be added here
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Room") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Room Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, description.ifEmpty { null }) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RoomOptionsDialog(
    room: ChatRoom,
    onDismiss: () -> Unit,
    onUpdate: (String, String?) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(room.name) }
    var description by remember { mutableStateOf(room.description ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Room") },
            text = { Text("Are you sure you want to delete '${room.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Room") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Room Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Room")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onUpdate(name, description.ifEmpty { null }) },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatTime(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        outputFormat.format(date!!)
    } catch (e: Exception) {
        try {
            // Try alternative format without milliseconds
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(dateString)
            val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            outputFormat.format(date!!)
        } catch (e: Exception) {
            dateString.takeLast(8) // Fallback: just show last part
        }
    }
}
