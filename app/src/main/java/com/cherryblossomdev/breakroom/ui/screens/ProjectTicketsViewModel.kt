package com.cherryblossomdev.breakroom.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.HelpDeskRepository
import com.cherryblossomdev.breakroom.data.ProjectRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.EstimateUnits
import com.cherryblossomdev.breakroom.data.models.Project
import com.cherryblossomdev.breakroom.data.models.ProjectAssignee
import com.cherryblossomdev.breakroom.data.models.SplitSubtask
import com.cherryblossomdev.breakroom.data.models.Ticket
import com.cherryblossomdev.breakroom.data.models.TicketAttachment
import com.cherryblossomdev.breakroom.data.models.TicketComment
import com.cherryblossomdev.breakroom.data.models.TicketDependency
import com.cherryblossomdev.breakroom.data.models.TicketTimelineEntry
import com.cherryblossomdev.breakroom.projects.BacklogEntry
import com.cherryblossomdev.breakroom.projects.BacklogRow
import com.cherryblossomdev.breakroom.projects.BacklogTree
import com.cherryblossomdev.breakroom.ui.components.AccessibilityAnnouncement
import com.cherryblossomdev.breakroom.ui.components.PendingFile
import com.cherryblossomdev.breakroom.ui.components.attachmentLimitError
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Valid status transitions matching web version
object StatusTransitions {
    private val transitions = mapOf(
        // Help Desk tickets start 'open'; on a board they're backlog (web 1f2f954)
        "open" to listOf("on-deck", "in_progress"),
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

        // Board lanes. Backlog and Closed tickets get no lane -- the board
        // links to a backlog list and a closed-tickets list instead (web
        // 1f2f954, 0f30e57).
        val allStatuses = listOf(ON_DECK, IN_PROGRESS, RESOLVED)
    }
}

// Every change made in the ticket panel is staged here; nothing reaches the
// backend until Save Changes (web 8ae60e0).
data class TicketDraft(
    val title: String,
    val description: String,
    val priority: String,
    val status: String,
    val assignedTo: Int?,
    // Estimate as typed ("" = not estimated) + its unit (migration 083)
    val estimateAmount: String,
    val estimateUnit: String,
    // Dependency ids to add / saved dependency ids to remove on save
    val addDeps: List<Int> = emptyList(),
    val removeDeps: List<Int> = emptyList(),
    // Files to upload / saved attachment ids to delete on save (migration 085)
    val addFiles: List<PendingFile> = emptyList(),
    val removeAttachments: List<Int> = emptyList()
) {
    // "" when not estimated -- the unit alone doesn't count as a change
    val estimateKey: String
        get() = estimateAmount.trim().toDoubleOrNull()?.let { "$it $estimateUnit" }
            ?: estimateAmount.trim()

    companion object {
        fun from(ticket: Ticket) = TicketDraft(
            title = ticket.title,
            description = ticket.description ?: "",
            priority = ticket.priority,
            status = ticket.status,
            assignedTo = ticket.assigned_to ?: ticket.assignee_id,
            estimateAmount = ticket.estimateAmount?.takeIf { ticket.hasEstimate }
                ?.let { EstimateUnits.formatAmount(it) } ?: "",
            estimateUnit = ticket.estimate_unit?.takeIf { ticket.hasEstimate } ?: "hours"
        )
    }
}

// Why the unsaved-changes prompt is up: closing the ticket, opening a linked
// ticket, or leaving the project workspace altogether
sealed class LeaveTarget {
    data object CloseTicket : LeaveTarget()
    data object LeaveWorkspace : LeaveTarget()
    data class OpenTicket(val ticketId: Int) : LeaveTarget()
    // Splitting works from the saved ticket, so edits are settled first
    data object OpenSplit : LeaveTarget()
}

// A row in the panel's "Depends on" / "Blocking" lists. pending: "add" (not
// saved yet), "remove" (still listed, struck through, until saved) or null.
data class DependencyRow(
    val id: Int,
    val title: String,
    val status: String,
    val pending: String? = null
)

private fun isDone(status: String?) = status == "resolved" || status == "closed"

// Backlog, including Help Desk tickets still in the legacy 'open' status
fun Ticket.isInBacklog(): Boolean = status == "backlog" || status == "open"

data class ProjectTicketsUiState(
    val project: Project? = null,
    // Set when the project itself couldn't be loaded (404 / 403 / network);
    // the workspace shows it in place of the board
    val loadError: String? = null,
    val canWork: Boolean = false,
    val canManage: Boolean = false,
    // People a ticket here can be assigned to (employees + working members)
    val assignees: List<ProjectAssignee> = emptyList(),
    // Edges touching this project's tickets; either end may be in another
    // project of the same company
    val dependencies: List<TicketDependency> = emptyList(),
    // [{ ticket_id, started_at, done_at }] from status history (GANTT)
    val timeline: List<TicketTimelineEntry> = emptyList(),
    // Tickets split into subtasks (migration 086); off the board
    val splitParents: List<Ticket> = emptyList(),
    val showingClosed: Boolean = false,
    val showingBacklog: Boolean = false,
    val showSplitDialog: Boolean = false,
    val isSplitting: Boolean = false,
    val splitError: String? = null,
    // Backlog list search: #id or words in the title
    val backlogSearch: String = "",
    // Split parents whose backlog group is collapsed
    val collapsedGroups: Set<Int> = emptySet(),
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
    val ticketAttachments: List<TicketAttachment> = emptyList(),
    val attachmentError: String? = null,
    val isOpeningAttachment: Boolean = false,
    // One-shot: a downloaded attachment for the screen to hand to another app
    val openedFile: Pair<File, String>? = null,
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

    // The API orders tickets by priority, then newest first
    val backlogTickets: List<Ticket>
        get() = tickets.filter { it.isInBacklog() }

    // Backlog tickets grouped under their split parents, nested, in order
    val backlogTree: List<BacklogEntry>
        get() = BacklogTree.build(backlogTickets) { ancestorsOf(it) }

    // What the list shows: search and collapsed groups applied
    val backlogRows: List<BacklogRow>
        get() {
            val tree = backlogTree
            return BacklogTree.rows(tree, BacklogTree.visibleKeys(tree, backlogSearch), collapsedGroups)
        }

    data class GroupStats(val total: Int, val inBacklog: Int, val done: Int)

    // Direct subtasks; one that was split again counts by its own subtasks' progress
    fun groupStats(parent: Ticket): GroupStats {
        val statuses = subtasksOf(parent.id).map { liveStatus(it.id, it.status) }
        return GroupStats(
            total = statuses.size,
            inBacklog = statuses.count { it == "backlog" || it == "open" },
            done = statuses.count { isDone(it) }
        )
    }

    val splitParentsById: Map<Int, Ticket>
        get() = splitParents.associateBy { it.id }

    // Tickets the panel can open: the board's, plus split parents (off the
    // board, but reachable from their subtasks)
    val openableById: Map<Int, Ticket>
        get() = ticketsById + splitParentsById

    // A subtask's split parent
    fun parentOf(ticket: Ticket): Ticket? = ticket.parent_ticket_id?.let { splitParentsById[it] }

    // The split tickets above this one, outermost first
    fun ancestorsOf(ticket: Ticket): List<Ticket> {
        val byId = splitParentsById
        val chain = mutableListOf<Ticket>()
        val seen = mutableSetOf<Int>()
        var p = ticket.parent_ticket_id?.let { byId[it] }
        while (p != null && seen.add(p.id)) {
            chain.add(0, p)
            p = p.parent_ticket_id?.let { byId[it] }
        }
        return chain
    }

    // Splits nest to any depth, so a ticket's subtasks can include split
    // tickets of their own (in splitParents rather than on the board)
    fun subtasksOf(id: Int): List<Ticket> = (tickets + splitParents).filter { it.parent_ticket_id == id }

    // Split: any open ticket, subtasks included; finished work isn't split
    val canSplit: Boolean
        get() = selectedTicket != null && canWork && !isDone(selectedTicket.status)

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
                if (d.estimateKey != o.estimateKey) {
                    // null amount clears the estimate
                    put("estimate_amount", d.estimateAmount.trim().toDoubleOrNull())
                    put("estimate_unit", d.estimateUnit)
                }
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

    // Every change made in the panel must land here, or the save bar won't
    // appear and the leave prompt won't warn about it
    val isDirty: Boolean
        get() = draft != null && (
            changedFields.isNotEmpty() || draft.addDeps.isNotEmpty() || draft.removeDeps.isNotEmpty() ||
                draft.addFiles.isNotEmpty() || draft.removeAttachments.isNotEmpty() || hasUnpostedComment
            )

    // Creator or anyone who can work the ticket may attach; the uploader or
    // anyone who can work it may remove (mirrors routes/helpdesk.js)
    val canAttach: Boolean
        get() = selectedTicket != null && (canWork || isCreator)

    fun canRemoveAttachment(a: TicketAttachment): Boolean = canWork || a.uploader_handle == currentUsername

    val ticketsById: Map<Int, Ticket>
        get() = tickets.associateBy { it.id }

    // Prefer the board's live status over the one captured in the edge. A
    // split ticket's own status goes stale -- its subtasks carry the work --
    // so anything depending on it follows them: done once they all are.
    // Subtasks that were split in turn follow their own subtasks.
    fun liveStatus(id: Int, fallback: String?, seen: MutableSet<Int> = mutableSetOf()): String {
        ticketsById[id]?.let { return it.status }
        if (id in splitParentsById && seen.add(id)) {
            val subs = subtasksOf(id).map { liveStatus(it.id, it.status, seen) }
            if (subs.isNotEmpty()) {
                if (subs.all { isDone(it) }) return "resolved"
                return if (subs.any { it != "backlog" && it != "open" }) "in_progress" else "backlog"
            }
        }
        return fallback ?: ""
    }

    // ticket id -> ids of its unfinished dependencies (the card's Blocked chip)
    val openBlockersByTicket: Map<Int, List<Int>>
        get() = dependencies
            .filterNot { isDone(liveStatus(it.depends_on_ticket_id, it.depends_on_status)) }
            .groupBy({ it.ticket_id }, { it.depends_on_ticket_id })

    // Saved dependencies of the open ticket plus the draft's pending ones
    val selectedDependsOn: List<DependencyRow>
        get() {
            val ticket = selectedTicket ?: return emptyList()
            val removing = draft?.removeDeps.orEmpty().toSet()
            val saved = dependencies.filter { it.ticket_id == ticket.id }.map {
                DependencyRow(
                    id = it.depends_on_ticket_id,
                    title = it.depends_on_title ?: "Ticket #${it.depends_on_ticket_id}",
                    status = liveStatus(it.depends_on_ticket_id, it.depends_on_status),
                    pending = if (it.depends_on_ticket_id in removing) "remove" else null
                )
            }
            val adding = draft?.addDeps.orEmpty().mapNotNull { id ->
                ticketsById[id]?.let { DependencyRow(it.id, it.title, it.status, pending = "add") }
            }
            return saved + adding
        }

    val selectedBlocking: List<DependencyRow>
        get() {
            val ticket = selectedTicket ?: return emptyList()
            return dependencies.filter { it.depends_on_ticket_id == ticket.id }.map {
                DependencyRow(
                    id = it.ticket_id,
                    title = it.ticket_title ?: "Ticket #${it.ticket_id}",
                    status = liveStatus(it.ticket_id, it.ticket_status)
                )
            }
        }

    // Tickets on this board the open ticket could depend on: not itself, not
    // already a dependency, and not anything that (as far as this board
    // knows) already depends on it -- that would be a loop. The server
    // re-checks loops across the whole company.
    val dependencyCandidates: List<Ticket>
        get() {
            val ticket = selectedTicket ?: return emptyList()
            val dependents = mutableSetOf(ticket.id)
            var grew = true
            while (grew) {
                grew = false
                for (d in dependencies) {
                    if (d.depends_on_ticket_id in dependents && dependents.add(d.ticket_id)) grew = true
                }
            }
            val already = selectedDependsOn.map { it.id }.toSet()
            return tickets.filter { it.id !in dependents && it.id !in already }.sortedBy { it.id }
        }

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
        viewModelScope.launch { reloadProject() }
    }

    private suspend fun reloadProject() {
        Log.d(TAG, "loadProjectTickets: Starting load for project $projectId")
        run {
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
                            dependencies = result.data.dependencies ?: emptyList(),
                            timeline = result.data.timeline ?: emptyList(),
                            splitParents = result.data.split_parents ?: emptyList(),
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
        _uiState.update { it.copy(showingClosed = true, showingBacklog = false) }
    }

    fun showBacklog() {
        _uiState.update { it.copy(showingBacklog = true, showingClosed = false) }
    }

    fun hideBacklog() {
        _uiState.update { it.copy(showingBacklog = false, backlogSearch = "") }
    }

    fun updateBacklogSearch(text: String) {
        _uiState.update { it.copy(backlogSearch = text) }
    }

    fun toggleBacklogGroup(parentId: Int) {
        _uiState.update {
            val c = it.collapsedGroups
            it.copy(collapsedGroups = if (parentId in c) c - parentId else c + parentId)
        }
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
                ticketAttachments = emptyList(),
                attachmentError = null,
                commentText = "",
                editingCommentId = null,
                editCommentText = ""
            )
        }
        loadComments(ticket.id)
        loadAttachments(ticket.id)
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
                ticketAttachments = emptyList(),
                attachmentError = null,
                commentText = "",
                editingCommentId = null,
                editCommentText = ""
            )
        }
    }

    /** Close the ticket panel, asking first if there are unsaved changes. */
    fun requestCloseTicket() {
        if (_uiState.value.isDirty) {
            _uiState.update { it.copy(leavePrompt = LeaveTarget.CloseTicket) }
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
        _uiState.update { it.copy(leavePrompt = LeaveTarget.LeaveWorkspace) }
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
        when (target) {
            LeaveTarget.CloseTicket -> closeTicket()
            LeaveTarget.LeaveWorkspace -> {
                closeTicket()
                _uiState.update { it.copy(exitWorkspace = true) }
            }
            is LeaveTarget.OpenTicket -> _uiState.value.openableById[target.ticketId]?.let { selectTicket(it) }
            LeaveTarget.OpenSplit -> _uiState.update { it.copy(showSplitDialog = true, splitError = null) }
        }
    }

    // Jump to a linked ticket if it's on this board or a split parent (it
    // may be in another project), asking first if there are unsaved changes
    fun openLinkedTicket(ticketId: Int) {
        val ticket = _uiState.value.openableById[ticketId] ?: return
        if (_uiState.value.isDirty) {
            _uiState.update { it.copy(leavePrompt = LeaveTarget.OpenTicket(ticketId)) }
        } else {
            selectTicket(ticket)
        }
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

    // Picking a ticket stages it immediately -- there's no separate Add step
    // to forget (web c5a1c53). Re-picking one marked for removal keeps it.
    fun addDependency(ticketId: Int) = updateDraft { d ->
        when {
            ticketId in d.removeDeps -> d.copy(removeDeps = d.removeDeps - ticketId)
            ticketId in d.addDeps -> d
            else -> d.copy(addDeps = d.addDeps + ticketId)
        }
    }

    // x on a saved dependency marks it for removal; on a pending one, drops
    // it; on one already marked for removal, undoes that
    fun toggleDependency(row: DependencyRow) = updateDraft { d ->
        when (row.pending) {
            "add" -> d.copy(addDeps = d.addDeps - row.id)
            "remove" -> d.copy(removeDeps = d.removeDeps - row.id)
            else -> d.copy(removeDeps = d.removeDeps + row.id)
        }
    }

    fun updateEstimateAmount(amount: String) = updateDraft { it.copy(estimateAmount = amount) }

    fun updateEstimateUnit(unit: String) = updateDraft { it.copy(estimateUnit = unit) }

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
        state.draft?.let { EstimateUnits.validate(it.estimateAmount) }?.let { error ->
            _uiState.update { it.copy(saveError = error) }
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

            for (id in _uiState.value.draft?.removeDeps.orEmpty()) {
                when (val result = projectRepository.removeDependency(ticket.id, id)) {
                    is BreakroomResult.Success -> {
                        replaceTicketEdges(ticket.id, result.data)
                        updateDraft { it.copy(removeDeps = it.removeDeps - id) }
                    }
                    is BreakroomResult.Error -> return failSave(result.message)
                    else -> return failSave("Failed to remove dependency on #$id")
                }
            }
            for (id in _uiState.value.draft?.addDeps.orEmpty()) {
                when (val result = projectRepository.addDependency(ticket.id, id)) {
                    is BreakroomResult.Success -> {
                        replaceTicketEdges(ticket.id, result.data)
                        updateDraft { it.copy(addDeps = it.addDeps - id) }
                    }
                    // e.g. "Adding this dependency would create a loop"
                    is BreakroomResult.Error -> return failSave(result.message)
                    else -> return failSave("Failed to add dependency on #$id")
                }
            }

            for (id in _uiState.value.draft?.removeAttachments.orEmpty()) {
                when (val result = projectRepository.deleteAttachment(id)) {
                    is BreakroomResult.Success -> {
                        _uiState.update { it.copy(ticketAttachments = result.data) }
                        updateDraft { it.copy(removeAttachments = it.removeAttachments - id) }
                    }
                    is BreakroomResult.Error -> return failSave(result.message)
                    else -> return failSave("Failed to remove attachment")
                }
            }
            val files = _uiState.value.draft?.addFiles.orEmpty()
            if (files.isNotEmpty()) {
                when (val result = projectRepository.uploadAttachments(ticket.id, files.map { it.uri })) {
                    is BreakroomResult.Success -> {
                        _uiState.update { it.copy(ticketAttachments = result.data) }
                        updateDraft { it.copy(addFiles = emptyList()) }
                    }
                    is BreakroomResult.Error -> return failSave(result.message)
                    else -> return failSave("Failed to upload attachments")
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

    // Swap in the server's fresh edge set for one ticket
    private fun replaceTicketEdges(ticketId: Int, edges: List<TicketDependency>) {
        _uiState.update { state ->
            state.copy(
                dependencies = state.dependencies.filter {
                    it.ticket_id != ticketId && it.depends_on_ticket_id != ticketId
                } + edges
            )
        }
    }

    // Merge a saved ticket (PUT response) into the panel and the board, and
    // make it the new baseline for the draft -- keeping any dependency
    // changes that are still to be sent
    private fun applySavedTicket(saved: Ticket, statusChanged: Boolean) {
        _uiState.update { state ->
            val tickets = state.tickets.map { if (it.id == saved.id) saved else it }
            state.copy(
                tickets = tickets,
                splitParents = state.splitParents.map { if (it.id == saved.id) saved else it },
                ticketsByStatus = groupTicketsByStatus(tickets),
                selectedTicket = saved,
                draft = TicketDraft.from(saved).copy(
                    addDeps = state.draft?.addDeps.orEmpty(),
                    removeDeps = state.draft?.removeDeps.orEmpty(),
                    addFiles = state.draft?.addFiles.orEmpty(),
                    removeAttachments = state.draft?.removeAttachments.orEmpty()
                ),
                original = TicketDraft.from(saved),
                announcement = if (statusChanged) {
                    AccessibilityAnnouncement(text = "Status changed to ${saved.formattedStatus}")
                } else state.announcement
            )
        }
    }

    // ---- Split into subtasks (migration 086) ----

    fun openSplitDialog() {
        if (!_uiState.value.canSplit) return
        if (_uiState.value.isDirty) {
            _uiState.update { it.copy(leavePrompt = LeaveTarget.OpenSplit) }
        } else {
            _uiState.update { it.copy(showSplitDialog = true, splitError = null) }
        }
    }

    fun hideSplitDialog() {
        _uiState.update { it.copy(showSplitDialog = false, splitError = null) }
    }

    fun splitTicket(mode: String, subtasks: List<SplitSubtask>) {
        val parent = _uiState.value.selectedTicket ?: return
        if (_uiState.value.isSplitting) return
        if (subtasks.any { it.title.isBlank() }) {
            _uiState.update { it.copy(splitError = "Every subtask needs a title") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSplitting = true, splitError = null) }
            when (val result = projectRepository.splitTicket(parent.id, mode, subtasks)) {
                is BreakroomResult.Success -> {
                    _uiState.update { it.copy(isSplitting = false, showSplitDialog = false) }
                    reloadProject()
                    // The parent is off the board now; reopen it to show its subtasks
                    val n = result.data.subtask_ids.size
                    _uiState.value.splitParentsById[parent.id]?.let { selectTicket(it) }
                    _uiState.update {
                        it.copy(
                            successMessage = "Added $n subtask${if (n == 1) "" else "s"}",
                            announcement = AccessibilityAnnouncement(text = "Split into $n subtask${if (n == 1) "" else "s"}")
                        )
                    }
                }
                is BreakroomResult.Error -> _uiState.update { it.copy(isSplitting = false, splitError = result.message) }
                else -> _uiState.update { it.copy(isSplitting = false, splitError = "Session expired - please log in again") }
            }
        }
    }

    // ---- Attachments ----
    // Picked files and removals are staged in the draft like every other edit

    // For image thumbnails, which load straight from the attachment URL
    fun attachmentAuthHeader(): String? = projectRepository.authHeader()

    private fun loadAttachments(ticketId: Int) {
        viewModelScope.launch {
            when (val result = projectRepository.getAttachments(ticketId)) {
                is BreakroomResult.Success -> _uiState.update {
                    if (it.selectedTicket?.id == ticketId) it.copy(ticketAttachments = result.data) else it
                }
                else -> { /* non-fatal */ }
            }
        }
    }

    fun addFiles(files: List<PendingFile>) = updateDraft { it.copy(addFiles = it.addFiles + files) }

    fun dropPendingFile(index: Int) = updateDraft { d ->
        d.copy(addFiles = d.addFiles.filterIndexed { i, _ -> i != index })
    }

    fun markAttachmentForRemoval(a: TicketAttachment) = updateDraft { it.copy(removeAttachments = it.removeAttachments + a.id) }

    fun undoAttachmentRemoval(a: TicketAttachment) = updateDraft { it.copy(removeAttachments = it.removeAttachments - a.id) }

    // Attachments are behind auth, so they're downloaded, then handed to
    // another app by the screen (openedFile)
    fun openAttachment(a: TicketAttachment) {
        if (_uiState.value.isOpeningAttachment) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOpeningAttachment = true, attachmentError = null) }
            when (val result = projectRepository.downloadAttachment(a)) {
                is BreakroomResult.Success -> _uiState.update {
                    it.copy(isOpeningAttachment = false, openedFile = result.data to a.content_type)
                }
                is BreakroomResult.Error -> _uiState.update { it.copy(isOpeningAttachment = false, attachmentError = result.message) }
                else -> _uiState.update { it.copy(isOpeningAttachment = false, attachmentError = "Failed to open attachment") }
            }
        }
    }

    fun onAttachmentOpened(error: String?) {
        _uiState.update { it.copy(openedFile = null, attachmentError = error ?: it.attachmentError) }
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

    // Estimates are an employee/working-member field; the dialog only offers
    // them then (and the backend ignores them otherwise)
    fun createTicket(
        title: String,
        description: String?,
        priority: String,
        estimateAmount: Double? = null,
        estimateUnit: String? = null,
        files: List<PendingFile> = emptyList()
    ) {
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "Title is required") }
            return
        }
        attachmentLimitError(files)?.let { error ->
            _uiState.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingTicket = true, error = null) }
            Log.d(TAG, "createTicket: Creating ticket for project $projectId")
            val estimate = estimateAmount.takeIf { _uiState.value.canWork }
            when (val result = projectRepository.createTicket(
                projectId, title, description, priority,
                estimate, estimateUnit.takeIf { estimate != null }
            )) {
                is BreakroomResult.Success -> {
                    Log.d(TAG, "createTicket: Success - ticket ${result.data.id} created")
                    // The ticket exists now; attach any picked files to it
                    val uploadError = if (files.isEmpty()) null else {
                        when (val upload = projectRepository.uploadAttachments(result.data.id, files.map { it.uri })) {
                            is BreakroomResult.Success -> null
                            is BreakroomResult.Error -> upload.message
                            else -> "Failed to upload attachments"
                        }
                    }
                    _uiState.update {
                        val tickets = it.tickets + result.data
                        it.copy(
                            tickets = tickets,
                            ticketsByStatus = groupTicketsByStatus(tickets),
                            isCreatingTicket = false,
                            showCreateDialog = false,
                            successMessage = if (uploadError == null) "Ticket created" else null
                        )
                    }
                    // Upload failed: open the new ticket so the files can be attached again
                    if (uploadError != null) {
                        selectTicket(result.data)
                        _uiState.update {
                            it.copy(attachmentError = "The ticket was created, but its attachments didn't upload: $uploadError")
                        }
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
