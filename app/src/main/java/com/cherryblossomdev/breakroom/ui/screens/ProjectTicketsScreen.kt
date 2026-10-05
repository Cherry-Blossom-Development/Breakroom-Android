package com.cherryblossomdev.breakroom.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherryblossomdev.breakroom.data.models.EstimateUnits
import com.cherryblossomdev.breakroom.data.models.ProjectAssignee
import com.cherryblossomdev.breakroom.data.models.Ticket
import com.cherryblossomdev.breakroom.data.models.TicketComment
import com.cherryblossomdev.breakroom.ui.components.AccessibilityAnnouncer
import com.cherryblossomdev.breakroom.ui.components.PendingFile
import com.cherryblossomdev.breakroom.ui.components.TicketAttachmentsSection
import com.cherryblossomdev.breakroom.ui.components.attachmentLimitError
import com.cherryblossomdev.breakroom.ui.components.formatFileSize
import com.cherryblossomdev.breakroom.ui.components.openDownloadedFile
import com.cherryblossomdev.breakroom.ui.components.rememberAttachmentPicker
import androidx.compose.ui.platform.LocalContext
import com.cherryblossomdev.breakroom.ui.theme.isHighContrastEnabled
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private fun String.stripHtml(): String =
    this
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</(p|div|li|h[1-6])>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

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
// The project board, shown in the project workspace's Kanban section (the
// workspace supplies the title bar and handles a failed project load).
fun ProjectTicketsScreen(
    viewModel: ProjectTicketsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // System Back steps out of the ticket detail / closed list before it
    // leaves the workspace
    BackHandler(enabled = uiState.selectedTicket != null) {
        if (uiState.isEditing) viewModel.backToTicket() else viewModel.requestCloseTicket()
    }
    BackHandler(enabled = uiState.selectedTicket == null && uiState.showingClosed) {
        viewModel.hideClosedTickets()
    }
    BackHandler(enabled = uiState.selectedTicket == null && uiState.showingBacklog) {
        viewModel.hideBacklog()
    }
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

    AccessibilityAnnouncer(uiState.announcement)

    val context = LocalContext.current
    LaunchedEffect(uiState.openedFile) {
        uiState.openedFile?.let { (file, mimeType) ->
            viewModel.onAttachmentOpened(openDownloadedFile(context, file, mimeType))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            // Only show FAB on the board itself
            if (uiState.selectedTicket == null && !uiState.showingClosed && uiState.canCreateTickets) {
                FloatingActionButton(
                    onClick = { viewModel.showCreateDialog() }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create Ticket")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main Kanban view
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading && uiState.project == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.showingClosed) {
                    ClosedTicketsList(
                        tickets = uiState.closedTickets,
                        onBackToBoard = { viewModel.hideClosedTickets() },
                        onTicketClick = { viewModel.selectTicket(it) }
                    )
                } else if (uiState.showingBacklog) {
                    BacklogList(
                        state = uiState,
                        onSearchChange = { viewModel.updateBacklogSearch(it) },
                        onBackToBoard = { viewModel.hideBacklog() },
                        onTicketClick = { viewModel.selectTicket(it) }
                    )
                } else {
                    // Backlog and Closed tickets have no lane; they're
                    // counted here instead, opening their lists
                    val backlogCount = uiState.backlogTickets.size
                    val closedCount = uiState.closedTickets.size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { viewModel.showBacklog() },
                            modifier = Modifier.testTag("backlog-link")
                        ) {
                            Text("$backlogCount Backlog ticket${if (backlogCount == 1) "" else "s"}")
                        }
                        TextButton(onClick = { viewModel.showClosedTickets() }) {
                            Text("$closedCount Closed ticket${if (closedCount == 1) "" else "s"}")
                        }
                    }

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
                            openBlockers = uiState.openBlockersByTicket,
                            parentOf = { uiState.parentOf(it) },
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
                        state = uiState,
                        viewModel = viewModel
                    )
                }
            }

            if (uiState.leavePrompt != null) {
                LeavePromptDialog(onChoice = { viewModel.resolveLeavePrompt(it) })
            }

            val splitTarget = uiState.selectedTicket
            if (uiState.showSplitDialog && splitTarget != null) {
                SplitTicketDialog(
                    ticket = splitTarget,
                    isSubmitting = uiState.isSplitting,
                    error = uiState.splitError,
                    onDismiss = { viewModel.hideSplitDialog() },
                    onSplit = { mode, subtasks -> viewModel.splitTicket(mode, subtasks) }
                )
            }

            // Create Ticket Dialog
            if (uiState.showCreateDialog) {
                CreateTicketDialog(
                    isCreating = uiState.isCreatingTicket,
                    canEstimate = uiState.canWork,
                    onDismiss = { viewModel.hideCreateDialog() },
                    onCreate = { title, description, priority, estimateAmount, estimateUnit, files ->
                        viewModel.createTicket(title, description, priority, estimateAmount, estimateUnit, files)
                    }
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
            IconButton(onClick = onGoLeft, enabled = canGoLeft) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous status",
                    tint = if (canGoLeft) statusColor else MaterialTheme.colorScheme.outlineVariant
                )
            }

            val countText = if (ticketCount == 1) "1 ticket" else "$ticketCount tickets"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "${currentStatus.displayName}, $countText"
                }
            ) {
                Text(
                    text = currentStatus.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = countText,
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

// Closed tickets, most recently closed first. Unlike web, a row opens the
// ticket, so it can be reopened (closed -> resolved) from here.
@Composable
private fun ClosedTicketsList(
    tickets: List<Ticket>,
    onBackToBoard: () -> Unit,
    onTicketClick: (Ticket) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(
            onClick = onBackToBoard,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back to Kanban Board")
        }
        Text(
            "Closed Tickets (${tickets.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (tickets.isEmpty()) {
            Text(
                "No closed tickets yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tickets, key = { it.id }) { ticket ->
                    ClosedTicketRow(ticket = ticket, onClick = { onTicketClick(ticket) })
                }
            }
        }
    }
}

@Composable
private fun ClosedTicketRow(ticket: Ticket, onClick: () -> Unit) {
    val priorityColor = priorityColors[ticket.priority] ?: MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${ticket.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    ticket.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    ticket.formattedPriority,
                    style = MaterialTheme.typography.labelSmall,
                    color = priorityColor,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            val closedOn = formatShortDate(ticket.resolved_at ?: ticket.updated_at)
            val details = listOfNotNull(
                closedOn?.let { "Closed $it" },
                "Opened by ${ticket.creatorName}",
                ticket.assigneeName?.let { "Assigned to $it" }
            )
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// The backlog as a searchable list (web 1f2f954). Rows open the same ticket
// panel as the board.
@Composable
private fun BacklogList(
    state: ProjectTicketsUiState,
    onSearchChange: (String) -> Unit,
    onBackToBoard: () -> Unit,
    onTicketClick: (Ticket) -> Unit
) {
    val all = state.backlogTickets
    val shown = state.filteredBacklog
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(
            onClick = onBackToBoard,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back to Kanban Board")
        }
        Text(
            "Backlog (${all.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (all.isNotEmpty()) {
            OutlinedTextField(
                value = state.backlogSearch,
                onValueChange = onSearchChange,
                placeholder = { Text("Search #id or title") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics { contentDescription = "Search the backlog" }
                    .testTag("backlog-search")
            )
        }
        when {
            all.isEmpty() -> Text(
                "The backlog is empty.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            shown.isEmpty() -> Text(
                "No backlog tickets match \"${state.backlogSearch.trim()}\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.id }) { ticket ->
                    BacklogTicketRow(
                        ticket = ticket,
                        parent = state.parentOf(ticket),
                        blockedBy = state.openBlockersByTicket[ticket.id].orEmpty(),
                        onClick = { onTicketClick(ticket) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BacklogTicketRow(
    ticket: Ticket,
    parent: Ticket?,
    blockedBy: List<Int>,
    onClick: () -> Unit
) {
    val priorityColor = priorityColors[ticket.priority] ?: MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("backlog-ticket-${ticket.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${ticket.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    ticket.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (ticket.hasEstimate) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clearAndSetSemantics { contentDescription = "Estimate ${ticket.formattedEstimate}" }
                    ) {
                        Text(
                            ticket.shortEstimate,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    ticket.formattedPriority,
                    style = MaterialTheme.typography.labelSmall,
                    color = priorityColor,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (parent != null) {
                Text(
                    "\u21B3 #${parent.id} ${parent.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (blockedBy.isNotEmpty()) {
                Text(
                    "Blocked by ${blockedBy.joinToString(", ") { "#$it" }}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            val details = listOfNotNull(
                ticket.assigneeName,
                formatShortDate(ticket.created_at)?.let { "Opened $it" }
            )
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// "Sep 29, 2026" from an ISO timestamp; null if missing/unparseable
private fun formatShortDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(iso.take(19)) ?: return null
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun KanbanLane(
    status: KanbanStatus,
    tickets: List<Ticket>,
    openBlockers: Map<Int, List<Int>>,
    parentOf: (Ticket) -> Ticket?,
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
                    tint = if (isHighContrastEnabled()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
                    parent = parentOf(ticket),
                    blockedBy = openBlockers[ticket.id].orEmpty(),
                    onClick = { onTicketClick(ticket) }
                )
            }
        }
    }
}

@Composable
private fun TicketCard(
    ticket: Ticket,
    parent: Ticket?,
    blockedBy: List<Int>,
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

            // Subtask marker (migration 086)
            if (parent != null) {
                Text(
                    text = "\u21B3 #${parent.id} ${parent.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .semantics { contentDescription = "Subtask of #${parent.id} ${parent.title}" }
                )
            }

            // Unfinished dependencies (web's "Blocked by" chip)
            if (blockedBy.isNotEmpty()) {
                val blockers = blockedBy.joinToString(", ") { "#$it" }
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Blocked by $blockers",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (!ticket.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ticket.description.stripHtml(),
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${ticket.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                if (ticket.hasEstimate) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = "Estimate ${ticket.formattedEstimate}"
                        }
                    ) {
                        Text(
                            text = ticket.shortEstimate,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============ TICKET DETAIL CONTENT ============

// The ticket panel. Every change (status, assignee, the title/description/
// priority form, unposted comments) is staged in the draft; the save bar
// appears once anything is changed and nothing is saved until it's used.
@Composable
private fun TicketDetailContent(
    ticket: Ticket,
    state: ProjectTicketsUiState,
    viewModel: ProjectTicketsViewModel
) {
    val draft = state.draft ?: return
    val busy = state.isSavingChanges

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
                IconButton(onClick = {
                    if (state.isEditing) viewModel.backToTicket() else viewModel.requestCloseTicket()
                }) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = if (state.isEditing) "Back to ticket" else "Close ticket"
                    )
                }
                Text(
                    text = if (state.isEditing) "Edit Ticket" else "Ticket Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (!state.isEditing && state.canSplit) {
                    TextButton(
                        onClick = { viewModel.openSplitDialog() },
                        enabled = !busy,
                        modifier = Modifier.testTag("split-ticket")
                    ) {
                        Text(if (ticket.isSplit) "Add subtasks" else "Split")
                    }
                }
                if (!state.isEditing && state.isCreator) {
                    IconButton(onClick = { viewModel.startEditing() }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ticket")
                    }
                }
            }

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.isEditing) {
                    EditTicketForm(
                        title = draft.title,
                        description = draft.description,
                        priority = draft.priority,
                        enabled = !busy,
                        onTitleChange = { viewModel.updateEditTitle(it) },
                        onDescriptionChange = { viewModel.updateEditDescription(it) },
                        onPriorityChange = { viewModel.updateEditPriority(it) },
                        onBackToTicket = { viewModel.backToTicket() }
                    )
                } else {
                    TicketDetailBody(ticket = ticket, draft = draft, state = state, viewModel = viewModel)
                }
            }

            if (state.isDirty || state.saveError != null) {
                SaveBar(
                    isDirty = state.isDirty,
                    isSaving = busy,
                    error = state.saveError,
                    onDiscard = { viewModel.discardChanges() },
                    onSave = { viewModel.saveChanges() }
                )
            }
        }
    }
}

@Composable
private fun TicketDetailBody(
    ticket: Ticket,
    draft: TicketDraft,
    state: ProjectTicketsUiState,
    viewModel: ProjectTicketsViewModel
) {
    val busy = state.isSavingChanges
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = draft.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(status = draft.status)
            PriorityBadge(priority = draft.priority)
        }

        state.parentOf(ticket)?.let { parent ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Subtask of ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "#${parent.id} ${parent.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable(onClickLabel = "Open parent ticket") { viewModel.openLinkedTicket(parent.id) }
                        .testTag("subtask-of-link")
                )
            }
        }

        if (ticket.isSplit) {
            SplitSummary(
                ticket = ticket,
                subtasks = state.subtasksOf(ticket.id),
                liveStatus = { state.liveStatus(it.id, it.status) },
                onOpen = { viewModel.openLinkedTicket(it) }
            )
        }

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
                if (!state.canWork) {
                    InfoRow(label = "Estimate", value = ticket.formattedEstimate.ifEmpty { "Not estimated" })
                }
            }
        }

        if (state.canWork) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Estimate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    EstimateInput(
                        amount = draft.estimateAmount,
                        unit = draft.estimateUnit,
                        enabled = !busy,
                        placeholder = "Not estimated",
                        onAmountChange = { viewModel.updateEstimateAmount(it) },
                        onUnitChange = { viewModel.updateEstimateUnit(it) }
                    )
                }
            }
            AssignSection(
                assigneeId = draft.assignedTo,
                assignees = state.assignees,
                enabled = !busy,
                onAssign = { viewModel.chooseAssignee(it) }
            )
        }

        if (draft.description.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = draft.description.stripHtml(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.allowedTransitions.isNotEmpty()) {
            StatusTransitionSection(
                transitions = state.allowedTransitions,
                chosenStatus = draft.status,
                enabled = !busy,
                onChoose = { viewModel.chooseStatus(it) }
            )
        }

        TicketAttachmentsSection(
            attachments = state.ticketAttachments,
            pendingFiles = draft.addFiles,
            pendingRemovals = draft.removeAttachments,
            canAttach = state.canAttach,
            canRemove = { state.canRemoveAttachment(it) },
            busy = busy || state.isOpeningAttachment,
            error = state.attachmentError,
            authHeader = remember { viewModel.attachmentAuthHeader() },
            onAdd = { viewModel.addFiles(it) },
            onRemove = { viewModel.markAttachmentForRemoval(it) },
            onUndoRemove = { viewModel.undoAttachmentRemoval(it) },
            onDropPending = { viewModel.dropPendingFile(it) },
            onOpen = { viewModel.openAttachment(it) }
        )

        DependenciesSection(
            dependsOn = state.selectedDependsOn,
            blocking = state.selectedBlocking,
            candidates = state.dependencyCandidates,
            onBoard = state.openableById.keys,
            canEdit = state.canWork && !busy,
            onAdd = { viewModel.addDependency(it) },
            onToggle = { viewModel.toggleDependency(it) },
            onOpen = { viewModel.openLinkedTicket(it) }
        )

        // Comments
        HorizontalDivider()
        Text(
            text = "Comments (${state.ticketComments.count { !it.isDeleted }})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (state.ticketComments.isEmpty()) {
            Text(
                text = "No comments yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.ticketComments.forEach { comment ->
                CommentItemProject(
                    comment = comment,
                    currentUsername = state.currentUsername,
                    isEditing = state.editingCommentId == comment.id,
                    editText = state.editCommentText,
                    onStartEdit = { viewModel.startEditComment(comment.id, comment.content) },
                    onEditTextChange = { viewModel.updateEditCommentText(it) },
                    onSaveEdit = { viewModel.saveEditComment() },
                    onCancelEdit = { viewModel.cancelEditComment() },
                    onDelete = { viewModel.deleteComment(comment.id) }
                )
            }
        }
        OutlinedTextField(
            value = state.commentText,
            onValueChange = { viewModel.updateCommentText(it) },
            label = { Text("Add a comment") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        Button(
            onClick = { viewModel.addComment() },
            enabled = state.commentText.isNotBlank() && !state.isPostingComment && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isPostingComment) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (state.isPostingComment) "Posting..." else "Add Comment")
        }

        Text(
            text = "Ticket #${ticket.id}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

// Finish-to-start links (migration 079): what this ticket depends on, with
// staged adds/removals, and what it's blocking. Tickets in another project
// are listed but can't be opened from here.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DependenciesSection(
    dependsOn: List<DependencyRow>,
    blocking: List<DependencyRow>,
    candidates: List<Ticket>,
    onBoard: Set<Int>,
    canEdit: Boolean,
    onAdd: (Int) -> Unit,
    onToggle: (DependencyRow) -> Unit,
    onOpen: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Depends on", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (dependsOn.isEmpty()) {
                Text(
                    "No dependencies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            dependsOn.forEach { row ->
                DependencyRowItem(
                    row = row,
                    canOpen = row.id in onBoard,
                    onOpen = { onOpen(row.id) },
                    trailing = if (canEdit) {
                        {
                            if (row.pending == "remove") {
                                TextButton(
                                    onClick = { onToggle(row) },
                                    modifier = Modifier.semantics { contentDescription = "Keep dependency on #${row.id}" }
                                ) { Text("Undo") }
                            } else {
                                IconButton(onClick = { onToggle(row) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove dependency on #${row.id}")
                                }
                            }
                        }
                    } else null
                )
            }

            if (canEdit) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (candidates.isNotEmpty()) expanded = it }
                ) {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = candidates.isNotEmpty(),
                        placeholder = {
                            Text(if (candidates.isEmpty()) "No other tickets to depend on" else "Add a ticket this depends on...")
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("add-dependency")
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        candidates.forEach { ticket ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "#${ticket.id} ${ticket.title} (${ticket.formattedStatus})",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                // Picking stages it right away (web c5a1c53)
                                onClick = {
                                    onAdd(ticket.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (blocking.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Blocking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                blocking.forEach { row ->
                    DependencyRowItem(row = row, canOpen = row.id in onBoard, onOpen = { onOpen(row.id) }, trailing = null)
                }
            }
        }
    }
}

// On a split ticket: what became of it, and its subtasks with live status
@Composable
private fun SplitSummary(
    ticket: Ticket,
    subtasks: List<Ticket>,
    liveStatus: (Ticket) -> String,
    onOpen: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Subtasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (ticket.isCategory) "Split into subtasks \u00B7 shown as a category on the GANTT and Burndown charts, off the Kanban board"
                else "Split into subtasks \u00B7 hidden from the boards and charts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            subtasks.forEach { sub ->
                val status = liveStatus(sub)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "#${sub.id} ${sub.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClickLabel = "Open subtask") { onOpen(sub.id) }
                            .padding(vertical = 4.dp)
                    )
                    if (sub.hasEstimate) {
                        Text(
                            sub.shortEstimate,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    if (sub.isSplit) {
                        Text(
                            "Split",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    Text(
                        status.replace("_", " ").replace("-", " ")
                            .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColorsByKey[status] ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DependencyRowItem(
    row: DependencyRow,
    canOpen: Boolean,
    onOpen: () -> Unit,
    trailing: (@Composable () -> Unit)?
) {
    val statusColor = statusColorsByKey[row.status] ?: MaterialTheme.colorScheme.primary
    val statusLabel = row.status.replace("_", " ").replace("-", " ")
        .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = canOpen, onClickLabel = "Open ticket", onClick = onOpen)
                .padding(vertical = 4.dp)
        ) {
            Text(
                "#${row.id} ${row.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (row.pending == "remove") TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
                when {
                    row.pending == "add" -> Text("unsaved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    row.pending == "remove" -> Text("removing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    !canOpen -> Text("in another project", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        trailing?.invoke()
    }
}

// Estimate as an amount plus the unit it's entered in ("3" + "days"); blank
// amount = not estimated. Shows the backend's range error inline.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EstimateInput(
    amount: String,
    unit: String,
    enabled: Boolean,
    placeholder: String,
    onAmountChange: (String) -> Unit,
    onUnitChange: (String) -> Unit
) {
    var unitExpanded by remember { mutableStateOf(false) }
    val error = EstimateUnits.validate(amount)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            label = { Text("Estimate") },
            placeholder = { Text(placeholder) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = enabled,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("estimate-amount")
        )
        ExposedDropdownMenuBox(
            expanded = unitExpanded,
            onExpandedChange = { if (enabled) unitExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = unit,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text("Unit") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = unitExpanded,
                onDismissRequest = { unitExpanded = false }
            ) {
                EstimateUnits.ALL.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onUnitChange(option)
                            unitExpanded = false
                        }
                    )
                }
            }
        }
    }
}

// Appears once anything is changed; nothing is saved until Save Changes
@Composable
private fun SaveBar(
    isDirty: Boolean,
    isSaving: Boolean,
    error: String?,
    onDiscard: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ticket-save-bar")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (isDirty) {
                Text(
                    "You have unsaved changes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = isDirty && !isSaving,
                    modifier = Modifier.weight(1f)
                ) { Text("Discard") }
                Button(
                    onClick = onSave,
                    enabled = isDirty && !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ticket-save-changes")
                ) { Text(if (isSaving) "Saving..." else "Save Changes") }
            }
        }
    }
}

@Composable
private fun LeavePromptDialog(onChoice: (LeaveChoice) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoice(LeaveChoice.KEEP_EDITING) },
        title = { Text("Unsaved changes") },
        text = { Text("You have unsaved changes to this ticket. Save them before leaving?") },
        confirmButton = {
            Button(onClick = { onChoice(LeaveChoice.SAVE) }) { Text("Save Changes") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onChoice(LeaveChoice.KEEP_EDITING) }) { Text("Keep Editing") }
                TextButton(onClick = { onChoice(LeaveChoice.DISCARD) }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
private fun CommentItemProject(
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text(text = comment.content, style = MaterialTheme.typography.bodySmall)
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
private fun EditTicketForm(
    title: String,
    description: String,
    priority: String,
    enabled: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onBackToTicket: () -> Unit
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
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            isError = title.isBlank(),
            supportingText = if (title.isBlank()) {
                { Text("Title is required") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            enabled = enabled,
            minLines = 4,
            maxLines = 8
        )

        ExposedDropdownMenuBox(
            expanded = priorityExpanded,
            onExpandedChange = { if (enabled) priorityExpanded = it }
        ) {
            OutlinedTextField(
                value = priority.replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
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
                        text = { Text(text = p.replaceFirstChar { it.uppercase() }, color = color) },
                        onClick = {
                            onPriorityChange(p)
                            priorityExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedButton(onClick = onBackToTicket, modifier = Modifier.fillMaxWidth()) {
            Text("Back to ticket")
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
    assigneeId: Int?,
    assignees: List<ProjectAssignee>,
    enabled: Boolean,
    onAssign: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = assignees.find { it.user_id == assigneeId }

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
                onExpandedChange = { if (enabled) expanded = it }
            ) {
                OutlinedTextField(
                    value = current?.displayName ?: if (assigneeId == null) "Unassigned" else "User #$assigneeId",
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) }
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
                    assignees.forEach { person ->
                        DropdownMenuItem(
                            text = { Text(person.displayName) },
                            onClick = {
                                onAssign(person.user_id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// Picking a status stages it (the chosen one is filled in); picking it
// again puts back the saved status
@Composable
private fun StatusTransitionSection(
    transitions: List<String>,
    chosenStatus: String,
    enabled: Boolean,
    onChoose: (String) -> Unit
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
                transitions.forEach { targetStatus ->
                    val color = statusColorsByKey[targetStatus] ?: MaterialTheme.colorScheme.primary
                    val label = targetStatus.replace("_", " ").replace("-", " ")
                        .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    val chosen = chosenStatus == targetStatus
                    val modifier = Modifier
                        .weight(1f)
                        .semantics { selected = chosen }
                    if (chosen) {
                        Button(
                            onClick = { onChoose(targetStatus) },
                            enabled = enabled,
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = modifier
                        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    } else {
                        OutlinedButton(
                            onClick = { onChoose(targetStatus) },
                            enabled = enabled,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                            modifier = modifier
                        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTicketDialog(
    isCreating: Boolean,
    canEstimate: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        title: String, description: String?, priority: String,
        estimateAmount: Double?, estimateUnit: String, files: List<PendingFile>
    ) -> Unit
) {
    var files by remember { mutableStateOf(emptyList<PendingFile>()) }
    var filesError by remember { mutableStateOf<String?>(null) }
    val pickFiles = rememberAttachmentPicker { picked ->
        filesError = attachmentLimitError(picked, files.size)
        if (filesError == null) files = files + picked
    }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
    var estimateAmount by remember { mutableStateOf("") }
    var estimateUnit by remember { mutableStateOf("hours") }
    val estimateError = EstimateUnits.validate(estimateAmount)
    var priorityExpanded by remember { mutableStateOf(false) }

    val priorities = listOf("low", "medium", "high", "urgent")

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = {
            Text(
                text = "Create New Ticket",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    placeholder = { Text("Brief description of the issue") },
                    enabled = !isCreating,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Description field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Provide details about the issue...") },
                    enabled = !isCreating,
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                // Priority dropdown
                ExposedDropdownMenuBox(
                    expanded = priorityExpanded,
                    onExpandedChange = { if (!isCreating) priorityExpanded = it }
                ) {
                    OutlinedTextField(
                        value = priority.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                        enabled = !isCreating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = priorityExpanded,
                        onDismissRequest = { priorityExpanded = false }
                    ) {
                        priorities.forEach { p ->
                            val color = priorityColors[p] ?: MaterialTheme.colorScheme.onSurface
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = p.replaceFirstChar { it.uppercase() },
                                        color = color
                                    )
                                },
                                onClick = {
                                    priority = p
                                    priorityExpanded = false
                                }
                            )
                        }
                    }
                }

                if (canEstimate) {
                    EstimateInput(
                        amount = estimateAmount,
                        unit = estimateUnit,
                        enabled = !isCreating,
                        placeholder = "e.g. 4",
                        onAmountChange = { estimateAmount = it },
                        onUnitChange = { estimateUnit = it }
                    )
                }

                // Attachments upload once the ticket exists
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { filesError = null; pickFiles() }, enabled = !isCreating) {
                        Text("Attach files")
                    }
                    files.forEachIndexed { i, f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                f.name + if (f.size >= 0) " (${formatFileSize(f.size)})" else "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { files = files.filterIndexed { j, _ -> j != i } },
                                enabled = !isCreating
                            ) { Icon(Icons.Filled.Close, contentDescription = "Don't attach ${f.name}") }
                        }
                    }
                    Text(
                        filesError ?: "Images, spreadsheets, documents — up to 25 MB each, 10 files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (filesError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isCreating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(title, description.ifBlank { null }, priority, estimateAmount.trim().toDoubleOrNull(), estimateUnit, files)
                },
                enabled = !isCreating && title.isNotBlank() && estimateError == null
            ) {
                Text(if (isCreating) "Creating..." else "Create Ticket")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Cancel")
            }
        }
    )
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
