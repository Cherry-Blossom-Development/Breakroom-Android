package com.example.breakroom.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.CompanyRepository
import com.example.breakroom.data.models.BreakroomResult
import com.example.breakroom.data.models.CompanyEmployee
import com.example.breakroom.data.models.Project
import com.example.breakroom.data.models.Ticket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Valid status transitions matching web version
object StatusTransitions {
    private val transitions = mapOf(
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

        val allStatuses = listOf(BACKLOG, ON_DECK, IN_PROGRESS, RESOLVED, CLOSED)
    }
}

data class ProjectTicketsUiState(
    val project: Project? = null,
    val tickets: List<Ticket> = emptyList(),
    val ticketsByStatus: Map<KanbanStatus, List<Ticket>> = emptyMap(),
    val currentStatusIndex: Int = 0,
    val selectedTicket: Ticket? = null,
    val employees: List<CompanyEmployee> = emptyList(),
    val isLoading: Boolean = false,
    val isUpdatingTicket: Boolean = false,
    val isCreatingTicket: Boolean = false,
    val showCreateDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class ProjectTicketsViewModel(
    private val companyRepository: CompanyRepository,
    private val projectId: Int
) : ViewModel() {

    companion object {
        private const val TAG = "ProjectTicketsVM"
    }

    private val _uiState = MutableStateFlow(ProjectTicketsUiState())
    val uiState: StateFlow<ProjectTicketsUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "ViewModel created for project $projectId")
        loadProjectTickets()
    }

    fun loadProjectTickets() {
        Log.d(TAG, "loadProjectTickets: Starting load for project $projectId")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = companyRepository.getProjectWithTickets(projectId)) {
                is BreakroomResult.Success -> {
                    val tickets = result.data.tickets
                    val ticketsByStatus = groupTicketsByStatus(tickets)
                    Log.d(TAG, "loadProjectTickets: Success - got ${tickets.size} tickets")
                    _uiState.value = _uiState.value.copy(
                        project = result.data.project,
                        tickets = tickets,
                        ticketsByStatus = ticketsByStatus,
                        isLoading = false
                    )
                }
                is BreakroomResult.Error -> {
                    Log.e(TAG, "loadProjectTickets: Error - ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    Log.e(TAG, "loadProjectTickets: Auth error")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    private fun groupTicketsByStatus(tickets: List<Ticket>): Map<KanbanStatus, List<Ticket>> {
        val grouped = mutableMapOf<KanbanStatus, List<Ticket>>()
        KanbanStatus.allStatuses.forEach { status ->
            grouped[status] = tickets.filter {
                KanbanStatus.fromApiValue(it.status) == status
            }
        }
        return grouped
    }

    fun setCurrentStatusIndex(index: Int) {
        if (index in 0 until KanbanStatus.allStatuses.size) {
            _uiState.value = _uiState.value.copy(currentStatusIndex = index)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    fun selectTicket(ticket: Ticket) {
        Log.d(TAG, "selectTicket: Selected ticket ${ticket.id}")
        _uiState.value = _uiState.value.copy(selectedTicket = ticket)
        loadEmployees()
    }

    fun clearSelectedTicket() {
        _uiState.value = _uiState.value.copy(selectedTicket = null)
    }

    private fun loadEmployees() {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            Log.d(TAG, "loadEmployees: Loading for company ${project.company_id}")
            when (val result = companyRepository.getCompanyEmployees(project.company_id)) {
                is BreakroomResult.Success -> {
                    val activeEmployees = result.data.filter { it.status == "active" }
                    Log.d(TAG, "loadEmployees: Got ${activeEmployees.size} active employees")
                    _uiState.value = _uiState.value.copy(employees = activeEmployees)
                }
                is BreakroomResult.Error -> {
                    Log.e(TAG, "loadEmployees: Error - ${result.message}")
                }
                is BreakroomResult.AuthenticationError -> {
                    Log.e(TAG, "loadEmployees: Auth error")
                }
            }
        }
    }

    fun updateTicketStatus(newStatus: String) {
        val ticket = _uiState.value.selectedTicket ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdatingTicket = true, error = null)
            Log.d(TAG, "updateTicketStatus: Changing ticket ${ticket.id} to $newStatus")
            when (val result = companyRepository.updateTicketStatus(ticket.id, newStatus)) {
                is BreakroomResult.Success -> {
                    Log.d(TAG, "updateTicketStatus: Success")
                    val updatedTicket = result.data
                    // Update in tickets list and ticketsByStatus
                    val updatedTickets = _uiState.value.tickets.map {
                        if (it.id == ticket.id) updatedTicket else it
                    }
                    _uiState.value = _uiState.value.copy(
                        tickets = updatedTickets,
                        ticketsByStatus = groupTicketsByStatus(updatedTickets),
                        selectedTicket = updatedTicket,
                        isUpdatingTicket = false,
                        successMessage = "Status updated"
                    )
                }
                is BreakroomResult.Error -> {
                    Log.e(TAG, "updateTicketStatus: Error - ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdatingTicket = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdatingTicket = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun assignTicket(assigneeId: Int?) {
        val ticket = _uiState.value.selectedTicket ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdatingTicket = true, error = null)
            Log.d(TAG, "assignTicket: Assigning ticket ${ticket.id} to $assigneeId")
            when (val result = companyRepository.assignTicket(ticket.id, assigneeId)) {
                is BreakroomResult.Success -> {
                    Log.d(TAG, "assignTicket: Success")
                    val updatedTicket = result.data
                    // Update in tickets list
                    val updatedTickets = _uiState.value.tickets.map {
                        if (it.id == ticket.id) updatedTicket else it
                    }
                    val assigneeName = if (assigneeId == null) "Unassigned" else updatedTicket.assigneeName ?: "Unknown"
                    _uiState.value = _uiState.value.copy(
                        tickets = updatedTickets,
                        ticketsByStatus = groupTicketsByStatus(updatedTickets),
                        selectedTicket = updatedTicket,
                        isUpdatingTicket = false,
                        successMessage = "Assigned to $assigneeName"
                    )
                }
                is BreakroomResult.Error -> {
                    Log.e(TAG, "assignTicket: Error - ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdatingTicket = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdatingTicket = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun createTicket(title: String, description: String?, priority: String) {
        if (title.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Title is required")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingTicket = true, error = null)
            Log.d(TAG, "createTicket: Creating ticket for project $projectId")
            when (val result = companyRepository.createProjectTicket(projectId, title, description, priority)) {
                is BreakroomResult.Success -> {
                    Log.d(TAG, "createTicket: Success - ticket ${result.data.id} created")
                    val newTicket = result.data
                    val updatedTickets = _uiState.value.tickets + newTicket
                    _uiState.value = _uiState.value.copy(
                        tickets = updatedTickets,
                        ticketsByStatus = groupTicketsByStatus(updatedTickets),
                        isCreatingTicket = false,
                        showCreateDialog = false,
                        successMessage = "Ticket created"
                    )
                }
                is BreakroomResult.Error -> {
                    Log.e(TAG, "createTicket: Error - ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isCreatingTicket = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isCreatingTicket = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }
}
