package com.cherryblossomdev.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.KanbanRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.Ticket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== Redirect ViewModel ====================

sealed class KanbanRedirectState {
    object Loading : KanbanRedirectState()
    data class Ready(val projectId: Int, val projectTitle: String) : KanbanRedirectState()
    data class NoActiveProjects(val companyId: Int) : KanbanRedirectState()
    data class Error(val message: String) : KanbanRedirectState()
}

class KanbanRedirectViewModel(
    private val repository: KanbanRepository
) : ViewModel() {

    private val _state = MutableStateFlow<KanbanRedirectState>(KanbanRedirectState.Loading)
    val state: StateFlow<KanbanRedirectState> = _state.asStateFlow()

    init {
        determineDestination()
    }

    fun retry() = determineDestination()

    private fun determineDestination() {
        _state.value = KanbanRedirectState.Loading
        viewModelScope.launch {
            // Step 1: Get user's companies
            val companiesResult = repository.getMyCompanies()
            if (companiesResult is BreakroomResult.Error) {
                _state.value = KanbanRedirectState.Error(companiesResult.message)
                return@launch
            }
            val companies = (companiesResult as BreakroomResult.Success).data

            if (companies.isNotEmpty()) {
                // Step 2: Get first company's active projects
                val firstCompany = companies[0]
                val projectsResult = repository.getCompanyProjects(firstCompany.id)
                if (projectsResult is BreakroomResult.Error) {
                    _state.value = KanbanRedirectState.Error(projectsResult.message)
                    return@launch
                }
                val projects = (projectsResult as BreakroomResult.Success).data
                val activeProjects = projects.filter { it.isActive }

                if (activeProjects.isNotEmpty()) {
                    // Prefer non-default project, fall back to first active
                    val project = activeProjects.firstOrNull { !it.isDefault } ?: activeProjects[0]
                    _state.value = KanbanRedirectState.Ready(project.id, project.title)
                } else {
                    _state.value = KanbanRedirectState.NoActiveProjects(firstCompany.id)
                }
            } else {
                // Step 3: No companies — create "Personal Workspace"
                val createResult = repository.createCompany(
                    name = "Personal Workspace",
                    description = "My personal project management workspace",
                    employeeTitle = "Owner"
                )
                if (createResult is BreakroomResult.Error) {
                    _state.value = KanbanRedirectState.Error(createResult.message)
                    return@launch
                }
                val newCompany = (createResult as BreakroomResult.Success).data
                val projectsResult = repository.getCompanyProjects(newCompany.id)
                if (projectsResult is BreakroomResult.Error) {
                    _state.value = KanbanRedirectState.Error(projectsResult.message)
                    return@launch
                }
                val projects = (projectsResult as BreakroomResult.Success).data
                if (projects.isNotEmpty()) {
                    val project = projects[0]
                    _state.value = KanbanRedirectState.Ready(project.id, project.title)
                } else {
                    _state.value = KanbanRedirectState.NoActiveProjects(newCompany.id)
                }
            }
        }
    }
}

// ==================== Board ViewModel ====================

data class KanbanBoardUiState(
    val projectTitle: String = "",
    val tickets: List<Ticket> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val showAddTicket: Boolean = false,
    val editingTicket: Ticket? = null,
    // New ticket form state
    val newTicketTitle: String = "",
    val newTicketDescription: String = "",
    val newTicketPriority: String = "medium"
)

class KanbanBoardViewModel(
    private val repository: KanbanRepository,
    private val projectId: Int,
    initialTitle: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(KanbanBoardUiState(projectTitle = initialTitle))
    val uiState: StateFlow<KanbanBoardUiState> = _uiState.asStateFlow()

    init {
        loadBoard()
    }

    fun loadBoard() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val result = repository.getProjectWithTickets(projectId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        projectTitle = result.data.project.title,
                        tickets = result.data.tickets,
                        isLoading = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Unknown error")
                }
            }
        }
    }

    fun moveTicket(ticketId: Int, newStatus: String) {
        val prevTickets = _uiState.value.tickets
        // Optimistic update
        _uiState.value = _uiState.value.copy(
            tickets = prevTickets.map { if (it.id == ticketId) it.copy(status = newStatus) else it }
        )
        viewModelScope.launch {
            when (val result = repository.updateTicket(ticketId, status = newStatus)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        tickets = _uiState.value.tickets.map { if (it.id == result.data.id) result.data else it }
                    )
                }
                is BreakroomResult.Error -> {
                    // Rollback on failure
                    _uiState.value = _uiState.value.copy(tickets = prevTickets, saveError = result.message)
                }
                else -> {}
            }
        }
    }

    fun showAddTicket() { _uiState.value = _uiState.value.copy(showAddTicket = true) }

    fun hideAddTicket() {
        _uiState.value = _uiState.value.copy(
            showAddTicket = false,
            newTicketTitle = "", newTicketDescription = "", newTicketPriority = "medium"
        )
    }

    fun setNewTicketTitle(v: String) { _uiState.value = _uiState.value.copy(newTicketTitle = v) }
    fun setNewTicketDescription(v: String) { _uiState.value = _uiState.value.copy(newTicketDescription = v) }
    fun setNewTicketPriority(v: String) { _uiState.value = _uiState.value.copy(newTicketPriority = v) }

    fun createTicket() {
        val state = _uiState.value
        if (state.newTicketTitle.isBlank() || state.isSaving) return
        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            when (val result = repository.createProjectTicket(
                projectId = projectId,
                title = state.newTicketTitle.trim(),
                description = state.newTicketDescription.trim().ifBlank { null },
                priority = state.newTicketPriority
            )) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        tickets = listOf(result.data) + _uiState.value.tickets,
                        isSaving = false,
                        showAddTicket = false,
                        newTicketTitle = "", newTicketDescription = "", newTicketPriority = "medium"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, saveError = result.message)
                }
                else -> { _uiState.value = _uiState.value.copy(isSaving = false) }
            }
        }
    }

    fun setEditingTicket(ticket: Ticket?) { _uiState.value = _uiState.value.copy(editingTicket = ticket) }

    fun updateTicket(ticketId: Int, title: String, description: String?, priority: String, status: String) {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            when (val result = repository.updateTicket(
                ticketId = ticketId,
                title = title, description = description, priority = priority, status = status
            )) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        tickets = _uiState.value.tickets.map { if (it.id == result.data.id) result.data else it },
                        isSaving = false,
                        editingTicket = null
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, saveError = result.message)
                }
                else -> { _uiState.value = _uiState.value.copy(isSaving = false) }
            }
        }
    }

    fun clearSaveError() { _uiState.value = _uiState.value.copy(saveError = null) }
}
