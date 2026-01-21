package com.example.breakroom.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.CompanyRepository
import com.example.breakroom.data.models.BreakroomResult
import com.example.breakroom.data.models.CompanyEmployee
import com.example.breakroom.data.models.Ticket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TicketDetailUiState(
    val ticket: Ticket? = null,
    val employees: List<CompanyEmployee> = emptyList(),
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val isEditing: Boolean = false,
    val editTitle: String = "",
    val editDescription: String = "",
    val editPriority: String = "medium",
    val error: String? = null,
    val successMessage: String? = null
)

class TicketDetailViewModel(
    private val companyRepository: CompanyRepository,
    private val initialTicket: Ticket,
    private val companyId: Int
) : ViewModel() {

    companion object {
        private const val TAG = "TicketDetailVM"
    }

    private val _uiState = MutableStateFlow(TicketDetailUiState(ticket = initialTicket))
    val uiState: StateFlow<TicketDetailUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "ViewModel created for ticket ${initialTicket.id}")
        loadEmployees()
    }

    private fun loadEmployees() {
        viewModelScope.launch {
            Log.d(TAG, "loadEmployees: Loading employees for company $companyId")
            when (val result = companyRepository.getCompanyEmployees(companyId)) {
                is BreakroomResult.Success -> {
                    // Filter to only active employees
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

    fun updateStatus(newStatus: String) {
        val ticket = _uiState.value.ticket ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, error = null)
            Log.d(TAG, "updateStatus: Changing ticket ${ticket.id} from ${ticket.status} to $newStatus")
            when (val result = companyRepository.updateTicketStatus(ticket.id, newStatus)) {
                is BreakroomResult.Success -> {
                    Log.d(TAG, "updateStatus: Success")
                    _uiState.value = _uiState.value.copy(
                        ticket = result.data,
                        isUpdating = false,
                        successMessage = "Status updated to ${result.data.formattedStatus}"
                    )
                }
                is BreakroomResult.Error -> {
                    Log.e(TAG, "updateStatus: Error - ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun assignTicket(assigneeId: Int?) {
        val ticket = _uiState.value.ticket ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, error = null)
            Log.d(TAG, "assignTicket: Assigning ticket ${ticket.id} to user $assigneeId")
            when (val result = companyRepository.assignTicket(ticket.id, assigneeId)) {
                is BreakroomResult.Success -> {
                    Log.d(TAG, "assignTicket: Success")
                    val assigneeName = if (assigneeId == null) "Unassigned" else result.data.assigneeName ?: "Unknown"
                    _uiState.value = _uiState.value.copy(
                        ticket = result.data,
                        isUpdating = false,
                        successMessage = "Assigned to $assigneeName"
                    )
                }
                is BreakroomResult.Error -> {
                    Log.e(TAG, "assignTicket: Error - ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    fun startEditing() {
        val ticket = _uiState.value.ticket ?: return
        _uiState.value = _uiState.value.copy(
            isEditing = true,
            editTitle = ticket.title,
            editDescription = ticket.description ?: "",
            editPriority = ticket.priority
        )
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(isEditing = false)
    }

    fun updateEditTitle(title: String) {
        _uiState.value = _uiState.value.copy(editTitle = title)
    }

    fun updateEditDescription(description: String) {
        _uiState.value = _uiState.value.copy(editDescription = description)
    }

    fun updateEditPriority(priority: String) {
        _uiState.value = _uiState.value.copy(editPriority = priority)
    }

    fun saveTicket() {
        val ticket = _uiState.value.ticket ?: return
        val state = _uiState.value

        if (state.editTitle.isBlank()) {
            _uiState.value = state.copy(error = "Title is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, error = null)
            Log.d(TAG, "saveTicket: Saving ticket ${ticket.id}")

            when (val result = companyRepository.updateTicket(
                ticketId = ticket.id,
                title = state.editTitle.trim(),
                description = state.editDescription.trim().ifBlank { null },
                priority = state.editPriority
            )) {
                is BreakroomResult.Success -> {
                    Log.d(TAG, "saveTicket: Success")
                    _uiState.value = _uiState.value.copy(
                        ticket = result.data,
                        isUpdating = false,
                        isEditing = false,
                        successMessage = "Ticket updated successfully"
                    )
                }
                is BreakroomResult.Error -> {
                    Log.e(TAG, "saveTicket: Error - ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }
}
