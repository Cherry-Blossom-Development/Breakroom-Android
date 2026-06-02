package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.ProfileRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.NotificationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== ViewModel ====================

data class SettingsUiState(
    val isLoading: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notifyChatMessages: Boolean = true,
    val notifyFriendRequests: Boolean = true,
    val notifyBlogComments: Boolean = true,
    val settingsError: String? = null,
    // Account deletion
    val deletionConfirmed: Boolean = false,
    val isDeletionSubmitting: Boolean = false,
    val deletionSuccess: Boolean = false,
    val deletionError: String? = null
)

class SettingsViewModel(private val repo: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repo.getNotificationSettings()) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    notificationsEnabled = result.data.notifications_enabled,
                    notifyChatMessages = result.data.notify_chat_messages,
                    notifyFriendRequests = result.data.notify_friend_requests,
                    notifyBlogComments = result.data.notify_blog_comments
                )
                else -> _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun setNotificationsEnabled(value: Boolean) {
        _uiState.value = _uiState.value.copy(notificationsEnabled = value)
        saveSettings()
    }

    fun setNotifyChatMessages(value: Boolean) {
        _uiState.value = _uiState.value.copy(notifyChatMessages = value)
        saveSettings()
    }

    fun setNotifyFriendRequests(value: Boolean) {
        _uiState.value = _uiState.value.copy(notifyFriendRequests = value)
        saveSettings()
    }

    fun setNotifyBlogComments(value: Boolean) {
        _uiState.value = _uiState.value.copy(notifyBlogComments = value)
        saveSettings()
    }

    private fun saveSettings() {
        val s = _uiState.value
        viewModelScope.launch {
            val result = repo.saveNotificationSettings(
                NotificationSettings(
                    notifications_enabled = s.notificationsEnabled,
                    notify_chat_messages = s.notifyChatMessages,
                    notify_friend_requests = s.notifyFriendRequests,
                    notify_blog_comments = s.notifyBlogComments
                )
            )
            if (result is BreakroomResult.Error) {
                _uiState.value = _uiState.value.copy(settingsError = "Failed to save settings")
            }
        }
    }

    fun setDeletionConfirmed(value: Boolean) {
        _uiState.value = _uiState.value.copy(deletionConfirmed = value)
    }

    fun submitDeletionRequest() {
        if (!_uiState.value.deletionConfirmed) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeletionSubmitting = true, deletionError = null)
            when (val result = repo.submitDeletionRequest()) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    isDeletionSubmitting = false, deletionSuccess = true
                )
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isDeletionSubmitting = false, deletionError = result.message
                )
                else -> _uiState.value = _uiState.value.copy(isDeletionSubmitting = false)
            }
        }
    }
}

// ==================== Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    username: String,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NotificationsCard(state = state, viewModel = viewModel)
            AccountDeletionCard(state = state, username = username, viewModel = viewModel)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NotificationsCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Notifications", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))

            SettingsToggleRow(
                label = "Allow notifications",
                checked = state.notificationsEnabled,
                onCheckedChange = viewModel::setNotificationsEnabled,
                isMaster = true
            )

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .alpha(if (state.notificationsEnabled) 1f else 0.4f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingsToggleRow(
                    label = "New messages in chat",
                    checked = state.notifyChatMessages && state.notificationsEnabled,
                    onCheckedChange = viewModel::setNotifyChatMessages,
                    enabled = state.notificationsEnabled
                )
                SettingsToggleRow(
                    label = "Friend requests",
                    checked = state.notifyFriendRequests && state.notificationsEnabled,
                    onCheckedChange = viewModel::setNotifyFriendRequests,
                    enabled = state.notificationsEnabled
                )
                SettingsToggleRow(
                    label = "Comments on your content",
                    checked = state.notifyBlogComments && state.notificationsEnabled,
                    onCheckedChange = viewModel::setNotifyBlogComments,
                    enabled = state.notificationsEnabled
                )
            }

            state.settingsError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isMaster: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isMaster) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun AccountDeletionCard(
    state: SettingsUiState,
    username: String,
    viewModel: SettingsViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Account Deletion",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error
            )

            Text(
                "Requesting deletion will permanently remove your account and all associated data. " +
                "This action cannot be undone. An administrator will process your request.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            if (state.deletionSuccess) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        "Your deletion request has been submitted. An administrator will process it shortly.",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                OutlinedTextField(
                    value = username,
                    onValueChange = {},
                    label = { Text("Account") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = state.deletionConfirmed,
                        onCheckedChange = viewModel::setDeletionConfirmed,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "I understand this will permanently delete my account and all associated data",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                state.deletionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Button(
                    onClick = viewModel::submitDeletionRequest,
                    enabled = state.deletionConfirmed && !state.isDeletionSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isDeletionSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Submitting…")
                    } else {
                        Text("Request Account Deletion")
                    }
                }
            }
        }
    }
}
