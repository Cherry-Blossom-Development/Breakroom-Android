package com.example.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.example.breakroom.data.models.BreakroomBlock
import com.example.breakroom.data.models.BreakroomResult
import com.example.breakroom.ui.widgets.BreakroomWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val username: String? = null,
    val blocks: List<BreakroomBlock> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingBlocks: Boolean = false,
    val isLoggedOut: Boolean = false,
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
        }
    }

    fun refresh() {
        loadData()
    }

    fun removeBlock(blockId: Int) {
        viewModelScope.launch {
            when (breakroomRepository.removeBlock(blockId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        blocks = _uiState.value.blocks.filter { it.id != blockId }
                    )
                }
                is BreakroomResult.Error -> {
                    // Could show error snackbar
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    chatRepository: ChatRepository,
    onLogout: () -> Unit
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
                isLoading = uiState.isLoadingBlocks,
                onRefresh = viewModel::refresh
            )
        } else {
            // Widget grid
            BreakroomContent(
                blocks = uiState.blocks,
                chatRepository = chatRepository,
                isRefreshing = uiState.isLoadingBlocks,
                onRefresh = viewModel::refresh,
                onRemoveBlock = viewModel::removeBlock
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
    chatRepository: ChatRepository,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRemoveBlock: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header with refresh
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Breakroom",
                    style = MaterialTheme.typography.titleLarge
                )

                Row {
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
            text = "Your breakroom is empty.\nAdd widgets from the web app to get started!",
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

// Calculate widget height based on block size
private fun calculateWidgetHeight(block: BreakroomBlock): androidx.compose.ui.unit.Dp {
    // Base height is roughly 150dp per grid row (matching web's rowHeight: 150)
    val baseHeight = 120.dp
    return baseHeight * block.h
}
