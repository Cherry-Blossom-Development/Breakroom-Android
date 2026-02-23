package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cherryblossomdev.breakroom.data.models.Ticket

// ==================== Status and priority definitions ====================

private data class BoardColumnDef(val value: String, val label: String, val color: Color)

private val boardColumns = listOf(
    BoardColumnDef("backlog",     "Backlog",     Color(0xFFFF9800)),
    BoardColumnDef("on-deck",     "On Deck",     Color(0xFF9C27B0)),
    BoardColumnDef("in_progress", "In Progress", Color(0xFF2196F3)),
    BoardColumnDef("resolved",    "Resolved",    Color(0xFF4CAF50)),
    BoardColumnDef("closed",      "Closed",      Color(0xFF9E9E9E))
)

private val kanbanPriorityColors = mapOf(
    "low"    to Color(0xFF9E9E9E),
    "medium" to Color(0xFF2196F3),
    "high"   to Color(0xFFFF9800),
    "urgent" to Color(0xFFF44336)
)

private fun allowedTransitions(status: String): List<BoardColumnDef> {
    val targets = when (status) {
        "backlog"     -> listOf("on-deck", "in_progress")
        "on-deck"     -> listOf("backlog", "in_progress")
        "in_progress" -> listOf("on-deck", "resolved")
        "resolved"    -> listOf("in_progress", "closed")
        "closed"      -> listOf("resolved")
        else          -> emptyList()
    }
    return targets.mapNotNull { t -> boardColumns.find { it.value == t } }
}

// ==================== Redirect Screen ====================

@Composable
fun KanbanRedirectScreen(
    viewModel: KanbanRedirectViewModel,
    onNavigateToBoard: (projectId: Int, projectTitle: String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is KanbanRedirectState.Ready) {
            val ready = state as KanbanRedirectState.Ready
            onNavigateToBoard(ready.projectId, ready.projectTitle)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            is KanbanRedirectState.Loading, is KanbanRedirectState.Ready -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        "Finding your projects...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is KanbanRedirectState.NoActiveProjects -> {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "No Active Projects",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your company doesn't have any active projects. Create a project from the Company Portal to get started with Kanban.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is KanbanRedirectState.Error -> {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Something went wrong",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.retry() }) { Text("Try Again") }
                }
            }
        }
    }
}

// ==================== Board Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanBoardScreen(
    viewModel: KanbanBoardViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveError) {
        uiState.saveError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { scaffoldPadding ->
        Column(modifier = modifier.fillMaxSize().padding(scaffoldPadding)) {

            // Header
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = uiState.projectTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { viewModel.showAddTicket() }) {
                        Icon(Icons.Default.Add, contentDescription = "New Ticket", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(uiState.error ?: "Error", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { viewModel.loadBoard() }) { Text("Retry") }
                        }
                    }
                }
                else -> {
                    // Horizontal-scrolling kanban board
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxHeight()
                        ) {
                            boardColumns.forEach { statusDef ->
                                KanbanColumn(
                                    statusDef = statusDef,
                                    tickets = uiState.tickets.filter { it.status == statusDef.value },
                                    onTicketClick = { viewModel.setEditingTicket(it) },
                                    onMoveTicket = { ticket, target -> viewModel.moveTicket(ticket.id, target) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add ticket dialog
    if (uiState.showAddTicket) {
        AddTicketDialog(
            title = uiState.newTicketTitle,
            description = uiState.newTicketDescription,
            priority = uiState.newTicketPriority,
            isSaving = uiState.isSaving,
            onTitleChange = viewModel::setNewTicketTitle,
            onDescriptionChange = viewModel::setNewTicketDescription,
            onPriorityChange = viewModel::setNewTicketPriority,
            onSave = { viewModel.createTicket() },
            onDismiss = { viewModel.hideAddTicket() }
        )
    }

    // Edit ticket dialog
    uiState.editingTicket?.let { ticket ->
        EditTicketDialog(
            ticket = ticket,
            isSaving = uiState.isSaving,
            onSave = { title, description, priority, status ->
                viewModel.updateTicket(ticket.id, title, description, priority, status)
            },
            onDismiss = { viewModel.setEditingTicket(null) }
        )
    }
}

// ==================== Kanban Column ====================

@Composable
private fun KanbanColumn(
    statusDef: BoardColumnDef,
    tickets: List<Ticket>,
    onTicketClick: (Ticket) -> Unit,
    onMoveTicket: (Ticket, String) -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
    ) {
        // Column header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(modifier = Modifier.size(10.dp).background(statusDef.color, CircleShape))
            Text(
                text = statusDef.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = "${tickets.size}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tickets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No tickets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                tickets.forEach { ticket ->
                    TicketCard(
                        ticket = ticket,
                        statusDef = statusDef,
                        onClick = { onTicketClick(ticket) },
                        onMove = { target -> onMoveTicket(ticket, target) }
                    )
                }
            }
        }
    }
}

// ==================== Ticket Card ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketCard(
    ticket: Ticket,
    statusDef: BoardColumnDef,
    onClick: () -> Unit,
    onMove: (String) -> Unit
) {
    val transitions = allowedTransitions(statusDef.value)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = ticket.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                KanbanPriorityIndicator(priority = ticket.priority)
                ticket.assigneeName?.let { assignee ->
                    Text(
                        text = assignee,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp)
                    )
                }
            }

            if (transitions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    transitions.forEach { target ->
                        OutlinedButton(
                            onClick = { onMove(target.value) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = target.color),
                            border = androidx.compose.foundation.BorderStroke(1.dp, target.color.copy(alpha = 0.5f))
                        ) {
                            Text(target.label, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanPriorityIndicator(priority: String) {
    val color = kanbanPriorityColors[priority] ?: Color.Gray
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Text(
            text = priority.replaceFirstChar { it.uppercase() },
            fontSize = 10.sp,
            color = color
        )
    }
}

// ==================== Add Ticket Dialog ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTicketDialog(
    title: String,
    description: String,
    priority: String,
    isSaving: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("New Ticket", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("low", "medium", "high", "urgent").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { onPriorityChange(p) },
                            label = { Text(p.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = (kanbanPriorityColors[p] ?: Color.Gray).copy(alpha = 0.2f),
                                selectedLabelColor = kanbanPriorityColors[p] ?: Color.Gray
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSave, enabled = title.isNotBlank() && !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }
}

// ==================== Edit Ticket Dialog ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTicketDialog(
    ticket: Ticket,
    isSaving: Boolean,
    onSave: (title: String, description: String?, priority: String, status: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(ticket.id) { mutableStateOf(ticket.title) }
    var description by remember(ticket.id) { mutableStateOf(ticket.description ?: "") }
    var priority by remember(ticket.id) { mutableStateOf(ticket.priority) }
    var status by remember(ticket.id) { mutableStateOf(ticket.status) }
    var statusExpanded by remember { mutableStateOf(false) }

    val currentStatusLabel = boardColumns.find { it.value == status }?.label
        ?: status.replace("_", " ").replace("-", " ")
            .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Edit Ticket", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                if (ticket.creatorName.isNotBlank()) {
                    Text(
                        "Created by ${ticket.creatorName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it }
                ) {
                    OutlinedTextField(
                        value = currentStatusLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        boardColumns.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = { status = s.value; statusExpanded = false }
                            )
                        }
                    }
                }

                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("low", "medium", "high", "urgent").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = (kanbanPriorityColors[p] ?: Color.Gray).copy(alpha = 0.2f),
                                selectedLabelColor = kanbanPriorityColors[p] ?: Color.Gray
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(title.trim(), description.trim().ifBlank { null }, priority, status) },
                        enabled = title.isNotBlank() && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
