package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cherryblossomdev.breakroom.data.models.Ticket
import com.cherryblossomdev.breakroom.data.models.TicketComment

private fun String.stripHtml(): String =
    this
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</(p|div|li|h[1-6])>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

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
    "on-deck" to Color(0xFF0DCAF0),
    "in_progress" to Color(0xFFFFC107),
    "resolved" to Color(0xFF17A2B8),
    "closed" to Color(0xFF6C757D)
)

private fun formatStatusLabel(status: String): String =
    status.replace("_", " ").replace("-", " ")
        .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

private fun validNextStatuses(current: String): List<String> = when (current) {
    "open"        -> listOf("backlog", "on-deck", "in_progress", "resolved")
    "backlog"     -> listOf("on-deck", "in_progress", "resolved")
    "on-deck"     -> listOf("in_progress", "resolved")
    "in_progress" -> listOf("resolved")
    "resolved"    -> listOf("closed", "open")
    else          -> emptyList()
}

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
            Button(onClick = { viewModel.showNewTicketDialog() }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Ticket")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        item { EmptySection(text = "No open tickets") }
                    } else {
                        items(uiState.openTickets) { ticket ->
                            TicketCard(ticket = ticket, onClick = { viewModel.selectTicket(ticket) })
                        }
                    }

                    // Resolved/Closed Section
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
                        item { EmptySection(text = "No resolved tickets") }
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
            isSubmitting = uiState.isSubmitting,
            comments = uiState.ticketComments,
            commentText = uiState.commentText,
            isPostingComment = uiState.isPostingComment,
            editingCommentId = uiState.editingCommentId,
            editCommentText = uiState.editCommentText,
            currentUsername = uiState.currentUsername,
            onDismiss = { viewModel.selectTicket(null) },
            onUpdate = { title, description, priority, status ->
                viewModel.updateTicket(ticket.id, title, description, priority, status)
            },
            onCommentTextChange = { viewModel.updateCommentText(it) },
            onAddComment = { viewModel.addComment() },
            onStartEditComment = { id, content -> viewModel.startEditComment(id, content) },
            onEditCommentTextChange = { viewModel.updateEditCommentText(it) },
            onSaveEditComment = { viewModel.saveEditComment() },
            onCancelEditComment = { viewModel.cancelEditComment() },
            onDeleteComment = { viewModel.deleteComment(it) }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isClosed)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: ID and Priority
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

            // Creator / Assignee
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "by ${ticket.creatorName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                ticket.assigneeName?.let { name ->
                    Text(
                        text = "→ $name",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = statusColors[status] ?: Color.Gray
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            text = formatStatusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = if (status == "in_progress" || status == "on-deck") Color.Black else Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PriorityBadge(priority: String) {
    val color = priorityColors[priority] ?: Color.Gray
    Surface(color = color, shape = MaterialTheme.shapes.small) {
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
private fun TicketDetailDialog(
    ticket: Ticket,
    isSubmitting: Boolean,
    comments: List<TicketComment>,
    commentText: String,
    isPostingComment: Boolean,
    editingCommentId: Int?,
    editCommentText: String,
    currentUsername: String,
    onDismiss: () -> Unit,
    onUpdate: (title: String, description: String?, priority: String, status: String) -> Unit,
    onCommentTextChange: (String) -> Unit,
    onAddComment: () -> Unit,
    onStartEditComment: (Int, String) -> Unit,
    onEditCommentTextChange: (String) -> Unit,
    onSaveEditComment: () -> Unit,
    onCancelEditComment: () -> Unit,
    onDeleteComment: (Int) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var editTitle by remember(ticket.id) { mutableStateOf(ticket.title) }
    var editDescription by remember(ticket.id) { mutableStateOf(ticket.description ?: "") }
    var editPriority by remember(ticket.id) { mutableStateOf(ticket.priority) }
    var editStatus by remember(ticket.id) { mutableStateOf(ticket.status) }
    var priorityExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    if (isEditMode) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Title") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    } else {
                        Text(
                            text = ticket.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { isEditMode = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ticket")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isEditMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(status = ticket.status)
                        PriorityBadge(priority = ticket.priority)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Scrollable content
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                ) {
                    if (isEditMode) {
                        // Status dropdown
                        ExposedDropdownMenuBox(
                            expanded = statusExpanded,
                            onExpandedChange = { statusExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = formatStatusLabel(editStatus),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Status") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false }
                            ) {
                                listOf("open", "backlog", "on-deck", "in_progress", "resolved", "closed").forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(formatStatusLabel(s)) },
                                        onClick = { editStatus = s; statusExpanded = false }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Priority dropdown
                        ExposedDropdownMenuBox(
                            expanded = priorityExpanded,
                            onExpandedChange = { priorityExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = editPriority.replaceFirstChar { it.uppercase() },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Priority") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = priorityExpanded,
                                onDismissRequest = { priorityExpanded = false }
                            ) {
                                listOf("low", "medium", "high", "urgent").forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.replaceFirstChar { it.uppercase() }) },
                                        onClick = { editPriority = p; priorityExpanded = false }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = editDescription,
                            onValueChange = { editDescription = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            maxLines = 5
                        )
                    } else {
                        // Info card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                DetailRow("Created by", ticket.creatorName)
                                DetailRow("Created", HelpDeskViewModel.formatDateTime(ticket.created_at))
                                ticket.assigneeName?.let { name ->
                                    DetailRow("Assigned to", name)
                                }
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
                            text = ticket.description?.stripHtml() ?: "No description provided.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        // Status transitions
                        val nextStatuses = validNextStatuses(ticket.status)
                        if (nextStatuses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Move To",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            nextStatuses.chunked(2).forEach { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    rowItems.forEach { nextStatus ->
                                        val color = statusColors[nextStatus] ?: Color.Gray
                                        OutlinedButton(
                                            onClick = {
                                                onUpdate(ticket.title, ticket.description, ticket.priority, nextStatus)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = formatStatusLabel(nextStatus),
                                                color = color,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                    // Fill empty slot in last row if odd count
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Comments section
                        Spacer(modifier = Modifier.height(20.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Comments (${comments.count { !it.isDeleted }})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (comments.isEmpty()) {
                            Text(
                                text = "No comments yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            comments.forEach { comment ->
                                CommentItem(
                                    comment = comment,
                                    currentUsername = currentUsername,
                                    isEditing = editingCommentId == comment.id,
                                    editText = editCommentText,
                                    onStartEdit = { onStartEditComment(comment.id, comment.content) },
                                    onEditTextChange = onEditCommentTextChange,
                                    onSaveEdit = onSaveEditComment,
                                    onCancelEdit = onCancelEditComment,
                                    onDelete = { onDeleteComment(comment.id) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = onCommentTextChange,
                            label = { Text("Add a comment") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onAddComment,
                            enabled = commentText.isNotBlank() && !isPostingComment,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isPostingComment) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isPostingComment) "Posting..." else "Add Comment")
                        }
                    }
                }

                // Footer
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditMode) {
                        TextButton(onClick = {
                            editTitle = ticket.title
                            editDescription = ticket.description ?: ""
                            editPriority = ticket.priority
                            editStatus = ticket.status
                            isEditMode = false
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onUpdate(editTitle, editDescription.ifBlank { null }, editPriority, editStatus)
                                isEditMode = false
                            },
                            enabled = editTitle.isNotBlank() && !isSubmitting
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isSubmitting) "Saving..." else "Save")
                        }
                    } else {
                        Button(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: TicketComment,
    currentUsername: String,
    isEditing: Boolean,
    editText: String,
    onStartEdit: () -> Unit,
    onEditTextChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (comment.isDeleted) {
                Text(
                    text = "Comment deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            } else if (isEditing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = onEditTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCancelEdit) { Text("Cancel") }
                    Button(onClick = onSaveEdit, enabled = editText.isNotBlank()) { Text("Save") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = comment.handle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = HelpDeskViewModel.formatDateTime(comment.created_at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodySmall
                )
                if (comment.handle == currentUsername) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onStartEdit) { Text("Edit", style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = onDelete) {
                            Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
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
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Create New Ticket",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Brief description of the issue") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Provide details about the issue...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = priorityExpanded,
                        onDismissRequest = { priorityExpanded = false }
                    ) {
                        listOf("low", "medium", "high", "urgent").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.replaceFirstChar { it.uppercase() }) },
                                onClick = { priority = p; priorityExpanded = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(title, description, priority) },
                        enabled = title.isNotBlank() && !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
            modifier = Modifier.fillMaxWidth().padding(24.dp),
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
            OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
