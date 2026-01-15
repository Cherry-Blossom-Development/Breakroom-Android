package com.example.breakroom.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.breakroom.data.models.Ticket

// Priority colors
private val priorityColors = mapOf(
    "low" to Color(0xFF6C757D),
    "medium" to Color(0xFF0D6EFD),
    "high" to Color(0xFFFD7E14),
    "urgent" to Color(0xFFDC3545)
)

// Status colors
private val statusColors = mapOf(
    "open" to Color(0xFF28A745),
    "backlog" to Color(0xFF6F42C1),
    "in_progress" to Color(0xFFFFC107),
    "resolved" to Color(0xFF17A2B8),
    "closed" to Color(0xFF6C757D)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDeskScreen(
    viewModel: HelpDeskViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Help Desk",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (uiState.companyName.isNotBlank()) {
                    Text(
                        text = uiState.companyName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = { viewModel.showNewTicketDialog() }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Ticket")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                ErrorState(
                    error = uiState.error!!,
                    onRetry = viewModel::loadData,
                    onDismiss = viewModel::clearError
                )
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Open Tickets Section
                    item {
                        Text(
                            text = "Open Tickets (${uiState.openTickets.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Divider(
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            thickness = 2.dp
                        )
                    }

                    if (uiState.openTickets.isEmpty()) {
                        item {
                            EmptySection(text = "No open tickets")
                        }
                    } else {
                        items(uiState.openTickets) { ticket ->
                            TicketCard(
                                ticket = ticket,
                                onClick = { viewModel.selectTicket(ticket) }
                            )
                        }
                    }

                    // Closed Tickets Section
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Resolved/Closed (${uiState.closedTickets.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Divider(
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            thickness = 2.dp
                        )
                    }

                    if (uiState.closedTickets.isEmpty()) {
                        item {
                            EmptySection(text = "No resolved tickets")
                        }
                    } else {
                        items(uiState.closedTickets) { ticket ->
                            TicketCard(
                                ticket = ticket,
                                onClick = { viewModel.selectTicket(ticket) },
                                isClosed = true
                            )
                        }
                    }
                }
            }
        }
    }

    // New Ticket Dialog
    if (uiState.showNewTicketDialog) {
        NewTicketDialog(
            isSubmitting = uiState.isSubmitting,
            onDismiss = { viewModel.hideNewTicketDialog() },
            onSubmit = { title, description, priority ->
                viewModel.createTicket(title, description, priority)
            }
        )
    }

    // Ticket Detail Dialog
    uiState.selectedTicket?.let { ticket ->
        TicketDetailDialog(
            ticket = ticket,
            onDismiss = { viewModel.selectTicket(null) },
            onUpdateStatus = { newStatus ->
                viewModel.updateTicketStatus(ticket.id, newStatus)
            }
        )
    }

    // Success snackbar
    uiState.successMessage?.let { message ->
        LaunchedEffect(message) {
            viewModel.clearSuccessMessage()
        }
    }
}

@Composable
private fun TicketCard(
    ticket: Ticket,
    onClick: () -> Unit,
    isClosed: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isClosed)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: ID and Priority
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${ticket.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PriorityBadge(priority = ticket.priority)
            }

            // Title
            Text(
                text = ticket.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Status and Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = ticket.status)
                Text(
                    text = HelpDeskViewModel.formatDateTime(ticket.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Creator
            Text(
                text = "by ${ticket.creatorName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = statusColors[status] ?: Color.Gray
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status.replace("_", " ").replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = if (status == "in_progress") Color.Black else Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PriorityBadge(priority: String) {
    val color = priorityColors[priority] ?: Color.Gray
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = priority.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTicketDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, description: String, priority: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
    var priorityExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Create New Ticket",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Brief description of the issue") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Provide details about the issue...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Priority dropdown
                ExposedDropdownMenuBox(
                    expanded = priorityExpanded,
                    onExpandedChange = { priorityExpanded = it }
                ) {
                    OutlinedTextField(
                        value = priority.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = priorityExpanded,
                        onDismissRequest = { priorityExpanded = false }
                    ) {
                        listOf("low", "medium", "high", "urgent").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    priority = p
                                    priorityExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(title, description, priority) },
                        enabled = title.isNotBlank() && !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isSubmitting) "Creating..." else "Create Ticket")
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketDetailDialog(
    ticket: Ticket,
    onDismiss: () -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Text(
                    text = ticket.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status and Priority badges
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(status = ticket.status)
                    PriorityBadge(priority = ticket.priority)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Info card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            DetailRow("Created by", ticket.creatorName)
                            DetailRow("Created", HelpDeskViewModel.formatDateTime(ticket.created_at))
                            if (ticket.resolved_at != null) {
                                DetailRow("Resolved", HelpDeskViewModel.formatDateTime(ticket.resolved_at))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = ticket.description ?: "No description provided.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // Status update actions
                    if (ticket.status != "closed") {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Update Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (ticket.status in listOf("open", "backlog")) {
                                Button(
                                    onClick = { onUpdateStatus("in_progress") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = statusColors["in_progress"] ?: Color.Gray
                                    )
                                ) {
                                    Text("In Progress", color = Color.Black)
                                }
                            }
                            if (ticket.status in listOf("open", "backlog", "in_progress")) {
                                Button(
                                    onClick = { onUpdateStatus("resolved") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = statusColors["resolved"] ?: Color.Gray
                                    )
                                ) {
                                    Text("Resolved")
                                }
                            }
                            if (ticket.status == "resolved") {
                                Button(
                                    onClick = { onUpdateStatus("closed") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = statusColors["closed"] ?: Color.Gray
                                    )
                                ) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }

                // Footer
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptySection(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.HelpOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss) {
                Text("Dismiss")
            }
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
