package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.BreakroomRepository
import com.cherryblossomdev.breakroom.data.FeaturesRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.Shortcut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag

// Tool category data class
data class ToolCategory(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val tools: List<Tool>
)

// Individual tool data class
data class Tool(
    val id: String,
    val name: String,
    val description: String,
    val route: String,
    val shortcutName: String = name,
    val featureKey: String? = null  // null = available to all; non-null = requires feature flag
)

// All possible tools (featureKey restricts visibility to users with that feature)
private val allToolCategories = listOf(
    ToolCategory(
        id = "musician",
        name = "Musician Tools",
        description = "Tools for musicians, composers, and audio enthusiasts",
        icon = Icons.Outlined.MusicNote,
        tools = listOf(
            Tool(
                id = "lyric-lab",
                name = "Lyric Lab",
                description = "Capture lyric ideas, organize them into songs, and collaborate with other songwriters.",
                route = "/lyrics",
                shortcutName = "Lyric Lab"
            ),
            Tool(
                id = "sessions",
                name = "Sessions",
                description = "Track and manage your recording sessions, log progress, and keep notes on each session.",
                route = "/sessions",
                shortcutName = "Sessions",
                featureKey = "sessions"
            )
        )
    ),
    ToolCategory(
        id = "artist",
        name = "Artist Tools",
        description = "Creative tools for visual artists and designers",
        icon = Icons.Outlined.Palette,
        tools = listOf(
            Tool(
                id = "art-gallery",
                name = "Art Gallery",
                description = "Showcase your artwork with a public gallery page and manage your collection.",
                route = "/art-gallery",
                shortcutName = "Art Gallery"
            )
        )
    ),
    ToolCategory(
        id = "writer",
        name = "Writer Tools",
        description = "Utilities for writers, bloggers, and content creators",
        icon = Icons.Outlined.Create,
        tools = listOf(
            Tool(
                id = "blog",
                name = "Blog",
                description = "Write and publish blog posts with a rich text editor and your own public blog URL.",
                route = "/blog",
                shortcutName = "Blog"
            )
        )
    ),
    ToolCategory(
        id = "developer",
        name = "Developer Tools",
        description = "Productivity tools for programmers and developers",
        icon = Icons.Outlined.Code,
        tools = listOf(
            Tool(
                id = "kanban",
                name = "Kanban",
                description = "Manage your projects and tasks with a visual Kanban board.",
                route = "/kanban",
                shortcutName = "Kanban"
            )
        )
    )
)

// ==================== ViewModel ====================

data class ToolShedUiState(
    // Map of tool.id -> existing Shortcut (null = not bookmarked)
    val shortcutMap: Map<String, Shortcut?> = emptyMap(),
    val addingShortcutId: String? = null,
    val categories: List<ToolCategory> = emptyList()
)

class ToolShedViewModel(
    private val breakroomRepository: BreakroomRepository,
    private val featuresRepository: FeaturesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolShedUiState())
    val uiState: StateFlow<ToolShedUiState> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            val myFeatures = featuresRepository.getMyFeatures()
            val visibleCategories = allToolCategories.map { category ->
                category.copy(tools = category.tools.filter { tool ->
                    tool.featureKey == null || myFeatures.contains(tool.featureKey)
                })
            }
            _uiState.value = _uiState.value.copy(categories = visibleCategories)
            checkShortcuts(visibleCategories.flatMap { it.tools })
        }
    }

    private suspend fun checkShortcuts(tools: List<Tool>) {
        val map = mutableMapOf<String, Shortcut?>()
        tools.forEach { tool ->
            val result = breakroomRepository.checkShortcut(tool.route)
            if (result is BreakroomResult.Success) {
                map[tool.id] = if (result.data.exists) result.data.shortcut else null
            } else {
                map[tool.id] = null
            }
        }
        _uiState.value = _uiState.value.copy(shortcutMap = map)
    }

    fun checkShortcuts() {
        viewModelScope.launch {
            checkShortcuts(_uiState.value.categories.flatMap { it.tools })
        }
    }

    fun addShortcut(tool: Tool, onShortcutsChanged: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(addingShortcutId = tool.id)
            val result = breakroomRepository.createShortcut(
                name = tool.shortcutName,
                url = tool.route
            )
            if (result is BreakroomResult.Success) {
                _uiState.value = _uiState.value.copy(
                    shortcutMap = _uiState.value.shortcutMap + (tool.id to result.data),
                    addingShortcutId = null
                )
                onShortcutsChanged()
            } else {
                _uiState.value = _uiState.value.copy(addingShortcutId = null)
            }
        }
    }

    fun removeShortcut(tool: Tool, onShortcutsChanged: () -> Unit) {
        val shortcut = _uiState.value.shortcutMap[tool.id] ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(addingShortcutId = tool.id)
            val result = breakroomRepository.deleteShortcut(shortcut.id)
            if (result is BreakroomResult.Success) {
                _uiState.value = _uiState.value.copy(
                    shortcutMap = _uiState.value.shortcutMap + (tool.id to null),
                    addingShortcutId = null
                )
                onShortcutsChanged()
            } else {
                _uiState.value = _uiState.value.copy(addingShortcutId = null)
            }
        }
    }
}

// ==================== Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolShedScreen(
    viewModel: ToolShedViewModel,
    onNavigateToTool: (Tool) -> Unit = {},
    onShortcutsChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("screen-tool-shed"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Tool Shed",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your collection of productivity tools",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Welcome/Intro Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Welcome to the Tool Shed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The Tool Shed is where we're building a collection of helpful productivity tools that you can choose to use based on your interests and needs. Whether you're a musician, artist, writer, or developer, you'll find utilities here designed to make your creative and professional work easier.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tools are completely optional - browse the categories below and enable only the ones that are useful to you. More tools will be added over time based on community feedback and requests.",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section header
        item {
            Text(
                text = "Tool Categories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Tool Categories (only show categories that have tools)
        items(state.categories.filter { it.tools.isNotEmpty() }) { category ->
            ToolCategoryCard(
                category = category,
                shortcutMap = state.shortcutMap,
                addingShortcutId = state.addingShortcutId,
                onToolClick = onNavigateToTool,
                onAddShortcut = { tool -> viewModel.addShortcut(tool, onShortcutsChanged) },
                onRemoveShortcut = { tool -> viewModel.removeShortcut(tool, onShortcutsChanged) }
            )
        }

        // Feedback section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Have a Tool Suggestion?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "We're always looking for ideas! If there's a productivity tool you'd like to see added to the Tool Shed, let us know through the Help Desk.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ToolCategoryCard(
    category: ToolCategory,
    shortcutMap: Map<String, Shortcut?>,
    addingShortcutId: String?,
    onToolClick: (Tool) -> Unit,
    onAddShortcut: (Tool) -> Unit,
    onRemoveShortcut: (Tool) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Category header
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Category info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tools list or "Coming Soon"
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                category.tools.forEach { tool ->
                    val hasShortcut = shortcutMap[tool.id] != null
                    val isLoading = addingShortcutId == tool.id
                    ToolItem(
                        tool = tool,
                        hasShortcut = hasShortcut,
                        isShortcutLoading = isLoading,
                        onClick = { onToolClick(tool) },
                        onShortcutToggle = {
                            if (hasShortcut) onRemoveShortcut(tool)
                            else onAddShortcut(tool)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolItem(
    tool: Tool,
    hasShortcut: Boolean,
    isShortcutLoading: Boolean,
    onClick: () -> Unit,
    onShortcutToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Shortcut bookmark icon button
            IconButton(
                onClick = onShortcutToggle,
                enabled = !isShortcutLoading
            ) {
                if (isShortcutLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (hasShortcut) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = "Remove from shortcuts",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = "Add to shortcuts",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Open button
            FilledTonalButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Open")
            }
        }
    }
}
