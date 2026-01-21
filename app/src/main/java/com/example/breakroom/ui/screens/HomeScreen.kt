package com.example.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
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
    onNavigateToShortcut: (Shortcut) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

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
                shortcuts = uiState.shortcuts,
                isLoading = uiState.isLoadingBlocks,
                onRefresh = viewModel::refresh,
                onAddBlock = viewModel::showAddBlockDialog,
                onShortcutClick = onNavigateToShortcut
            )
        } else {
            // Widget grid
            BreakroomContent(
                blocks = uiState.blocks,
                shortcuts = uiState.shortcuts,
                chatRepository = chatRepository,
                tokenManager = tokenManager,
                isRefreshing = uiState.isLoadingBlocks,
                onRefresh = viewModel::refresh,
                onRemoveBlock = viewModel::removeBlock,
                onAddBlock = viewModel::showAddBlockDialog,
                onShortcutClick = onNavigateToShortcut
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreakroomContent(
    blocks: List<BreakroomBlock>,
    shortcuts: List<Shortcut>,
    chatRepository: ChatRepository,
    tokenManager: TokenManager,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRemoveBlock: (Int) -> Unit,
    onAddBlock: () -> Unit,
    onShortcutClick: (Shortcut) -> Unit
) {
    var shortcutsExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with shortcuts dropdown, add block, and refresh
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shortcuts dropdown
                if (shortcuts.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { shortcutsExpanded = true }) {
                            Text("Shortcuts")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = shortcutsExpanded,
                            onDismissRequest = { shortcutsExpanded = false }
                        ) {
                            shortcuts.forEach { shortcut ->
                                DropdownMenuItem(
                                    text = { Text(shortcut.name) },
                                    onClick = {
                                        shortcutsExpanded = false
                                        onShortcutClick(shortcut)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Block button
                    FilledTonalButton(
                        onClick = onAddBlock,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Block", style = MaterialTheme.typography.labelMedium)
                    }

                    // Refresh button
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            }
        }

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
    shortcuts: List<Shortcut>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onAddBlock: () -> Unit,
    onShortcutClick: (Shortcut) -> Unit
) {
    var shortcutsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header with shortcuts and add block (same as BreakroomContent)
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shortcuts dropdown
                if (shortcuts.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { shortcutsExpanded = true }) {
                            Text("Shortcuts")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = shortcutsExpanded,
                            onDismissRequest = { shortcutsExpanded = false }
                        ) {
                            shortcuts.forEach { shortcut ->
                                DropdownMenuItem(
                                    text = { Text(shortcut.name) },
                                    onClick = {
                                        shortcutsExpanded = false
                                        onShortcutClick(shortcut)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Add Block button
                FilledTonalButton(
                    onClick = onAddBlock,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Block", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

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
                text = "Your breakroom is empty.\nTap 'Add Block' to get started!",
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

// Calculate widget height based on block size
private fun calculateWidgetHeight(block: BreakroomBlock): androidx.compose.ui.unit.Dp {
    // Base height is roughly 150dp per grid row (matching web's rowHeight: 150)
    val baseHeight = 120.dp
    return baseHeight * block.h
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
