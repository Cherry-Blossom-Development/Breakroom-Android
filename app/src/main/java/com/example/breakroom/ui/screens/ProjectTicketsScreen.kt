package com.example.breakroom.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.breakroom.data.models.Ticket
import kotlinx.coroutines.launch

// Status colors matching web version
private val statusColors = mapOf(
    KanbanStatus.BACKLOG to Color(0xFF6C757D),      // Gray
    KanbanStatus.ON_DECK to Color(0xFF17A2B8),      // Teal
    KanbanStatus.IN_PROGRESS to Color(0xFFFFC107), // Yellow
    KanbanStatus.RESOLVED to Color(0xFF28A745),    // Green
    KanbanStatus.CLOSED to Color(0xFF343A40)       // Dark Gray
)

// Priority colors
private val priorityColors = mapOf(
    "low" to Color(0xFF6C757D),
    "medium" to Color(0xFF17A2B8),
    "high" to Color(0xFFFFC107),
    "urgent" to Color(0xFFDC3545)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectTicketsScreen(
    viewModel: ProjectTicketsViewModel,
    projectName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = uiState.currentStatusIndex,
        pageCount = { KanbanStatus.allStatuses.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // Sync pager state with viewModel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentStatusIndex(pagerState.currentPage)
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${uiState.tickets.size} tickets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Error message
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Status navigation header
            KanbanStatusHeader(
                currentStatus = KanbanStatus.allStatuses[pagerState.currentPage],
                ticketCount = uiState.ticketsByStatus[KanbanStatus.allStatuses[pagerState.currentPage]]?.size ?: 0,
                canGoLeft = pagerState.currentPage > 0,
                canGoRight = pagerState.currentPage < KanbanStatus.allStatuses.size - 1,
                onGoLeft = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onGoRight = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            )

            // Status indicator dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                KanbanStatus.allStatuses.forEachIndexed { index, status ->
                    val isSelected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 10.dp else 8.dp)
                            .background(
                                color = if (isSelected) statusColors[status] ?: MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small
                            )
                    )
                }
            }

            // Horizontal pager for Kanban lanes
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val status = KanbanStatus.allStatuses[page]
                val tickets = uiState.ticketsByStatus[status] ?: emptyList()

                KanbanLane(
                    status = status,
                    tickets = tickets
                )
            }
        }
    }
}

@Composable
private fun KanbanStatusHeader(
    currentStatus: KanbanStatus,
    ticketCount: Int,
    canGoLeft: Boolean,
    canGoRight: Boolean,
    onGoLeft: () -> Unit,
    onGoRight: () -> Unit
) {
    val statusColor = statusColors[currentStatus] ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left arrow
            IconButton(
                onClick = onGoLeft,
                enabled = canGoLeft
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous status",
                    tint = if (canGoLeft) statusColor else MaterialTheme.colorScheme.outlineVariant
                )
            }

            // Status name and count
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentStatus.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = if (ticketCount == 1) "1 ticket" else "$ticketCount tickets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Right arrow
            IconButton(
                onClick = onGoRight,
                enabled = canGoRight
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Next status",
                    tint = if (canGoRight) statusColor else MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun KanbanLane(
    status: KanbanStatus,
    tickets: List<Ticket>
) {
    if (tickets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No tickets in ${status.displayName}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(tickets, key = { it.id }) { ticket ->
                TicketCard(ticket = ticket)
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: Ticket) {
    val priorityColor = priorityColors[ticket.priority] ?: MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title row with priority indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = ticket.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Priority badge
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            ticket.formattedPriority,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = priorityColor.copy(alpha = 0.15f),
                        labelColor = priorityColor
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Description preview
            if (!ticket.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ticket.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer with creator and assignee
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Creator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Creator",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = ticket.creatorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Assignee (if any)
                ticket.assigneeName?.let { assignee ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Assigned:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = assignee,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Ticket ID
            Text(
                text = "#${ticket.id}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
