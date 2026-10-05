package com.cherryblossomdev.breakroom.ui.screens

import com.cherryblossomdev.breakroom.text.RichText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.cherryblossomdev.breakroom.data.HelpDeskRepository
import com.cherryblossomdev.breakroom.data.ProjectRepository
import com.cherryblossomdev.breakroom.ui.components.PendingFile
import com.cherryblossomdev.breakroom.ui.components.attachmentLimitError
import java.io.File
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
    val editCommentText: String = "",
    val isEmployee: Boolean = false,
    // Attachments (migration 085): here attaching and removing happen right
    // away (explicit actions); on the project board they wait for Save Changes
    val ticketAttachments: List<TicketAttachment> = emptyList(),
    val attachmentsBusy: Boolean = false,
    val attachmentError: String? = null,
    // One-shot: a downloaded attachment for the screen to hand to another app
    val openedFile: Pair<File, String>? = null
) {
    // Mirrors routes/helpdesk.js: employees or the ticket's creator attach;
    // employees or the uploader remove
    val canAttach: Boolean
        get() = selectedTicket != null && (isEmployee || selectedTicket.creator_handle == currentUsername)

    fun canRemoveAttachment(a: TicketAttachment) = isEmployee || a.uploader_handle == currentUsername
}

class HelpDeskViewModel(
    private val helpDeskRepository: HelpDeskRepository,
    private val projectRepository: ProjectRepository,
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
                    _uiState.value = _uiState.value.copy(
                        companyName = companyResult.data.company.name,
                        isEmployee = companyResult.data.isEmployee == true
                    )
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

    fun createTicket(title: String, description: String, priority: String, files: List<PendingFile> = emptyList()) {
        if (title.isBlank()) return
        attachmentLimitError(files)?.let { error ->
            _uiState.value = _uiState.value.copy(error = error)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true)

            when (val result = helpDeskRepository.createTicket(
                companyId = companyId,
                title = title,
                description = RichText.fromPlainText(description).ifBlank { null },
                priority = priority
            )) {
                is BreakroomResult.Success -> {
                    // The ticket exists now; attach any picked files to it
                    val uploadError = if (files.isEmpty()) null else {
                        when (val upload = projectRepository.uploadAttachments(result.data.id, files.map { it.uri })) {
                            is BreakroomResult.Success -> null
                            is BreakroomResult.Error -> upload.message
                            else -> "Failed to upload attachments"
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        showNewTicketDialog = false,
                        successMessage = if (uploadError == null) "Ticket created successfully" else null
                    )
                    loadData()
                    // Upload failed: open the new ticket so the files can be attached again
                    if (uploadError != null) {
                        selectTicket(result.data)
                        _uiState.value = _uiState.value.copy(
                            attachmentError = "The ticket was created, but its attachments didn't upload: $uploadError"
                        )
                    }
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
                editCommentText = "",
                ticketAttachments = emptyList(),
                attachmentError = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                selectedTicket = ticket,
                ticketAttachments = emptyList(),
                attachmentError = null
            )
            loadComments(ticket.id)
            loadAttachments(ticket.id)
        }
    }

    // ---- Attachments ----

    fun attachmentAuthHeader(): String? = projectRepository.authHeader()

    private fun loadAttachments(ticketId: Int) {
        viewModelScope.launch {
            when (val result = projectRepository.getAttachments(ticketId)) {
                is BreakroomResult.Success -> if (_uiState.value.selectedTicket?.id == ticketId) {
                    _uiState.value = _uiState.value.copy(ticketAttachments = result.data)
                }
                else -> { /* non-fatal */ }
            }
        }
    }

    fun attachFiles(files: List<PendingFile>) {
        val ticketId = _uiState.value.selectedTicket?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(attachmentsBusy = true, attachmentError = null)
            _uiState.value = when (val result = projectRepository.uploadAttachments(ticketId, files.map { it.uri })) {
                is BreakroomResult.Success -> _uiState.value.copy(attachmentsBusy = false, ticketAttachments = result.data)
                is BreakroomResult.Error -> _uiState.value.copy(attachmentsBusy = false, attachmentError = result.message)
                else -> _uiState.value.copy(attachmentsBusy = false, attachmentError = "Failed to upload attachments")
            }
        }
    }

    // Confirmed by the screen first
    fun removeAttachment(a: TicketAttachment) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(attachmentsBusy = true, attachmentError = null)
            _uiState.value = when (val result = projectRepository.deleteAttachment(a.id)) {
                is BreakroomResult.Success -> _uiState.value.copy(attachmentsBusy = false, ticketAttachments = result.data)
                is BreakroomResult.Error -> _uiState.value.copy(attachmentsBusy = false, attachmentError = result.message)
                else -> _uiState.value.copy(attachmentsBusy = false, attachmentError = "Failed to remove attachment")
            }
        }
    }

    fun openAttachment(a: TicketAttachment) {
        if (_uiState.value.attachmentsBusy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(attachmentsBusy = true, attachmentError = null)
            _uiState.value = when (val result = projectRepository.downloadAttachment(a)) {
                is BreakroomResult.Success -> _uiState.value.copy(attachmentsBusy = false, openedFile = result.data to a.content_type)
                is BreakroomResult.Error -> _uiState.value.copy(attachmentsBusy = false, attachmentError = result.message)
                else -> _uiState.value.copy(attachmentsBusy = false, attachmentError = "Failed to open attachment")
            }
        }
    }

    fun onAttachmentOpened(error: String?) {
        _uiState.value = _uiState.value.copy(openedFile = null, attachmentError = error ?: _uiState.value.attachmentError)
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
