package com.example.breakroom.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.CompanyRepository
import com.example.breakroom.data.models.BreakroomResult
import com.example.breakroom.data.models.Project
import com.example.breakroom.data.models.Ticket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val isLoading: Boolean = false,
    val error: String? = null
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
}
