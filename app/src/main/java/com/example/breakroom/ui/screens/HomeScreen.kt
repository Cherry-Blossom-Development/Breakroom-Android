package com.example.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.AuthRepository
import com.example.breakroom.data.AuthResult
import com.example.breakroom.data.BreakroomRepository
import com.example.breakroom.data.ChatRepository
import com.example.breakroom.data.TokenManager
import com.example.breakroom.data.models.BlockType
import com.example.breakroom.data.models.BreakroomBlock
import com.example.breakroom.data.models.BreakroomResult
import com.example.breakroom.data.models.Shortcut
import com.example.breakroom.ui.widgets.BreakroomWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val username: String? = null,
    val blocks: List<BreakroomBlock> = emptyList(),
    val shortcuts: List<Shortcut> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingBlocks: Boolean = false,
    val isLoggedOut: Boolean = false,
    val showAddBlockDialog: Boolean = false,
    val isAddingBlock: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val breakroomRepository: BreakroomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load user info
            when (val result = authRepository.getMe()) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        username = result.data.username
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        username = authRepository.getStoredUsername()
                    )
                }
            }

            // Load breakroom layout
            loadLayout()
        }
    }

    private suspend fun loadLayout() {
        _uiState.value = _uiState.value.copy(isLoadingBlocks = true)

        when (val result = breakroomRepository.loadLayout()) {
            is BreakroomResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    blocks = result.data,
                    isLoading = false,
                    isLoadingBlocks = false
                )
            }
            is BreakroomResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingBlocks = false,
                    error = result.message
                )
            }
            is BreakroomResult.AuthenticationError -> {
                // Token is invalid - trigger logout
                logout()
            }
        }

        // Also load shortcuts
        loadShortcuts()
    }

    private suspend fun loadShortcuts() {
        when (val result = breakroomRepository.loadShortcuts()) {
            is BreakroomResult.Success -> {
                _uiState.value = _uiState.value.copy(shortcuts = result.data)
            }
            is BreakroomResult.Error -> {
                // Silently fail - shortcuts are not critical
            }
            is BreakroomResult.AuthenticationError -> {
                // Already handled in loadLayout
            }
        }
    }

    fun refresh() {
        loadData()
    }

    fun removeBlock(blockId: Int) {
        viewModelScope.launch {
            when (val result = breakroomRepository.removeBlock(blockId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        blocks = _uiState.value.blocks.filter { it.id != blockId }
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    logout()
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = _uiState.value.copy(isLoggedOut = true)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun showAddBlockDialog() {
        _uiState.value = _uiState.value.copy(showAddBlockDialog = true)
    }

    fun hideAddBlockDialog() {
        _uiState.value = _uiState.value.copy(showAddBlockDialog = false)
    }

    fun addBlock(blockType: String, title: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingBlock = true)

            when (val result = breakroomRepository.addBlock(blockType, null, title)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showAddBlockDialog = false,
                        isAddingBlock = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isAddingBlock = false
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    logout()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    chatRepository: ChatRepository,
    tokenManager: TokenManager,
    onLogout: () -> Unit,
    onRegisterActions: (onAddBlock: () -> Unit, onRefresh: () -> Unit) -> Unit = { _, _ -> },
    onUpdateRefreshing: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // Register add block and refresh actions with the parent (NavGraph top bar)
    DisposableEffect(Unit) {
        onRegisterActions(viewModel::showAddBlockDialog, viewModel::refresh)
        onDispose {
            onRegisterActions({}, {})
        }
    }

    // Push refreshing state up to NavGraph
    LaunchedEffect(uiState.isLoadingBlocks) {
        onUpdateRefreshing(uiState.isLoadingBlocks)
    }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading && uiState.blocks.isEmpty()) {
            // Initial loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.blocks.isEmpty()) {
            // Empty state
            EmptyBreakroomContent(
                username = uiState.username,
                isLoading = uiState.isLoadingBlocks,
                onRefresh = viewModel::refresh
            )
        } else {
            // Widget grid
            BreakroomContent(
                blocks = uiState.blocks,
                chatRepository = chatRepository,
                tokenManager = tokenManager,
                onRemoveBlock = viewModel::removeBlock
            )
        }

        // Add Block Dialog
        if (uiState.showAddBlockDialog) {
            AddBlockDialog(
                isAdding = uiState.isAddingBlock,
                onDismiss = viewModel::hideAddBlockDialog,
                onAddBlock = { blockType, title -> viewModel.addBlock(blockType, title) }
            )
        }

        // Error snackbar
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = viewModel::clearError) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
private fun BreakroomContent(
    blocks: List<BreakroomBlock>,
    chatRepository: ChatRepository,
    tokenManager: TokenManager,
    onRemoveBlock: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Widgets list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(blocks, key = { it.id }) { block ->
                BreakroomWidget(
                    block = block,
                    chatRepository = chatRepository,
                    tokenManager = tokenManager,
                    onRemove = onRemoveBlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(calculateWidgetHeight(block))
                )
            }
        }
    }
}

@Composable
private fun EmptyBreakroomContent(
    username: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Empty state content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to Breakroom",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            username?.let {
                Text(
                    text = "Hello, $it!",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Your breakroom is empty.\nTap the + icon above to get started!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh")
                }
            }
        }
    }
}

// Calculate widget height based on block size and type
private fun calculateWidgetHeight(block: BreakroomBlock): androidx.compose.ui.unit.Dp {
    val baseHeight = 120.dp
    // Content-heavy widgets (lists of items) need more vertical space on mobile
    val multiplier = when (block.blockType) {
        BlockType.WEATHER, BlockType.CALENDAR -> 1
        else -> 2
    }
    return baseHeight * block.h * multiplier
}

// Widget types available for adding
private data class WidgetType(
    val type: String,
    val label: String,
    val description: String
)

private val widgetTypes = listOf(
    WidgetType("updates", "Breakroom Updates", "Latest news and updates"),
    WidgetType("calendar", "Calendar/Time", "Date and time display"),
    WidgetType("weather", "Weather", "Current weather conditions"),
    WidgetType("news", "News", "NPR news headlines"),
    WidgetType("blog", "Blog Posts", "Your recent blog posts"),
    WidgetType("placeholder", "Placeholder", "Empty block for later")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBlockDialog(
    isAdding: Boolean,
    onDismiss: () -> Unit,
    onAddBlock: (blockType: String, title: String?) -> Unit
) {
    var selectedType by remember { mutableStateOf(widgetTypes.first()) }

    AlertDialog(
        onDismissRequest = { if (!isAdding) onDismiss() },
        title = { Text("Add Block") },
        text = {
            Column {
                Text(
                    "Select a widget type:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                widgetTypes.forEach { widget ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == widget,
                            onClick = { selectedType = widget }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                widget.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                widget.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddBlock(selectedType.type, null) },
                enabled = !isAdding
            ) {
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isAdding) "Adding..." else "Add Block")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isAdding
            ) {
                Text("Cancel")
            }
        }
    )
}
