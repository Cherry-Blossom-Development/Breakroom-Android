package com.cherryblossomdev.breakroom.ui.screens.chat

import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cherryblossomdev.breakroom.ModerationStore
import com.cherryblossomdev.breakroom.data.ModerationRepository
import kotlinx.coroutines.launch
import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.RetrofitClient
import com.cherryblossomdev.breakroom.ui.components.FlagDialog
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    token: String?,
    moderationRepository: ModerationRepository? = null,
    onNavigateToProfile: (String) -> Unit = {},
    onMarkRoomRead: (Int) -> Unit = {},
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
            currentUserId = viewModel.currentUserId,
            token = token,
            moderationRepository = moderationRepository,
            onBack = viewModel::leaveRoom,
            onMessageTextChange = viewModel::updateMessageText,
            onSendMessage = viewModel::sendMessage,
            onSelectImage = viewModel::setSelectedImage,
            onSelectVideo = viewModel::setSelectedVideo,
            onLoadMore = viewModel::loadMoreMessages,
            onEditMessage = viewModel::editMessage,
            onDeleteMessage = viewModel::deleteMessage,
            onNavigateToProfile = onNavigateToProfile,
            modifier = modifier
        )
    } else {
        RoomListContent(
            state = roomListState,
            onRoomSelected = { room ->
                onMarkRoomRead(room.id)
                viewModel.selectRoom(room)
            },
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
            },
            onInvite = { viewModel.showInviteUsersDialog(dialogState.selectedRoomForOptions!!) }
        )
    }

    // Invite users dialog
    if (dialogState.showInviteUsers) {
        InviteUsersDialog(
            roomName = dialogState.inviteForRoom?.name ?: "",
            allUsers = dialogState.allUsers,
            isLoading = dialogState.isLoadingUsers,
            searchQuery = dialogState.inviteSearchQuery,
            invitingUserId = dialogState.invitingUserId,
            error = dialogState.inviteError,
            onSearchQueryChange = viewModel::updateInviteSearchQuery,
            onInvite = viewModel::inviteUserToRoom,
            onDismiss = viewModel::hideInviteUsersDialog
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
                        modifier = Modifier.fillMaxSize().testTag("room-list"),
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
            .testTag("room-item")
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
    token: String?,
    moderationRepository: ModerationRepository? = null,
    onBack: () -> Unit,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSelectImage: (Uri?) -> Unit,
    onSelectVideo: (Uri?) -> Unit,
    onLoadMore: () -> Unit,
    onEditMessage: (Int, String) -> Unit,
    onDeleteMessage: (Int) -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var flaggingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editedText by remember { mutableStateOf("") }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var blockingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when the newest message changes (not when prepending older ones)
    LaunchedEffect(state.room?.id, state.messages.lastOrNull()?.id) {
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
                onSelectImage = onSelectImage,
                onSelectVideo = onSelectVideo
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
                    modifier = Modifier.weight(1f).testTag("message-list"),
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
                        val isOwn = message.user_id == currentUserId
                        MessageBubble(
                            message = message,
                            isOwn = isOwn,
                            onFlag = if (!isOwn && token != null) {
                                { flaggingMessage = message }
                            } else null,
                            onEdit = if (isOwn && !message.message.isNullOrEmpty()) {
                                { editingMessage = message; editedText = message.message ?: "" }
                            } else null,
                            onDelete = if (isOwn) {
                                { messageToDelete = message }
                            } else null,
                            onBlock = if (!isOwn) {
                                { blockingMessage = message }
                            } else null,
                            onNavigateToProfile = { onNavigateToProfile(message.handle) }
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

    // Flag dialog for reporting messages
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

    // Edit message dialog
    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null; editedText = "" },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = editedText.trim()
                        if (text.isNotEmpty()) {
                            onEditMessage(msg.id, text)
                        }
                        editingMessage = null
                        editedText = ""
                    },
                    enabled = editedText.trim().isNotEmpty()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null; editedText = "" }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation dialog
    messageToDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete Message") },
            text = { Text("Are you sure you want to delete this message? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteMessage(msg.id); messageToDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
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
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isOwn: Boolean,
    onFlag: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onBlock: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val onPrimaryMuted = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.45f)
    val onSurfaceMuted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val menuIconTint = if (isOwn) onPrimaryMuted else onSurfaceMuted
    val hasMenu = onFlag != null || onEdit != null || onDelete != null || onBlock != null
    val isBlocked = ModerationStore.isBlocked(message.user_id)

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
                // Header row — handle + meatball menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isOwn) {
                        Text(
                            text = message.handle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f)
                                .then(if (onNavigateToProfile != null) Modifier.clickable { onNavigateToProfile() } else Modifier)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (hasMenu) {
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
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = { menuExpanded = false; it() }
                                        )
                                    }
                                } else {
                                    onFlag?.let {
                                        DropdownMenuItem(
                                            text = { Text("Report", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = { menuExpanded = false; it() }
                                        )
                                    }
                                    onBlock?.let {
                                        DropdownMenuItem(
                                            text = { Text(if (isBlocked) "Unblock User" else "Block User", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = { menuExpanded = false; it() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))

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

                // Video if present
                message.video_path?.let { videoPath ->
                    val videoUrl = if (videoPath.startsWith("http")) {
                        videoPath
                    } else {
                        "${RetrofitClient.BASE_URL}api/uploads/$videoPath"
                    }
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
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
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
    onSelectImage: (Uri?) -> Unit,
    onSelectVideo: (Uri?) -> Unit
) {
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> onSelectImage(uri) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> onSelectVideo(uri) }

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

            // Selected video preview
            state.selectedVideoUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .height(80.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            "Video selected",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(
                        onClick = { onSelectVideo(null) },
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
                            "Remove video",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            var showAttachMenu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                        Icon(Icons.Default.Add, "Attach")
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Image") },
                            onClick = {
                                showAttachMenu = false
                                imagePicker.launch("image/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Video") },
                            onClick = {
                                showAttachMenu = false
                                videoPicker.launch("video/*")
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Type a message...") },
                    modifier = Modifier.weight(1f).testTag("message-input"),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onSend,
                    enabled = !state.isSending && (state.text.isNotBlank() || state.selectedImageUri != null || state.selectedVideoUri != null),
                    modifier = Modifier.testTag("send-button")
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
    val label = when (typingUsers.size) {
        1 -> "${typingUsers[0]} is typing"
        2 -> "${typingUsers[0]} and ${typingUsers[1]} are typing"
        else -> "${typingUsers.size} people are typing"
    }

    val infiniteTransition = rememberInfiniteTransition()

    val dot1Y by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0f at 0; -5f at 150; 0f at 300; 0f at 900
            },
            repeatMode = RepeatMode.Restart
        )
    )
    val dot2Y by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0f at 150; -5f at 300; 0f at 450; 0f at 900
            },
            repeatMode = RepeatMode.Restart
        )
    )
    val dot3Y by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0f at 300; -5f at 450; 0f at 600; 0f at 900
            },
            repeatMode = RepeatMode.Restart
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        listOf(dot1Y, dot2Y, dot3Y).forEach { offsetY ->
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .offset(y = offsetY.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
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
    onDelete: () -> Unit,
    onInvite: () -> Unit
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
                    TextButton(onClick = onInvite) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Invite Users")
                    }
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

@Composable
private fun InviteUsersDialog(
    roomName: String,
    allUsers: List<SearchUser>,
    isLoading: Boolean,
    searchQuery: String,
    invitingUserId: Int?,
    error: String?,
    onSearchQueryChange: (String) -> Unit,
    onInvite: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val filteredUsers = remember(allUsers, searchQuery) {
        if (searchQuery.isBlank()) allUsers
        else allUsers.filter { user ->
            user.handle.contains(searchQuery, ignoreCase = true) ||
                user.displayName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite to #$roomName") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Search users") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Box(modifier = Modifier.heightIn(min = 100.dp, max = 320.dp)) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        filteredUsers.isEmpty() -> {
                            Text(
                                text = if (searchQuery.isBlank()) "No users found" else "No results for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp)
                            )
                        }
                        else -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(filteredUsers, key = { it.id }) { user ->
                                    val isInviting = invitingUserId == user.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = user.initials,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = user.displayName,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "@${user.handle}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isInviting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            TextButton(
                                                onClick = { onInvite(user.id) },
                                                enabled = invitingUserId == null
                                            ) {
                                                Text("Invite")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun formatTime(dateString: String): String {
    fun format(date: Date): String {
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = date }
        val isToday = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
        return if (isToday) {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        } else if (now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
        } else {
            SimpleDateFormat("MMM d yyyy, h:mm a", Locale.getDefault()).format(date)
        }
    }
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        format(inputFormat.parse(dateString)!!)
    } catch (e: Exception) {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            format(inputFormat.parse(dateString)!!)
        } catch (e: Exception) {
            dateString.takeLast(8)
        }
    }
}
