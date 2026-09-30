package com.cherryblossomdev.breakroom.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.HelpDeskRepository
import com.cherryblossomdev.breakroom.data.ProjectRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.Project
import com.cherryblossomdev.breakroom.data.models.ProjectAssignee
import com.cherryblossomdev.breakroom.data.models.Ticket
import com.cherryblossomdev.breakroom.data.models.TicketComment
import com.cherryblossomdev.breakroom.ui.components.AccessibilityAnnouncement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Valid status transitions matching web version
object StatusTransitions {
    private val transitions = mapOf(
        "open" to listOf("backlog", "in_progress"),
        "backlog" to listOf("on-deck", "in_progress"),
        "on-deck" to listOf("backlog", "in_progress"),
        "in_progress" to listOf("on-deck", "resolved"),
        "resolved" to listOf("in_progress", "closed"),
        "closed" to listOf("resolved")
    )

    fun getValidTransitions(currentStatus: String): List<String> {
        return transitions[currentStatus] ?: emptyList()
    }
}

// Kanban status definitions
enum class KanbanStatus(val apiValue: String, val displayName: String) {
    BACKLOG("backlog", "Backlog"),
    ON_DECK("on-deck", "On Deck"),
    IN_PROGRESS("in_progress", "In Progress"),
    RESOLVED("resolved", "Resolved"),
    CLOSED("closed", "Closed");

    companion object {
        fun fromApiValue(value: String): KanbanStatus {
            return entries.find { it.apiValue == value } ?: BACKLOG
        }

        // Board lanes. Closed tickets get no lane -- the board links to a
        // closed-tickets list instead (web 0f30e57).
        val allStatuses = listOf(BACKLOG, ON_DECK, IN_PROGRESS, RESOLVED)
    }
}

// Every change made in the ticket panel is staged here; nothing reaches the
// backend until Save Changes (web 8ae60e0). Estimates, dependencies and
// attachments join this in later steps.
data class TicketDraft(
    val title: String,
    val description: String,
    val priority: String,
    val status: String,
    val assignedTo: Int?
) {
    companion object {
        fun from(ticket: Ticket) = TicketDraft(
            title = ticket.title,
            description = ticket.description ?: "",
            priority = ticket.priority,
            status = ticket.status,
            assignedTo = ticket.assigned_to ?: ticket.assignee_id
        )
    }
}

// Why the unsaved-changes prompt is up: closing the ticket, or leaving the
// project workspace altogether
enum class LeaveTarget { CLOSE_TICKET, LEAVE_WORKSPACE }

data class ProjectTicketsUiState(
    val project: Project? = null,
    // Set when the project itself couldn't be loaded (404 / 403 / network);
    // the workspace shows it in place of the board
    val loadError: String? = null,
    val canWork: Boolean = false,
    val canManage: Boolean = false,
    // People a ticket here can be assigned to (employees + working members)
    val assignees: List<ProjectAssignee> = emptyList(),
    val showingClosed: Boolean = false,
    val announcement: AccessibilityAnnouncement? = null,
    val tickets: List<Ticket> = emptyList(),
    val ticketsByStatus: Map<KanbanStatus, List<Ticket>> = emptyMap(),
    val currentStatusIndex: Int = 0,
    val selectedTicket: Ticket? = null,
    val currentUsername: String = "",
    val isLoading: Boolean = false,
    val isCreatingTicket: Boolean = false,
    val showCreateDialog: Boolean = false,
    // Title/description/priority form; its edits go to the draft too
    val isEditing: Boolean = false,
    val draft: TicketDraft? = null,
    val original: TicketDraft? = null,
    val isSavingChanges: Boolean = false,
    val saveError: String? = null,
    val leavePrompt: LeaveTarget? = null,
    // One-shot: the prompt resolved in favour of leaving the workspace
    val exitWorkspace: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val ticketComments: List<TicketComment> = emptyList(),
    val commentText: String = "",
    val isPostingComment: Boolean = false,
    val editingCommentId: Int? = null,
    val editCommentText: String = ""
) {
    // Most recently closed first. resolved_at is stamped when a ticket moves
    // to closed, so for closed tickets it's the close time.
    val closedTickets: List<Ticket>
        get() = tickets.filter { it.status == "closed" }
            .sortedByDescending { it.resolved_at ?: it.updated_at ?: "" }

    val isCreator: Boolean
        get() = selectedTicket?.creator_handle == currentUsername

    // Changed ticket fields, as a PUT /api/helpdesk/ticket body
    val changedFields: Map<String, Any?>
        get() {
            val d = draft ?: return emptyMap()
            val o = original ?: return emptyMap()
            return buildMap {
                if (d.title != o.title) put("title", d.title.trim())
                if (d.description != o.description) put("description", d.description)
                if (d.priority != o.priority) put("priority", d.priority)
                if (d.status != o.status) put("status", d.status)
                if (d.assignedTo != o.assignedTo) put("assigned_to", d.assignedTo)
            }
        }

    // A comment being written, or an edit to one, that hasn't been posted
    // (web c5a1c53: counts as unsaved and is posted by Save Changes)
    val commentEditChanged: Boolean
        get() {
            val id = editingCommentId ?: return false
            val comment = ticketComments.find { it.id == id } ?: return false
            return editCommentText.trim() != comment.content.trim()
        }

    val hasUnpostedComment: Boolean
        get() = commentText.isNotBlank() || commentEditChanged

    val isDirty: Boolean
        get() = draft != null && (changedFields.isNotEmpty() || hasUnpostedComment)

    // Employees / working members can make any valid move; the ticket's
    // creator may only resolve or close it (mirrors PUT /api/helpdesk/ticket)
    val allowedTransitions: List<String>
        get() {
            val ticket = selectedTicket ?: return emptyList()
            val transitions = StatusTransitions.getValidTransitions(ticket.status)
            return when {
                canWork -> transitions
                isCreator -> transitions.filter { it == "resolved" || it == "closed" }
                else -> emptyList()
            }
        }

    // Anyone may file into a public or Help Desk project
    val canCreateTickets: Boolean
        get() = canWork || project?.isPublic == true || project?.isDefault == true
}

class ProjectTicketsViewModel(
    private val projectRepository: ProjectRepository,
    private val helpDeskRepository: HelpDeskRepository,
    private val projectId: Int,
    private val currentUsername: String
) : ViewModel() {

    companion object {
        private const val TAG = "ProjectTicketsVM"
    }

    private val _uiState = MutableStateFlow(ProjectTicketsUiState(currentUsername = currentUsername))
    val uiState: StateFlow<ProjectTicketsUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "ViewModel created for project $projectId")
        loadProjectTickets()
    }

    fun loadProjectTickets() {
        Log.d(TAG, "loadProjectTickets: Starting load for project $projectId")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, loadError = null) }
            when (val result = projectRepository.getProject(projectId)) {
                is BreakroomResult.Success -> {
                    val tickets = result.data.tickets
                    Log.d(TAG, "loadProjectTickets: Success - got ${tickets.size} tickets")
                    _uiState.update {
                        it.copy(
                            project = result.data.project,
                            tickets = tickets,
                            ticketsByStatus = groupTicketsByStatus(tickets),
                            canWork = result.data.canWork,
                            canManage = result.data.canManage,
                            assignees = result.data.assignees ?: emptyList(),
                            isLoading = false
                        )
                    }
                }
                is BreakroomResult.Error -> {
                    Log.e(TAG, "loadProjectTickets: Error - ${result.message}")
                    failLoad(result.message)
                }
                else -> {
                    Log.e(TAG, "loadProjectTickets: Auth error")
                    failLoad("Session expired - please log in again")
                }
            }
        }
    }

    // Before the project has loaded, a failure replaces the board; after, it's
    // a transient message over the board that's already there
    private fun failLoad(message: String) {
        _uiState.update {
            if (it.project == null) it.copy(isLoading = false, loadError = message)
            else it.copy(isLoading = false, error = message)
        }
    }

    fun showClosedTickets() {
        _uiState.update { it.copy(showingClosed = true) }
    }

    fun hideClosedTickets() {
        _uiState.update { it.copy(showingClosed = false) }
    }

    private fun groupTicketsByStatus(tickets: List<Ticket>): Map<KanbanStatus, List<Ticket>> =
        KanbanStatus.allStatuses.associateWith { status ->
            tickets.filter { KanbanStatus.fromApiValue(it.status) == status }
        }

    fun setCurrentStatusIndex(index: Int) {
        if (index in 0 until KanbanStatus.allStatuses.size) {
            _uiState.update { it.copy(currentStatusIndex = index) }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    // ---- Ticket panel ----

    fun selectTicket(ticket: Ticket) {
        Log.d(TAG, "selectTicket: Selected ticket ${ticket.id}")
        _uiState.update {
            it.copy(
                selectedTicket = ticket,
                draft = TicketDraft.from(ticket),
                original = TicketDraft.from(ticket),
                isEditing = false,
                saveError = null,
                ticketComments = emptyList(),
                commentText = "",
                editingCommentId = null,
                editCommentText = ""
            )
        }
        loadComments(ticket.id)
    }

    private fun closeTicket() {
        _uiState.update {
            it.copy(
                selectedTicket = null,
                draft = null,
                original = null,
                isEditing = false,
                saveError = null,
                ticketComments = emptyList(),
                commentText = "",
                editingCommentId = null,
                editCommentText = ""
            )
        }
    }

    /** Close the ticket panel, asking first if there are unsaved changes. */
    fun requestCloseTicket() {
        if (_uiState.value.isDirty) {
            _uiState.update { it.copy(leavePrompt = LeaveTarget.CLOSE_TICKET) }
        } else {
            closeTicket()
        }
    }

    /**
     * Called before the workspace is left (Back from any section). Returns
     * true if leaving has to wait for the unsaved-changes prompt; the prompt
     * then sets exitWorkspace once the user saves or discards.
     */
    fun interceptLeaveWorkspace(): Boolean {
        if (!_uiState.value.isDirty) return false
        _uiState.update { it.copy(leavePrompt = LeaveTarget.LEAVE_WORKSPACE) }
        return true
    }

    fun onWorkspaceExited() {
        _uiState.update { it.copy(exitWorkspace = false) }
    }

    fun resolveLeavePrompt(choice: LeaveChoice) {
        val target = _uiState.value.leavePrompt ?: return
        _uiState.update { it.copy(leavePrompt = null) }
        when (choice) {
            LeaveChoice.KEEP_EDITING -> {}
            LeaveChoice.DISCARD -> {
                discardChanges()
                finishLeave(target)
            }
            LeaveChoice.SAVE -> viewModelScope.launch {
                if (saveChangesNow()) finishLeave(target)
            }
        }
    }

    private fun finishLeave(target: LeaveTarget) {
        closeTicket()
        if (target == LeaveTarget.LEAVE_WORKSPACE) _uiState.update { it.copy(exitWorkspace = true) }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
    }

    // Leaves the title/description/priority form; its edits stay staged
    fun backToTicket() {
        _uiState.update { it.copy(isEditing = false) }
    }

    private fun updateDraft(transform: (TicketDraft) -> TicketDraft) {
        _uiState.update { state -> state.draft?.let { state.copy(draft = transform(it), saveError = null) } ?: state }
    }

    fun updateEditTitle(title: String) = updateDraft { it.copy(title = title) }

    fun updateEditDescription(description: String) = updateDraft { it.copy(description = description) }

    fun updateEditPriority(priority: String) = updateDraft { it.copy(priority = priority) }

    fun chooseAssignee(userId: Int?) = updateDraft { it.copy(assignedTo = userId) }

    // Status buttons pick the draft's status; picking the chosen one again
    // puts back the saved status
    fun chooseStatus(status: String) {
        val original = _uiState.value.original ?: return
        updateDraft { it.copy(status = if (it.status == status) original.status else status) }
    }

    fun discardChanges() {
        _uiState.update { state ->
            val ticket = state.selectedTicket
            state.copy(
                draft = ticket?.let { TicketDraft.from(it) },
                original = ticket?.let { TicketDraft.from(it) },
                isEditing = false,
                saveError = null,
                commentText = "",
                editingCommentId = null,
                editCommentText = ""
            )
        }
    }

    fun saveChanges() {
        viewModelScope.launch { saveChangesNow() }
    }

    // Returns true when everything saved. On failure the unsaved part stays
    // in the draft and the error shows in the save bar.
    private suspend fun saveChangesNow(): Boolean {
        val state = _uiState.value
        val ticket = state.selectedTicket ?: return false
        if (!state.isDirty) return true
        if (state.isSavingChanges) return false

        val fields = state.changedFields
        if (fields.containsKey("title") && (fields["title"] as String).isBlank()) {
            _uiState.update { it.copy(saveError = "Title is required") }
            return false
        }

        _uiState.update { it.copy(isSavingChanges = true, saveError = null) }
        try {
            if (fields.isNotEmpty()) {
                when (val result = projectRepository.updateTicketFields(ticket.id, fields)) {
                    is BreakroomResult.Success -> applySavedTicket(result.data, statusChanged = fields.containsKey("status"))
                    is BreakroomResult.Error -> return failSave(result.message)
                    else -> return failSave("Session expired - please log in again")
                }
            }

            if (_uiState.value.commentEditChanged && !saveEditCommentNow()) {
                return failSave("Failed to save your comment edit")
            }
            if (_uiState.value.commentText.isNotBlank() && !postCommentNow()) {
                return failSave("Failed to post your comment")
            }

            _uiState.update { it.copy(isEditing = false, successMessage = "Changes saved") }
            return true
        } finally {
            _uiState.update { it.copy(isSavingChanges = false) }
        }
    }

    private fun failSave(message: String): Boolean {
        _uiState.update { it.copy(saveError = message) }
        return false
    }

    // Merge a saved ticket (PUT response) into the panel and the board, and
    // make it the new baseline for the draft
    private fun applySavedTicket(saved: Ticket, statusChanged: Boolean) {
        _uiState.update { state ->
            val tickets = state.tickets.map { if (it.id == saved.id) saved else it }
            state.copy(
                tickets = tickets,
                ticketsByStatus = groupTicketsByStatus(tickets),
                selectedTicket = saved,
                draft = TicketDraft.from(saved),
                original = TicketDraft.from(saved),
                announcement = if (statusChanged) {
                    AccessibilityAnnouncement(text = "Status changed to ${saved.formattedStatus}")
                } else state.announcement
            )
        }
    }

    // ---- Comments ----

    private fun loadComments(ticketId: Int) {
        viewModelScope.launch {
            when (val result = helpDeskRepository.getComments(ticketId)) {
                is BreakroomResult.Success -> _uiState.update { it.copy(ticketComments = result.data) }
                else -> { /* non-fatal */ }
            }
        }
    }

    fun updateCommentText(text: String) {
        _uiState.update { it.copy(commentText = text) }
    }

    fun addComment() {
        viewModelScope.launch { postCommentNow() }
    }

    // Clears the text only on success
    private suspend fun postCommentNow(): Boolean {
        val ticketId = _uiState.value.selectedTicket?.id ?: return false
        val content = _uiState.value.commentText.trim()
        if (content.isEmpty()) return true

        _uiState.update { it.copy(isPostingComment = true) }
        return when (val result = helpDeskRepository.addComment(ticketId, content)) {
            is BreakroomResult.Success -> {
                _uiState.update {
                    it.copy(ticketComments = it.ticketComments + result.data, commentText = "", isPostingComment = false)
                }
                true
            }
            is BreakroomResult.Error -> {
                _uiState.update { it.copy(isPostingComment = false, error = result.message) }
                false
            }
            else -> {
                _uiState.update { it.copy(isPostingComment = false, error = "Session expired") }
                false
            }
        }
    }

    fun startEditComment(commentId: Int, content: String) {
        _uiState.update { it.copy(editingCommentId = commentId, editCommentText = content) }
    }

    fun updateEditCommentText(text: String) {
        _uiState.update { it.copy(editCommentText = text) }
    }

    fun cancelEditComment() {
        _uiState.update { it.copy(editingCommentId = null, editCommentText = "") }
    }

    fun saveEditComment() {
        viewModelScope.launch { saveEditCommentNow() }
    }

    private suspend fun saveEditCommentNow(): Boolean {
        val commentId = _uiState.value.editingCommentId ?: return true
        val content = _uiState.value.editCommentText.trim()
        if (content.isEmpty()) return false

        return when (val result = helpDeskRepository.updateComment(commentId, content)) {
            is BreakroomResult.Success -> {
                _uiState.update { state ->
                    state.copy(
                        ticketComments = state.ticketComments.map { if (it.id == commentId) result.data else it },
                        editingCommentId = null,
                        editCommentText = ""
                    )
                }
                true
            }
            is BreakroomResult.Error -> {
                _uiState.update { it.copy(error = result.message) }
                false
            }
            else -> false
        }
    }

    fun deleteComment(commentId: Int) {
        viewModelScope.launch {
            when (helpDeskRepository.deleteComment(commentId)) {
                is BreakroomResult.Success -> _uiState.update { state ->
                    state.copy(ticketComments = state.ticketComments.map {
                        if (it.id == commentId) it.copy(is_deleted = 1) else it
                    })
                }
                else -> { /* non-fatal */ }
            }
        }
    }

    // ---- New ticket ----

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun createTicket(title: String, description: String?, priority: String) {
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "Title is required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingTicket = true, error = null) }
            Log.d(TAG, "createTicket: Creating ticket for project $projectId")
            when (val result = projectRepository.createTicket(projectId, title, description, priority)) {
                is BreakroomResult.Success -> {
                    Log.d(TAG, "createTicket: Success - ticket ${result.data.id} created")
                    _uiState.update {
                        val tickets = it.tickets + result.data
                        it.copy(
                            tickets = tickets,
                            ticketsByStatus = groupTicketsByStatus(tickets),
                            isCreatingTicket = false,
                            showCreateDialog = false,
                            successMessage = "Ticket created"
                        )
                    }
                }
                is BreakroomResult.Error -> {
                    Log.e(TAG, "createTicket: Error - ${result.message}")
                    _uiState.update { it.copy(isCreatingTicket = false, error = result.message) }
                }
                else -> _uiState.update { it.copy(isCreatingTicket = false, error = "Session expired - please log in again") }
            }
        }
    }
}

enum class LeaveChoice { KEEP_EDITING, DISCARD, SAVE }
