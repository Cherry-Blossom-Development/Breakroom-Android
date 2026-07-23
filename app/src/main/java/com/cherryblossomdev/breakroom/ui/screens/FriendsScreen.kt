package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.RetrofitClient
import com.cherryblossomdev.breakroom.ui.components.AccessibilityAnnouncer
import androidx.compose.ui.platform.testTag
import java.text.SimpleDateFormat
import java.util.*

enum class FriendsTab(val title: String) {
    FRIENDS("Friends"),
    REQUESTS("Requests"),
    SENT("Sent"),
    FIND("Find"),
    BLOCKED("Blocked")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(FriendsTab.FRIENDS) }
    var searchQuery by remember { mutableStateOf("") }
    var showRemoveDialog by remember { mutableStateOf<Friend?>(null) }
    var showBlockDialog by remember { mutableStateOf<Friend?>(null) }

    AccessibilityAnnouncer(uiState.announcement)

    LaunchedEffect(Unit) {
        viewModel.loadAll()
    }

    // Show snackbar for success messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .testTag("screen-friends")
        ) {
            // Tabs — custom row so each tab is content-width with equal gaps
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FriendsTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val badgeCount = when (tab) {
                                FriendsTab.REQUESTS -> uiState.requests.size
                                else -> 0
                            }
                            val labelColor = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                            val indicatorColor = MaterialTheme.colorScheme.primary
                            Column(
                                modifier = Modifier
                                    .clickable { selectedTab = tab }
                                    .drawBehind {
                                        if (isSelected) {
                                            drawRect(
                                                color = indicatorColor,
                                                topLeft = Offset(0f, size.height - 2.dp.toPx()),
                                                size = Size(size.width, 2.dp.toPx())
                                            )
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = labelColor,
                                        maxLines = 1
                                    )
                                    if (badgeCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                                            Text(badgeCount.toString())
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Divider()
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        when (selectedTab) {
                            FriendsTab.FRIENDS -> FriendsListTab(
                                friends = uiState.friends,
                                actionInProgress = uiState.actionInProgress,
                                onRemove = { showRemoveDialog = it },
                                onBlock = { showBlockDialog = it }
                            )
                            FriendsTab.REQUESTS -> RequestsTab(
                                requests = uiState.requests,
                                actionInProgress = uiState.actionInProgress,
                                onAccept = { viewModel.acceptFriendRequest(it) },
                                onDecline = { viewModel.declineFriendRequest(it) }
                            )
                            FriendsTab.SENT -> SentTab(
                                sent = uiState.sent,
                                actionInProgress = uiState.actionInProgress,
                                onCancel = { viewModel.cancelFriendRequest(it) }
                            )
                            FriendsTab.FIND -> FindUsersTab(
                                allUsers = uiState.allUsers,
                                friends = uiState.friends,
                                requests = uiState.requests,
                                sent = uiState.sent,
                                blocked = uiState.blocked,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                actionInProgress = uiState.actionInProgress,
                                onAddFriend = { viewModel.sendFriendRequest(it) }
                            )
                            FriendsTab.BLOCKED -> BlockedTab(
                                blocked = uiState.blocked,
                                actionInProgress = uiState.actionInProgress,
                                onUnblock = { viewModel.unblockUser(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Remove friend dialog
    showRemoveDialog?.let { friend ->
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = { Text("Remove Friend") },
            text = { Text("Are you sure you want to remove ${friend.displayName} from your friends?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeFriend(friend.id)
                        showRemoveDialog = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Block user dialog
    showBlockDialog?.let { friend ->
        AlertDialog(
            onDismissRequest = { showBlockDialog = null },
            title = { Text("Block User") },
            text = { Text("Are you sure you want to block ${friend.displayName}? This will also remove them from your friends.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.blockUser(friend.id)
                        showBlockDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FriendsListTab(
    friends: List<Friend>,
    actionInProgress: Int?,
    onRemove: (Friend) -> Unit,
    onBlock: (Friend) -> Unit
) {
    if (friends.isEmpty()) {
        EmptyState(message = "No friends yet", subMessage = "Use the Find Users tab to add friends")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(friends, key = { it.id }) { friend ->
                FriendCard(
                    friend = friend,
                    isLoading = actionInProgress == friend.id,
                    onRemove = { onRemove(friend) },
                    onBlock = { onBlock(friend) }
                )
            }
        }
    }
}

@Composable
private fun FriendCard(
    friend: Friend,
    isLoading: Boolean,
    onRemove: () -> Unit,
    onBlock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val friendLabel = remember(friend.handle, friend.displayName, friend.friends_since) {
                buildString {
                    append(friend.displayName)
                    append(". @${friend.handle}")
                    friend.friends_since?.let { append(". Friends since ${formatDate(it)}") }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {
                        contentDescription = friendLabel
                        customActions = listOf(
                            CustomAccessibilityAction("Remove friend") { onRemove(); true }
                        )
                    }
            ) {
                UserAvatar(
                    photoUrl = friend.profile_photo,
                    initials = friend.initials
                )
                Column {
                    Text(
                        text = "@${friend.handle}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = friend.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    friend.friends_since?.let {
                        Text(
                            text = "Friends since ${formatDate(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onRemove,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Remove", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onBlock,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Block", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestsTab(
    requests: List<FriendRequest>,
    actionInProgress: Int?,
    onAccept: (Int) -> Unit,
    onDecline: (Int) -> Unit
) {
    if (requests.isEmpty()) {
        EmptyState(message = "No pending requests", subMessage = "Friend requests will appear here")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(requests, key = { it.id }) { request ->
                RequestCard(
                    request = request,
                    isLoading = actionInProgress == request.id,
                    onAccept = { onAccept(request.id) },
                    onDecline = { onDecline(request.id) }
                )
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: FriendRequest,
    isLoading: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val requestLabel = remember(request.handle, request.displayName, request.requested_at) {
                buildString {
                    append(request.displayName)
                    append(". @${request.handle}")
                    request.requested_at?.let { append(". Requested ${formatDate(it)}") }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = requestLabel }
            ) {
                UserAvatar(
                    photoUrl = request.profile_photo,
                    initials = request.initials
                )
                Column {
                    Text(
                        text = "@${request.handle}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = request.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    request.requested_at?.let {
                        Text(
                            text = "Requested ${formatDate(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onAccept,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.semantics { contentDescription = "Accept request from ${request.displayName}" }
                    ) {
                        Text("Accept", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.semantics { contentDescription = "Decline request from ${request.displayName}" }
                    ) {
                        Text("Decline", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SentTab(
    sent: List<FriendRequest>,
    actionInProgress: Int?,
    onCancel: (Int) -> Unit
) {
    if (sent.isEmpty()) {
        EmptyState(message = "No sent requests", subMessage = "Requests you send will appear here")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sent, key = { it.id }) { request ->
                SentRequestCard(
                    request = request,
                    isLoading = actionInProgress == request.id,
                    onCancel = { onCancel(request.id) }
                )
            }
        }
    }
}

@Composable
private fun SentRequestCard(
    request: FriendRequest,
    isLoading: Boolean,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sentLabel = remember(request.handle, request.displayName, request.sent_at) {
                buildString {
                    append(request.displayName)
                    append(". @${request.handle}")
                    request.sent_at?.let { append(". Sent ${formatDate(it)}") }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = sentLabel }
            ) {
                UserAvatar(
                    photoUrl = request.profile_photo,
                    initials = request.initials
                )
                Column {
                    Text(
                        text = "@${request.handle}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = request.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    request.sent_at?.let {
                        Text(
                            text = "Sent ${formatDate(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                OutlinedButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.semantics { contentDescription = "Cancel request to ${request.displayName}" }
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun FindUsersTab(
    allUsers: List<SearchUser>,
    friends: List<Friend>,
    requests: List<FriendRequest>,
    sent: List<FriendRequest>,
    blocked: List<BlockedUser>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    actionInProgress: Int?,
    onAddFriend: (Int) -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Filter users: exclude friends, pending requests, sent requests, blocked
    val friendIds = friends.map { it.id }.toSet()
    val requestIds = requests.map { it.id }.toSet()
    val sentIds = sent.map { it.id }.toSet()
    val blockedIds = blocked.map { it.id }.toSet()
    val excludedIds = friendIds + requestIds + sentIds + blockedIds

    val filteredUsers = allUsers
        .filter { it.id !in excludedIds }
        .filter { user ->
            if (searchQuery.isBlank()) false
            else {
                val query = searchQuery.lowercase()
                user.handle.lowercase().contains(query) ||
                        (user.first_name?.lowercase()?.contains(query) == true) ||
                        (user.last_name?.lowercase()?.contains(query) == true) ||
                        (user.email?.lowercase()?.contains(query) == true)
            }
        }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by username, name, or email...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(12.dp)
        )

        if (searchQuery.isBlank()) {
            EmptyState(
                message = "Search for users",
                subMessage = "Enter a username, name, or email to find users"
            )
        } else if (filteredUsers.isEmpty()) {
            EmptyState(
                message = "No users found",
                subMessage = "Try a different search term"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredUsers, key = { it.id }) { user ->
                    SearchUserCard(
                        user = user,
                        isLoading = actionInProgress == user.id,
                        onAddFriend = { onAddFriend(user.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchUserCard(
    user: SearchUser,
    isLoading: Boolean,
    onAddFriend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                UserAvatar(
                    photoUrl = user.profile_photo,
                    initials = user.initials
                )
                Column {
                    Text(
                        text = "@${user.handle}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(
                    onClick = onAddFriend,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Friend", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun BlockedTab(
    blocked: List<BlockedUser>,
    actionInProgress: Int?,
    onUnblock: (Int) -> Unit
) {
    if (blocked.isEmpty()) {
        EmptyState(message = "No blocked users", subMessage = "Users you block will appear here")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(blocked, key = { it.id }) { user ->
                BlockedUserCard(
                    user = user,
                    isLoading = actionInProgress == user.id,
                    onUnblock = { onUnblock(user.id) }
                )
            }
        }
    }
}

@Composable
private fun BlockedUserCard(
    user: BlockedUser,
    isLoading: Boolean,
    onUnblock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val blockedLabel = remember(user.handle, user.displayName, user.blocked_at) {
                buildString {
                    append(user.displayName)
                    append(". @${user.handle}")
                    user.blocked_at?.let { append(". Blocked ${formatDate(it)}") }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {
                        contentDescription = blockedLabel
                        customActions = listOf(
                            CustomAccessibilityAction("Unblock") { onUnblock(); true }
                        )
                    }
            ) {
                // Show initials only for blocked users (no photo preview)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.initials,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    Text(
                        text = "@${user.handle}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    user.blocked_at?.let {
                        Text(
                            text = "Blocked ${formatDate(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                OutlinedButton(
                    onClick = onUnblock,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Unblock", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun UserAvatar(
    photoUrl: String?,
    initials: String,
    modifier: Modifier = Modifier
) {
    val fullUrl = if (!photoUrl.isNullOrBlank())
        "${RetrofitClient.BASE_URL}api/uploads/$photoUrl"
    else null

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (fullUrl != null) {
            AsyncImage(
                model = fullUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    subMessage: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateStr) ?: return dateStr
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
    } catch (e: Exception) {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val date = inputFormat.parse(dateStr) ?: return dateStr
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
        } catch (e: Exception) {
            dateStr
        }
    }
}
