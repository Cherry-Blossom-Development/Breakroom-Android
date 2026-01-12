package com.example.breakroom.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.breakroom.data.ChatRepository
import com.example.breakroom.data.TokenManager
import com.example.breakroom.data.models.BlockType
import com.example.breakroom.data.models.BreakroomBlock

@Composable
fun BreakroomWidget(
    block: BreakroomBlock,
    chatRepository: ChatRepository,
    tokenManager: TokenManager,
    onRemove: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header - skip for widgets with custom styling (weather, calendar)
            if (block.blockType != BlockType.WEATHER && block.blockType != BlockType.CALENDAR) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = block.displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (onRemove != null) {
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
                    }
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (block.blockType) {
                    BlockType.CHAT -> {
                        block.content_id?.let { roomId ->
                            ChatRoomWidget(
                                roomId = roomId,
                                chatRepository = chatRepository
                            )
                        } ?: PlaceholderContent("No chat room configured")
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
                        PlaceholderContent("Blog Posts\n(Coming Soon)")
                    }

                    BlockType.PLACEHOLDER -> {
                        PlaceholderContent("Empty Widget")
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
