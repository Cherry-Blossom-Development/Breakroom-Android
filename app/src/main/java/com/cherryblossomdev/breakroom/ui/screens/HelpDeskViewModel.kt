package com.cherryblossomdev.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.cherryblossomdev.breakroom.data.HelpDeskRepository
import com.cherryblossomdev.breakroom.data.models.*
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
    val successMessage: String? = null,
    val currentUsername: String = "",
    val ticketComments: List<TicketComment> = emptyList(),
    val commentText: String = "",
    val isPostingComment: Boolean = false,
    val editingCommentId: Int? = null,
    val editCommentText: String = ""
)

class HelpDeskViewModel(
    private val helpDeskRepository: HelpDeskRepository,
    private val companyId: Int = 1  // Default to Cherry Blossom Development
) : ViewModel() {

    private val _uiState = MutableStateFlow(HelpDeskUiState(currentUsername = helpDeskRepository.getUsername()))
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
                else -> { }
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
                else -> { }
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
                else -> { }
            }
        }
    }

    fun selectTicket(ticket: Ticket?) {
        if (ticket == null) {
            _uiState.value = _uiState.value.copy(
                selectedTicket = null,
                ticketComments = emptyList(),
                commentText = "",
                editingCommentId = null,
                editCommentText = ""
            )
        } else {
            _uiState.value = _uiState.value.copy(selectedTicket = ticket)
            loadComments(ticket.id)
        }
    }

    private fun loadComments(ticketId: Int) {
        viewModelScope.launch {
            when (val result = helpDeskRepository.getComments(ticketId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(ticketComments = result.data)
                }
                else -> { /* non-fatal */ }
            }
        }
    }

    fun updateCommentText(text: String) {
        _uiState.value = _uiState.value.copy(commentText = text)
    }

    fun addComment() {
        val ticketId = _uiState.value.selectedTicket?.id ?: return
        val content = _uiState.value.commentText.trim()
        if (content.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPostingComment = true)
            when (val result = helpDeskRepository.addComment(ticketId, content)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        ticketComments = _uiState.value.ticketComments + result.data,
                        commentText = "",
                        isPostingComment = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(isPostingComment = false, error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(isPostingComment = false, error = "Session expired")
                }
                else -> { }
            }
        }
    }

    fun startEditComment(commentId: Int, content: String) {
        _uiState.value = _uiState.value.copy(editingCommentId = commentId, editCommentText = content)
    }

    fun updateEditCommentText(text: String) {
        _uiState.value = _uiState.value.copy(editCommentText = text)
    }

    fun cancelEditComment() {
        _uiState.value = _uiState.value.copy(editingCommentId = null, editCommentText = "")
    }

    fun saveEditComment() {
        val commentId = _uiState.value.editingCommentId ?: return
        val content = _uiState.value.editCommentText.trim()
        if (content.isEmpty()) return

        viewModelScope.launch {
            when (val result = helpDeskRepository.updateComment(commentId, content)) {
                is BreakroomResult.Success -> {
                    val updated = _uiState.value.ticketComments.map { if (it.id == commentId) result.data else it }
                    _uiState.value = _uiState.value.copy(
                        ticketComments = updated,
                        editingCommentId = null,
                        editCommentText = ""
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> { }
            }
        }
    }

    fun deleteComment(commentId: Int) {
        viewModelScope.launch {
            when (helpDeskRepository.deleteComment(commentId)) {
                is BreakroomResult.Success -> {
                    val updated = _uiState.value.ticketComments.map {
                        if (it.id == commentId) it.copy(is_deleted = 1) else it
                    }
                    _uiState.value = _uiState.value.copy(ticketComments = updated)
                }
                is BreakroomResult.Error -> { /* non-fatal */ }
                else -> { }
            }
        }
    }

    fun updateTicket(ticketId: Int, title: String, description: String?, priority: String, status: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true)
            when (val result = helpDeskRepository.updateTicket(ticketId, title, description, priority, status)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedTicket = result.data,
                        isSubmitting = false,
                        successMessage = "Ticket updated"
                    )
                    loadData()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message, isSubmitting = false)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired - please log in again", isSubmitting = false)
                }
                else -> { }
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
