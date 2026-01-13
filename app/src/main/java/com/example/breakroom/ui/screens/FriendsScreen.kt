package com.example.breakroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.breakroom.data.models.*
import java.text.SimpleDateFormat
import java.util.*

enum class FriendsTab(val title: String) {
    FRIENDS("Friends"),
    REQUESTS("Requests"),
    SENT("Sent"),
    FIND("Find Users"),
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Friends",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.loadAll() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }

                    // Tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        FriendsTab.entries.forEach { tab ->
                            val badgeCount = when (tab) {
                                FriendsTab.REQUESTS -> uiState.requests.size
                                else -> 0
                            }
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(tab.title)
                                        if (badgeCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error
                                            ) {
                                                Text(badgeCount.toString())
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Accept", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
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
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
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
