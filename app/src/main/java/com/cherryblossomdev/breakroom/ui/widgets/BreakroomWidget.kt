package com.cherryblossomdev.breakroom.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.cherryblossomdev.breakroom.data.ChatRepository
import com.cherryblossomdev.breakroom.data.ModerationRepository
import com.cherryblossomdev.breakroom.data.TokenManager
import com.cherryblossomdev.breakroom.data.models.BlockType
import com.cherryblossomdev.breakroom.data.models.BreakroomBlock
import com.cherryblossomdev.breakroom.ui.scroll.ScrollCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BlockType.icon: ImageVector
    get() = when (this) {
        BlockType.CHAT -> Icons.Default.Chat
        BlockType.UPDATES -> Icons.Default.Notifications
        BlockType.CALENDAR -> Icons.Default.DateRange
        BlockType.WEATHER -> Icons.Default.Cloud
        BlockType.NEWS -> Icons.Default.List
        BlockType.BLOG -> Icons.Default.Create
        BlockType.PLACEHOLDER -> Icons.Default.Help
    }

private val BlockType.accentColor: Color
    get() = when (this) {
        BlockType.CHAT -> Color(0xFF2196F3)      // Blue
        BlockType.UPDATES -> Color(0xFFFF9800)   // Orange
        BlockType.CALENDAR -> Color(0xFF9C27B0)  // Purple
        BlockType.WEATHER -> Color(0xFF009688)   // Teal
        BlockType.NEWS -> Color(0xFFF44336)      // Red
        BlockType.BLOG -> Color(0xFF4CAF50)      // Green
        BlockType.PLACEHOLDER -> Color(0xFF9E9E9E) // Gray
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BreakroomWidget(
    block: BreakroomBlock,
    chatRepository: ChatRepository,
    tokenManager: TokenManager,
    moderationRepository: ModerationRepository? = null,
    onNavigateToProfile: (String) -> Unit = {},
    scrollCoordinator: ScrollCoordinator? = null,
    isCollapsed: Boolean = false,
    isReorderMode: Boolean = false,
    isDragging: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    onEnterReorderMode: () -> Unit = {},
    onRemove: ((Int) -> Unit)? = null,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val wrapHeight = block.blockType == BlockType.BLOG || block.blockType == BlockType.CHAT

    // Inner scroll connection implementing Rule 2:
    // Rule 2 — onPreScroll: if the outer page is in a fast fling, consume the drag so this
    //           widget's scrollable area doesn't move (outer keeps priority).
    // Edge bubble-up (Rule 3) is the natural Compose default — remaining scroll after the
    // widget hits its edge flows up to the outer LazyColumn automatically.
    val innerScrollConnection = remember(scrollCoordinator) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val coordinator = scrollCoordinator ?: return Offset.Zero
                coordinator.innerIsDispatching = true
                // Rule 2: block inner drag while the outer page is fast-flinging
                if (source == NestedScrollSource.Drag && coordinator.outerIsFlinging) {
                    return available
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                scrollCoordinator?.innerIsDispatching = false
                // Pass remaining scroll to outer LazyColumn (edge bubble-up)
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                scrollCoordinator?.innerIsDispatching = true
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                scrollCoordinator?.innerIsDispatching = false
                return Velocity.Zero
            }
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 90f,
        animationSpec = tween(durationMillis = 200),
        label = "chevron"
    )

    var headerFlashing by remember { mutableStateOf(false) }
    val flashScope = rememberCoroutineScope()
    val headerColor by animateColorAsState(
        targetValue = if (headerFlashing) Color(0xFFECC94B).copy(alpha = 0.7f)
                      else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(durationMillis = if (headerFlashing) 0 else 2000),
        label = "headerFlash"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 2.dp)
    ) {
        Column(
            modifier = if (wrapHeight || isCollapsed) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
        ) {
            // Header — all widget types use the same collapsible header
            Surface(
                color = headerColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isReorderMode) Modifier
                            else Modifier.combinedClickable(
                                onClick = onToggleCollapse,
                                onLongClick = onEnterReorderMode
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isReorderMode) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(20.dp)
                                .then(dragHandleModifier)
                        )
                    } else {
                        Icon(
                            imageVector = block.blockType.icon,
                            contentDescription = null,
                            tint = block.blockType.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = block.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!isReorderMode) {
                        if (onRemove != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onRemove(block.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = if (isCollapsed) "Expand" else "Collapse",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }
            }

            // Content — animated expand/collapse
            AnimatedVisibility(
                visible = !isCollapsed,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column {
                    Divider()
                    Box(
                        modifier = if (wrapHeight) Modifier.fillMaxWidth().nestedScroll(innerScrollConnection)
                                   else Modifier.fillMaxSize().nestedScroll(innerScrollConnection)
                    ) {
                        when (block.blockType) {
                            BlockType.CHAT -> {
                                block.content_id?.let { roomId ->
                                    ChatRoomWidget(
                                        roomId = roomId,
                                        chatRepository = chatRepository,
                                        moderationRepository = moderationRepository,
                                        currentUserHandle = tokenManager.getUsername() ?: "",
                                        token = tokenManager.getToken(),
                                        onNavigateToProfile = onNavigateToProfile,
                                        onNewMessage = {
                                            headerFlashing = true
                                            flashScope.launch {
                                                delay(2000)
                                                headerFlashing = false
                                            }
                                        }
                                    )
                                } ?: PlaceholderContent("Chat room unavailable. Remove this block and add it again to reconfigure.")
                            }

                            BlockType.UPDATES -> {
                                val token = tokenManager.getToken() ?: ""
                                UpdatesWidget(token = token)
                            }

                            BlockType.CALENDAR -> {
                                CalendarWidget()
                            }

                            BlockType.WEATHER -> {
                                val token = tokenManager.getToken() ?: ""
                                WeatherWidget(token = token)
                            }

                            BlockType.NEWS -> {
                                val token = tokenManager.getToken() ?: ""
                                NewsWidget(token = token)
                            }

                            BlockType.BLOG -> {
                                val token = tokenManager.getToken() ?: ""
                                BlogWidget(
                                    token = token,
                                    currentUserHandle = tokenManager.getUsername()
                                )
                            }

                            BlockType.PLACEHOLDER -> {
                                PlaceholderContent("This block is no longer supported. Remove it and add a new one.")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderContent(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
