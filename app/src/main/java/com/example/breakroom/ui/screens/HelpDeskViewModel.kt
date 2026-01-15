package com.example.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.HelpDeskRepository
import com.example.breakroom.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalDate

data class HelpDeskUiState(
    val companyName: String = "",
    val tickets: List<Ticket> = emptyList(),
    val openTickets: List<Ticket> = emptyList(),
    val closedTickets: List<Ticket> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTicket: Ticket? = null,
    val showNewTicketDialog: Boolean = false,
    val isSubmitting: Boolean = false,
    val successMessage: String? = null
)

class HelpDeskViewModel(
    private val helpDeskRepository: HelpDeskRepository,
    private val companyId: Int = 1  // Default to Cherry Blossom Development
) : ViewModel() {

    private val _uiState = MutableStateFlow(HelpDeskUiState())
    val uiState: StateFlow<HelpDeskUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load company info
            when (val companyResult = helpDeskRepository.getCompany(companyId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(companyName = companyResult.data.name)
                }
                is BreakroomResult.Error -> {
                    // Non-fatal, continue loading tickets
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Session expired - please log in again"
                    )
                    return@launch
                }
            }

            // Load tickets
            when (val ticketsResult = helpDeskRepository.getTickets(companyId)) {
                is BreakroomResult.Success -> {
                    val tickets = ticketsResult.data
                    _uiState.value = _uiState.value.copy(
                        tickets = tickets,
                        openTickets = tickets.filter { it.isOpen },
                        closedTickets = tickets.filter { it.isClosed },
                        isLoading = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ticketsResult.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun showNewTicketDialog() {
        _uiState.value = _uiState.value.copy(showNewTicketDialog = true)
    }

    fun hideNewTicketDialog() {
        _uiState.value = _uiState.value.copy(showNewTicketDialog = false)
    }

    fun createTicket(title: String, description: String, priority: String) {
        if (title.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true)

            when (val result = helpDeskRepository.createTicket(
                companyId = companyId,
                title = title,
                description = description.ifBlank { null },
                priority = priority
            )) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        showNewTicketDialog = false,
                        successMessage = "Ticket created successfully"
                    )
                    loadData()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun selectTicket(ticket: Ticket?) {
        _uiState.value = _uiState.value.copy(selectedTicket = ticket)
    }

    fun updateTicketStatus(ticketId: Int, newStatus: String) {
        viewModelScope.launch {
            when (val result = helpDeskRepository.updateTicketStatus(ticketId, newStatus)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedTicket = result.data,
                        successMessage = "Ticket updated"
                    )
                    loadData()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired - please log in again")
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    companion object {
        fun formatDateTime(dateStr: String?): String {
            if (dateStr.isNullOrBlank()) return ""

            return try {
                val date = ZonedDateTime.parse(dateStr)
                date.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
            } catch (e: Exception) {
                try {
                    val date = LocalDate.parse(dateStr.substringBefore("T"))
                    date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                } catch (e2: Exception) {
                    ""
                }
            }
        }
    }
}
