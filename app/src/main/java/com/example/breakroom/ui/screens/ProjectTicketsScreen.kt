package com.example.breakroom.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.breakroom.data.models.CompanyEmployee
import com.example.breakroom.data.models.Ticket
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Status colors matching web version
private val statusColors = mapOf(
    KanbanStatus.BACKLOG to Color(0xFF6C757D),      // Gray
    KanbanStatus.ON_DECK to Color(0xFF17A2B8),      // Teal
    KanbanStatus.IN_PROGRESS to Color(0xFFFFC107), // Yellow
    KanbanStatus.RESOLVED to Color(0xFF28A745),    // Green
    KanbanStatus.CLOSED to Color(0xFF343A40)       // Dark Gray
)

// Status colors by string key for detail view
private val statusColorsByKey = mapOf(
    "backlog" to Color(0xFF6C757D),
    "on-deck" to Color(0xFF17A2B8),
    "in_progress" to Color(0xFFFFC107),
    "resolved" to Color(0xFF28A745),
    "closed" to Color(0xFF343A40)
)

// Priority colors
private val priorityColors = mapOf(
    "low" to Color(0xFF6C757D),
    "medium" to Color(0xFF0D6EFD),
    "high" to Color(0xFFFD7E14),
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

    // Show snackbar for messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error, uiState.successMessage) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main Kanban view
            Column(modifier = Modifier.fillMaxSize()) {
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
                            tickets = tickets,
                            onTicketClick = { viewModel.selectTicket(it) }
                        )
                    }
                }
            }

            // Ticket Detail overlay
            AnimatedVisibility(
                visible = uiState.selectedTicket != null,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it }
            ) {
                uiState.selectedTicket?.let { ticket ->
                    TicketDetailContent(
                        ticket = ticket,
                        employees = uiState.employees,
                        isUpdating = uiState.isUpdatingTicket,
                        onBack = { viewModel.clearSelectedTicket() },
                        onStatusChange = { viewModel.updateTicketStatus(it) },
                        onAssign = { viewModel.assignTicket(it) }
                    )
                }
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
            IconButton(onClick = onGoLeft, enabled = canGoLeft) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous status",
                    tint = if (canGoLeft) statusColor else MaterialTheme.colorScheme.outlineVariant
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

            IconButton(onClick = onGoRight, enabled = canGoRight) {
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
    tickets: List<Ticket>,
    onTicketClick: (Ticket) -> Unit
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
                TicketCard(
                    ticket = ticket,
                    onClick = { onTicketClick(ticket) }
                )
            }
        }
    }
}

@Composable
private fun TicketCard(
    ticket: Ticket,
    onClick: () -> Unit
) {
    val priorityColor = priorityColors[ticket.priority] ?: MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(ticket.formattedPriority, style = MaterialTheme.typography.labelSmall)
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = priorityColor.copy(alpha = 0.15f),
                        labelColor = priorityColor
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                ticket.assigneeName?.let { assignee ->
                    Text(
                        text = "→ $assignee",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = "#${ticket.id}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ============ TICKET DETAIL CONTENT ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketDetailContent(
    ticket: Ticket,
    employees: List<CompanyEmployee>,
    isUpdating: Boolean,
    onBack: () -> Unit,
    onStatusChange: (String) -> Unit,
    onAssign: (Int?) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Ticket Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isUpdating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = ticket.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // Status and Priority badges
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(status = ticket.status)
                    PriorityBadge(priority = ticket.priority)
                }

                // Info section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InfoRow(label = "Created by", value = ticket.creatorName)
                        InfoRow(label = "Created", value = formatDate(ticket.created_at))
                        ticket.resolved_at?.let {
                            InfoRow(label = "Resolved", value = formatDate(it))
                        }
                        InfoRow(label = "Assigned to", value = ticket.assigneeName ?: "Unassigned")
                    }
                }

                // Assign section
                AssignSection(
                    currentAssigneeId = ticket.assignee_id ?: ticket.assigned_to,
                    employees = employees,
                    isUpdating = isUpdating,
                    onAssign = onAssign
                )

                // Description
                if (!ticket.description.isNullOrBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Description",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = ticket.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Status transitions
                val validTransitions = StatusTransitions.getValidTransitions(ticket.status)
                if (validTransitions.isNotEmpty()) {
                    StatusTransitionSection(
                        validTransitions = validTransitions,
                        isUpdating = isUpdating,
                        onStatusChange = onStatusChange
                    )
                }

                // Ticket ID
                Text(
                    text = "Ticket #${ticket.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = statusColorsByKey[status] ?: MaterialTheme.colorScheme.primary
    val displayStatus = status.replace("_", " ").replace("-", " ")
        .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = displayStatus,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun PriorityBadge(priority: String) {
    val color = priorityColors[priority] ?: MaterialTheme.colorScheme.primary

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = priority.replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignSection(
    currentAssigneeId: Int?,
    employees: List<CompanyEmployee>,
    isUpdating: Boolean,
    onAssign: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentAssignee = employees.find { it.user_id == currentAssigneeId }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Assign Ticket",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!isUpdating) expanded = it }
            ) {
                OutlinedTextField(
                    value = currentAssignee?.displayName ?: "Unassigned",
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isUpdating,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null)
                    }
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Unassigned") },
                        onClick = {
                            onAssign(null)
                            expanded = false
                        }
                    )
                    employees.forEach { employee ->
                        DropdownMenuItem(
                            text = { Text(employee.displayName) },
                            onClick = {
                                onAssign(employee.user_id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTransitionSection(
    validTransitions: List<String>,
    isUpdating: Boolean,
    onStatusChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Move to",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                validTransitions.forEach { targetStatus ->
                    val color = statusColorsByKey[targetStatus] ?: MaterialTheme.colorScheme.primary
                    val displayStatus = targetStatus.replace("_", " ").replace("-", " ")
                        .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                    Button(
                        onClick = { onStatusChange(targetStatus) },
                        enabled = !isUpdating,
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(displayStatus, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun formatDate(dateString: String?): String {
    if (dateString == null) return "Unknown"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        date?.let { outputFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            dateString
        }
    }
}
