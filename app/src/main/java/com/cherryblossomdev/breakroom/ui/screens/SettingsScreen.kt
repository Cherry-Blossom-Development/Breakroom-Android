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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    // Alternate email
    val alternateEmail: String? = null,
    val alternateEmailVerified: Boolean = false,
    val sendNoticesToAlternateEmail: Boolean = false,
    val alternateEmailInput: String = "",
    val isEditingAlternateEmail: Boolean = false,
    val isSavingAlternateEmail: Boolean = false,
    val isResendingAlternateEmail: Boolean = false,
    val alternateEmailError: String? = null,
    val alternateEmailMessage: String? = null,
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

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val settingsResult = repo.getNotificationSettings()
            val alternateEmailResult = repo.getAlternateEmail()

            val settings = (settingsResult as? BreakroomResult.Success)?.data
            val alternateEmail = (alternateEmailResult as? BreakroomResult.Success)?.data

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                notificationsEnabled = settings?.notifications_enabled ?: _uiState.value.notificationsEnabled,
                notifyChatMessages = settings?.notify_chat_messages ?: _uiState.value.notifyChatMessages,
                notifyFriendRequests = settings?.notify_friend_requests ?: _uiState.value.notifyFriendRequests,
                notifyBlogComments = settings?.notify_blog_comments ?: _uiState.value.notifyBlogComments,
                alternateEmail = alternateEmail?.alternate_email,
                alternateEmailVerified = alternateEmail?.alternate_email_verified ?: false,
                sendNoticesToAlternateEmail = alternateEmail?.send_notices_to_alternate_email ?: false,
                isEditingAlternateEmail = alternateEmail?.alternate_email.isNullOrBlank()
            )
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

    fun startEditAlternateEmail() {
        _uiState.value = _uiState.value.copy(
            isEditingAlternateEmail = true,
            alternateEmailInput = _uiState.value.alternateEmail ?: "",
            alternateEmailError = null,
            alternateEmailMessage = null
        )
    }

    fun cancelEditAlternateEmail() {
        _uiState.value = _uiState.value.copy(isEditingAlternateEmail = false, alternateEmailError = null)
    }

    fun setAlternateEmailInput(value: String) {
        _uiState.value = _uiState.value.copy(alternateEmailInput = value)
    }

    fun saveAlternateEmail() {
        val email = _uiState.value.alternateEmailInput.trim()
        if (email.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingAlternateEmail = true, alternateEmailError = null, alternateEmailMessage = null)
            when (val result = repo.setAlternateEmail(email)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSavingAlternateEmail = false, alternateEmailMessage = "Verification email sent")
                    load()
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(isSavingAlternateEmail = false, alternateEmailError = result.message)
                else -> _uiState.value = _uiState.value.copy(isSavingAlternateEmail = false)
            }
        }
    }

    fun resendAlternateEmailVerification() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResendingAlternateEmail = true, alternateEmailError = null, alternateEmailMessage = null)
            when (val result = repo.resendAlternateEmailVerification()) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(isResendingAlternateEmail = false, alternateEmailMessage = "Verification email resent")
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(isResendingAlternateEmail = false, alternateEmailError = result.message)
                else -> _uiState.value = _uiState.value.copy(isResendingAlternateEmail = false)
            }
        }
    }

    fun removeAlternateEmail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingAlternateEmail = true, alternateEmailError = null, alternateEmailMessage = null)
            when (val result = repo.setAlternateEmail("")) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSavingAlternateEmail = false)
                    load()
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(isSavingAlternateEmail = false, alternateEmailError = result.message)
                else -> _uiState.value = _uiState.value.copy(isSavingAlternateEmail = false)
            }
        }
    }

    fun setSendNoticesToAlternateEmail(value: Boolean) {
        val previous = _uiState.value.sendNoticesToAlternateEmail
        _uiState.value = _uiState.value.copy(sendNoticesToAlternateEmail = value, alternateEmailError = null)
        viewModelScope.launch {
            val result = repo.setAlternateEmailNotify(value)
            if (result is BreakroomResult.Error) {
                _uiState.value = _uiState.value.copy(sendNoticesToAlternateEmail = previous, alternateEmailError = result.message)
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh alternate-email verification status when returning from the browser
    // (there's no in-app deep link for the confirmation email, same as primary email).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            AlternateEmailCard(state = state, viewModel = viewModel)
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
private fun AlternateEmailCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Alternate Email", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                "Add a second address so account notices can also reach you there.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                state.isEditingAlternateEmail -> {
                    OutlinedTextField(
                        value = state.alternateEmailInput,
                        onValueChange = viewModel::setAlternateEmailInput,
                        label = { Text("Alternate email address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::saveAlternateEmail,
                            enabled = state.alternateEmailInput.isNotBlank() && !state.isSavingAlternateEmail
                        ) {
                            Text(if (state.isSavingAlternateEmail) "Sending…" else "Send Verification Email")
                        }
                        if (!state.alternateEmail.isNullOrBlank()) {
                            OutlinedButton(onClick = viewModel::cancelEditAlternateEmail) { Text("Cancel") }
                        }
                    }
                }
                !state.alternateEmailVerified -> {
                    Text("Pending confirmation: ${state.alternateEmail}", fontSize = 14.sp)
                    Text(
                        "Check that inbox for a confirmation link.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::resendAlternateEmailVerification, enabled = !state.isResendingAlternateEmail) {
                            Text(if (state.isResendingAlternateEmail) "Sending…" else "Resend Email")
                        }
                        OutlinedButton(onClick = viewModel::startEditAlternateEmail) { Text("Change") }
                        OutlinedButton(onClick = viewModel::removeAlternateEmail) { Text("Remove") }
                    }
                }
                else -> {
                    Text("Confirmed: ${state.alternateEmail}", fontSize = 14.sp)
                    SettingsToggleRow(
                        label = "Also send notices to this address",
                        checked = state.sendNoticesToAlternateEmail,
                        onCheckedChange = viewModel::setSendNoticesToAlternateEmail
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::startEditAlternateEmail) { Text("Change") }
                        OutlinedButton(onClick = viewModel::removeAlternateEmail) { Text("Remove") }
                    }
                }
            }

            state.alternateEmailMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            state.alternateEmailError?.let {
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
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label }
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
                        modifier = Modifier
                            .size(24.dp)
                            .semantics {
                                contentDescription = "I understand this will permanently delete my account and all associated data"
                            }
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
