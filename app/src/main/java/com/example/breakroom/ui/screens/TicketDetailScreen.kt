package com.example.breakroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.breakroom.data.models.CompanyEmployee
import com.example.breakroom.data.models.Ticket
import java.text.SimpleDateFormat
import java.util.*

// Status colors matching web version
private val statusColors = mapOf(
    "backlog" to Color(0xFF6C757D),      // Gray
    "on-deck" to Color(0xFF17A2B8),      // Teal
    "in_progress" to Color(0xFFFFC107),  // Yellow
    "resolved" to Color(0xFF28A745),     // Green
    "closed" to Color(0xFF343A40)        // Dark Gray
)

// Priority colors matching web version
private val priorityColors = mapOf(
    "low" to Color(0xFF6C757D),      // Gray
    "medium" to Color(0xFF0D6EFD),   // Blue
    "high" to Color(0xFFFD7E14),     // Orange
    "urgent" to Color(0xFFDC3545)    // Red
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    viewModel: TicketDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val ticket = uiState.ticket

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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header with back button and edit button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (uiState.isEditing) {
                        viewModel.cancelEditing()
                    } else {
                        onNavigateBack()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = if (uiState.isEditing) "Cancel" else "Back"
                    )
                }
                Text(
                    text = if (uiState.isEditing) "Edit Ticket" else "Ticket Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (!uiState.isEditing && ticket != null) {
                    IconButton(onClick = { viewModel.startEditing() }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit ticket"
                        )
                    }
                }
            }

            if (ticket == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Ticket not found")
                }
            } else {
                // Loading overlay
                if (uiState.isUpdating) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (uiState.isEditing) {
                    // Edit mode
                    EditTicketForm(
                        title = uiState.editTitle,
                        description = uiState.editDescription,
                        priority = uiState.editPriority,
                        isUpdating = uiState.isUpdating,
                        onTitleChange = { viewModel.updateEditTitle(it) },
                        onDescriptionChange = { viewModel.updateEditDescription(it) },
                        onPriorityChange = { viewModel.updateEditPriority(it) },
                        onSave = { viewModel.saveTicket() },
                        onCancel = { viewModel.cancelEditing() }
                    )
                } else {
                    // Display mode
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                // Created by
                                InfoRow(label = "Created by", value = ticket.creatorName)

                                // Created date
                                InfoRow(
                                    label = "Created",
                                    value = formatDate(ticket.created_at)
                                )

                                // Resolved date (if applicable)
                                ticket.resolved_at?.let { resolvedAt ->
                                    InfoRow(
                                        label = "Resolved",
                                        value = formatDate(resolvedAt)
                                    )
                                }

                                // Assigned to
                                InfoRow(
                                    label = "Assigned to",
                                    value = ticket.assigneeName ?: "Unassigned"
                                )
                            }
                        }

                        // Assign section
                        AssignSection(
                            currentAssigneeId = ticket.assignee_id ?: ticket.assigned_to,
                            employees = uiState.employees,
                            isUpdating = uiState.isUpdating,
                            onAssign = { viewModel.assignTicket(it) }
                        )

                        // Description section
                        if (!ticket.description.isNullOrBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
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

                        // Status transition buttons
                        val validTransitions = StatusTransitions.getValidTransitions(ticket.status)
                        if (validTransitions.isNotEmpty()) {
                            StatusTransitionSection(
                                currentStatus = ticket.status,
                                validTransitions = validTransitions,
                                isUpdating = uiState.isUpdating,
                                onStatusChange = { viewModel.updateStatus(it) }
                            )
                        }

                        // Ticket ID footer
                        Text(
                            text = "Ticket #${ticket.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = statusColors[status] ?: MaterialTheme.colorScheme.primary
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

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null
                        )
                    }
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // Unassigned option
                    DropdownMenuItem(
                        text = { Text("Unassigned") },
                        onClick = {
                            onAssign(null)
                            expanded = false
                        }
                    )

                    // Employee options
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
    currentStatus: String,
    validTransitions: List<String>,
    isUpdating: Boolean,
    onStatusChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
                    val color = statusColors[targetStatus] ?: MaterialTheme.colorScheme.primary
                    val displayStatus = targetStatus.replace("_", " ").replace("-", " ")
                        .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                    Button(
                        onClick = { onStatusChange(targetStatus) },
                        enabled = !isUpdating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = color
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = displayStatus,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTicketForm(
    title: String,
    description: String,
    priority: String,
    isUpdating: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    var priorityExpanded by remember { mutableStateOf(false) }
    val priorities = listOf("low", "medium", "high", "urgent")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title field
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isUpdating,
            singleLine = true
        )

        // Description field
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            enabled = !isUpdating,
            minLines = 4,
            maxLines = 8
        )

        // Priority dropdown
        ExposedDropdownMenuBox(
            expanded = priorityExpanded,
            onExpandedChange = { if (!isUpdating) priorityExpanded = it }
        ) {
            OutlinedTextField(
                value = priority.replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                enabled = !isUpdating,
                label = { Text("Priority") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = priorityExpanded,
                onDismissRequest = { priorityExpanded = false }
            ) {
                priorities.forEach { p ->
                    val color = priorityColors[p] ?: MaterialTheme.colorScheme.primary
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = p.replaceFirstChar { it.uppercase() },
                                color = color
                            )
                        },
                        onClick = {
                            onPriorityChange(p)
                            priorityExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isUpdating,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                enabled = !isUpdating && title.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save")
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
        // Try without time
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
