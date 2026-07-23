package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cherryblossomdev.breakroom.ui.scroll.ScrollCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.AuthRepository
import com.cherryblossomdev.breakroom.data.AuthResult
import com.cherryblossomdev.breakroom.data.BreakroomRepository
import com.cherryblossomdev.breakroom.data.ChatRepository
import com.cherryblossomdev.breakroom.data.TokenManager
import com.cherryblossomdev.breakroom.data.models.BlockType
import com.cherryblossomdev.breakroom.data.models.BreakroomBlock
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.ChatResult
import com.cherryblossomdev.breakroom.data.models.ChatRoom
import com.cherryblossomdev.breakroom.data.models.Shortcut
import com.cherryblossomdev.breakroom.ui.widgets.BreakroomWidget
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
    val error: String? = null,
    val collapsedBlockIds: Set<Int> = emptySet(),
    val isReorderMode: Boolean = false
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, isLoggedOut = false)

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
                    blocks = result.data.distinctBy { it.id },
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
            else -> { }
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
            else -> { }
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
                else -> { }
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

    fun toggleBlockCollapse(blockId: Int) {
        val current = _uiState.value.collapsedBlockIds
        _uiState.value = _uiState.value.copy(
            collapsedBlockIds = if (blockId in current) current - blockId else current + blockId
        )
    }

    fun enterReorderMode() {
        _uiState.value = _uiState.value.copy(isReorderMode = true)
    }

    fun exitReorderModeAndSave() {
        viewModelScope.launch {
            val blocks = _uiState.value.blocks
            _uiState.value = _uiState.value.copy(isReorderMode = false)
            breakroomRepository.updateLayout(blocks)
        }
    }

    fun reorderBlock(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val list = _uiState.value.blocks.toMutableList()
        list.add(toIndex, list.removeAt(fromIndex))
        _uiState.value = _uiState.value.copy(blocks = list)
    }

    fun showAddBlockDialog() {
        _uiState.value = _uiState.value.copy(showAddBlockDialog = true)
    }

    fun hideAddBlockDialog() {
        _uiState.value = _uiState.value.copy(showAddBlockDialog = false)
    }

    fun addBlock(blockType: String, contentId: Int? = null, title: String? = null, height: Int = 2) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingBlock = true)

            when (val result = breakroomRepository.addBlock(blockType, contentId, title, height = height)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        blocks = _uiState.value.blocks + result.data,
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
                else -> { }
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
    moderationRepository: com.cherryblossomdev.breakroom.data.ModerationRepository? = null,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToScheduledMessages: () -> Unit = {},
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

    Box(modifier = Modifier.fillMaxSize().testTag("screen-home")) {
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
                moderationRepository = moderationRepository,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToScheduledMessages = onNavigateToScheduledMessages,
                collapsedBlockIds = uiState.collapsedBlockIds,
                isReorderMode = uiState.isReorderMode,
                onRemoveBlock = viewModel::removeBlock,
                onToggleCollapse = viewModel::toggleBlockCollapse,
                onEnterReorderMode = viewModel::enterReorderMode,
                onReorderBlock = viewModel::reorderBlock,
                onExitReorderMode = viewModel::exitReorderModeAndSave
            )
        }

        // Add Block Dialog
        if (uiState.showAddBlockDialog) {
            AddBlockDialog(
                isAdding = uiState.isAddingBlock,
                existingBlockTypes = uiState.blocks.map { it.blockType.name.lowercase() },
                existingChatRoomIds = uiState.blocks
                    .filter { it.blockType == BlockType.CHAT }
                    .mapNotNull { it.content_id },
                chatRepository = chatRepository,
                onDismiss = viewModel::hideAddBlockDialog,
                onAddBlock = { blockType, contentId, title, height -> viewModel.addBlock(blockType, contentId, title, height) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BreakroomContent(
    blocks: List<BreakroomBlock>,
    chatRepository: ChatRepository,
    tokenManager: TokenManager,
    moderationRepository: com.cherryblossomdev.breakroom.data.ModerationRepository? = null,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToScheduledMessages: () -> Unit = {},
    collapsedBlockIds: Set<Int>,
    isReorderMode: Boolean,
    onRemoveBlock: (Int) -> Unit,
    onToggleCollapse: (Int) -> Unit,
    onEnterReorderMode: () -> Unit,
    onReorderBlock: (Int, Int) -> Unit,
    onExitReorderMode: () -> Unit
) {
    val listState = rememberLazyListState()
    var draggingBlockId by remember { mutableStateOf<Int?>(null) }

    // Scroll coordinator: tracks outer page fling state so inner widget scrolls can
    // respect Rules 2 (fast outer fling blocks inner drags) and 3 (edge blocking).
    val coordinator = remember { ScrollCoordinator() }
    val scope = rememberCoroutineScope()
    val outerTrackingConnection = remember(coordinator, scope) {
        var clearJob: Job? = null
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                when (source) {
                    NestedScrollSource.Fling -> {
                        // Only mark as outer fling when an inner widget isn't the dispatcher
                        if (!coordinator.innerIsDispatching) {
                            coordinator.outerIsFlinging = true
                            clearJob?.cancel()
                            clearJob = scope.launch {
                                delay(300L)
                                coordinator.outerIsFlinging = false
                            }
                        }
                    }
                    NestedScrollSource.Drag -> {
                        // User directly dragging the outer page — clear any fling state
                        if (!coordinator.innerIsDispatching) {
                            clearJob?.cancel()
                            coordinator.outerIsFlinging = false
                        }
                    }
                    else -> { /* no-op */ }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!coordinator.innerIsDispatching) {
                    clearJob?.cancel()
                    coordinator.outerIsFlinging = false
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(outerTrackingConnection)) {
        LazyColumn(
            state = listState,
            userScrollEnabled = !isReorderMode,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = if (isReorderMode) 80.dp else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                val currentIndex by rememberUpdatedState(index)
                val currentBlocksSize by rememberUpdatedState(blocks.size)
                val isEffectivelyCollapsed = isReorderMode || (block.id in collapsedBlockIds)
                val isDragging = draggingBlockId == block.id
                val dragAccumY = remember { floatArrayOf(0f) }

                val dragHandleModifier = if (isReorderMode) {
                    Modifier.pointerInput(block.id) {
                        detectDragGestures(
                            onDragStart = {
                                draggingBlockId = block.id
                                dragAccumY[0] = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumY[0] += dragAmount.y
                                val itemHeight = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == block.id }?.size ?: 80
                                val halfHeight = itemHeight / 2
                                if (dragAccumY[0] > halfHeight && currentIndex < currentBlocksSize - 1) {
                                    onReorderBlock(currentIndex, currentIndex + 1)
                                    dragAccumY[0] -= itemHeight.toFloat()
                                } else if (dragAccumY[0] < -halfHeight && currentIndex > 0) {
                                    onReorderBlock(currentIndex, currentIndex - 1)
                                    dragAccumY[0] += itemHeight.toFloat()
                                }
                            },
                            onDragEnd = {
                                draggingBlockId = null
                                dragAccumY[0] = 0f
                            },
                            onDragCancel = {
                                draggingBlockId = null
                                dragAccumY[0] = 0f
                            }
                        )
                    }
                } else Modifier

                BreakroomWidget(
                    block = block,
                    chatRepository = chatRepository,
                    tokenManager = tokenManager,
                    moderationRepository = moderationRepository,
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToScheduledMessages = onNavigateToScheduledMessages,
                    scrollCoordinator = coordinator,
                    isCollapsed = isEffectivelyCollapsed,
                    isReorderMode = isReorderMode,
                    isDragging = isDragging,
                    onToggleCollapse = { onToggleCollapse(block.id) },
                    onEnterReorderMode = onEnterReorderMode,
                    onRemove = if (isReorderMode) null else onRemoveBlock,
                    canMoveUp = index > 0,
                    canMoveDown = index < blocks.size - 1,
                    onMoveUp = { onReorderBlock(index, index - 1) },
                    onMoveDown = { onReorderBlock(index, index + 1) },
                    dragHandleModifier = dragHandleModifier,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement()
                        .then(
                            if (isEffectivelyCollapsed) Modifier.wrapContentHeight()
                            else when (block.blockType) {
                                BlockType.BLOG, BlockType.CHAT -> Modifier.wrapContentHeight()
                                else -> Modifier.height(calculateWidgetHeight(block))
                            }
                        )
                )
            }
        }

        // "Done" banner slides up from the bottom in reorder mode
        AnimatedVisibility(
            visible = isReorderMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onExitReorderMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        "Done Reordering",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
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

private data class WidgetType(
    val type: String,
    val label: String,
    val description: String
)

private val widgetTypes = listOf(
    WidgetType("chat_summary", "Chat Summary", "Browse and reply to unread messages"),
    WidgetType("scheduled_messages", "Scheduled Messages", "Write messages to be delivered at a future time"),
    WidgetType("updates", "Breakroom Updates", "Latest news and updates"),
    WidgetType("calendar", "Calendar/Time", "Date and time display"),
    WidgetType("weather", "Weather", "Current weather conditions"),
    WidgetType("news", "News", "NPR news headlines"),
    WidgetType("blog", "Blog Posts", "Your recent blog posts")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBlockDialog(
    isAdding: Boolean,
    existingBlockTypes: List<String>,
    existingChatRoomIds: List<Int>,
    chatRepository: ChatRepository,
    onDismiss: () -> Unit,
    onAddBlock: (blockType: String, contentId: Int?, title: String?, height: Int) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) } // "chat" or "widget"
    var selectedRoomId by remember { mutableStateOf<Int?>(null) }
    var selectedWidgetType by remember { mutableStateOf<WidgetType?>(null) }
    var blockHeight by remember { mutableStateOf(2) }
    var customTitle by remember { mutableStateOf("") }

    var availableRooms by remember { mutableStateOf<List<ChatRoom>>(emptyList()) }
    var isLoadingRooms by remember { mutableStateOf(false) }

    val availableWidgets = remember(existingBlockTypes) {
        widgetTypes.filter { it.type !in existingBlockTypes }
    }

    LaunchedEffect(selectedCategory) {
        if (selectedCategory == "chat") {
            isLoadingRooms = true
            selectedRoomId = null
            when (val result = chatRepository.loadRooms()) {
                is ChatResult.Success -> availableRooms = result.data.filter { it.id !in existingChatRoomIds }
                is ChatResult.Error -> availableRooms = emptyList()
            }
            isLoadingRooms = false
        }
    }

    val canAdd = !isAdding && when (selectedCategory) {
        "chat" -> selectedRoomId != null
        "widget" -> selectedWidgetType != null
        else -> false
    }

    Dialog(onDismissRequest = { if (!isAdding) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Text(
                    "Add Block",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)
                )
                Divider()

                // Step 1: category cards
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "What would you like to add?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AddBlockCategoryCard(
                            label = "Chat Room",
                            description = "Live chat with your network",
                            icon = Icons.Default.Chat,
                            selected = selectedCategory == "chat",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (selectedCategory != "chat") {
                                    selectedCategory = "chat"
                                    selectedWidgetType = null
                                }
                            }
                        )
                        AddBlockCategoryCard(
                            label = "Widget",
                            description = "News, weather, calendar & more",
                            icon = Icons.Default.Widgets,
                            selected = selectedCategory == "widget",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (selectedCategory != "widget") {
                                    selectedCategory = "widget"
                                    selectedRoomId = null
                                }
                            }
                        )
                    }
                }

                if (selectedCategory != null) {
                    Divider()

                    // Step 2: scrollable list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        when (selectedCategory) {
                            "chat" -> {
                                if (isLoadingRooms) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                } else if (availableRooms.isEmpty()) {
                                    item {
                                        Text(
                                            "All chat rooms are already on your page.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                } else {
                                    items(availableRooms) { room ->
                                        AddBlockListItem(
                                            title = "#${room.name}",
                                            subtitle = room.description?.takeIf { it.isNotEmpty() },
                                            selected = selectedRoomId == room.id,
                                            onClick = { selectedRoomId = room.id }
                                        )
                                    }
                                }
                            }
                            "widget" -> {
                                if (availableWidgets.isEmpty()) {
                                    item {
                                        Text(
                                            "All widget types are already on your page.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                } else {
                                    items(availableWidgets) { widget ->
                                        AddBlockListItem(
                                            title = widget.label,
                                            subtitle = widget.description,
                                            selected = selectedWidgetType == widget,
                                            onClick = { selectedWidgetType = widget }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom: height + title + buttons
                    Divider()
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(
                                "Height",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (1..4).forEach { h ->
                                    if (h == blockHeight) {
                                        Button(
                                            onClick = { blockHeight = h },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) { Text("$h") }
                                    } else {
                                        OutlinedButton(
                                            onClick = { blockHeight = h },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) { Text("$h") }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it },
                            label = { Text("Custom title (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isAdding,
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    val type = when (selectedCategory) {
                                        "chat" -> "chat"
                                        "widget" -> selectedWidgetType?.type ?: return@Button
                                        else -> return@Button
                                    }
                                    onAddBlock(
                                        type,
                                        if (selectedCategory == "chat") selectedRoomId else null,
                                        customTitle.trim().takeIf { it.isNotEmpty() },
                                        blockHeight
                                    )
                                },
                                enabled = canAdd,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isAdding) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(if (isAdding) "Adding..." else "Add Block")
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBlockCategoryCard(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddBlockListItem(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
