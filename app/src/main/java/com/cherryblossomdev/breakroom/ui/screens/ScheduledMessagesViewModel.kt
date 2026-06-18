package com.cherryblossomdev.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.ScheduledMessagesRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.ChatRoom
import com.cherryblossomdev.breakroom.data.models.ScheduledMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ScheduledMessagesUiState(
    val messages: List<ScheduledMessage> = emptyList(),
    val rooms: List<ChatRoom> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,

    // Form state
    val editingId: Int? = null,
    val formRoomId: Int? = null,
    val formMessageText: String = "",
    val formScheduledDate: Calendar = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) },
    val formWarningMinutes: Int = 10,
    val formIndicatorText: String = "- sent via scheduled message",
    val isSubmitting: Boolean = false
)

class ScheduledMessagesViewModel(
    private val repository: ScheduledMessagesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduledMessagesUiState())
    val state: StateFlow<ScheduledMessagesUiState> = _state

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val roomsResult = repository.getRooms()
            val messagesResult = repository.getScheduledMessages()
            _state.value = _state.value.copy(
                isLoading = false,
                rooms = if (roomsResult is BreakroomResult.Success) roomsResult.data else emptyList(),
                messages = if (messagesResult is BreakroomResult.Success) messagesResult.data else emptyList(),
                error = when {
                    roomsResult is BreakroomResult.Error -> roomsResult.message
                    messagesResult is BreakroomResult.Error -> messagesResult.message
                    else -> null
                }
            )
        }
    }

    fun updateFormRoom(roomId: Int) {
        _state.value = _state.value.copy(formRoomId = roomId)
    }

    fun updateFormText(text: String) {
        if (text.length <= 1000) _state.value = _state.value.copy(formMessageText = text)
    }

    fun updateFormDate(year: Int, month: Int, day: Int) {
        val cal = _state.value.formScheduledDate.clone() as Calendar
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, day)
        _state.value = _state.value.copy(formScheduledDate = cal)
    }

    fun updateFormTime(hour: Int, minute: Int) {
        val cal = _state.value.formScheduledDate.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        _state.value = _state.value.copy(formScheduledDate = cal)
    }

    fun updateFormWarningMinutes(minutes: Int) {
        _state.value = _state.value.copy(formWarningMinutes = minutes.coerceIn(0, 60))
    }

    fun updateFormIndicatorText(text: String) {
        _state.value = _state.value.copy(formIndicatorText = text)
    }

    fun setDefaultIndicator() {
        _state.value = _state.value.copy(formIndicatorText = "- sent via scheduled message")
    }

    fun setNoIndicator() {
        _state.value = _state.value.copy(formIndicatorText = "")
    }

    fun startEditing(message: ScheduledMessage) {
        val cal = Calendar.getInstance()
        try {
            val date = dateFormat.parse(message.scheduled_at)
            if (date != null) cal.time = date
        } catch (_: Exception) {}
        _state.value = _state.value.copy(
            editingId = message.id,
            formRoomId = message.room_id,
            formMessageText = message.message_text,
            formScheduledDate = cal,
            formWarningMinutes = message.warning_minutes,
            formIndicatorText = message.indicator_text
        )
    }

    fun cancelEditing() {
        _state.value = _state.value.copy(
            editingId = null,
            formRoomId = null,
            formMessageText = "",
            formScheduledDate = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) },
            formWarningMinutes = 10,
            formIndicatorText = "- sent via scheduled message"
        )
    }

    fun submitForm() {
        val s = _state.value
        val roomId = s.formRoomId ?: run {
            _state.value = s.copy(error = "Please select a room")
            return
        }
        if (s.formMessageText.isBlank()) {
            _state.value = s.copy(error = "Message cannot be empty")
            return
        }
        val scheduledAt = dateFormat.format(s.formScheduledDate.time)

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            val result = if (s.editingId != null) {
                repository.updateScheduledMessage(
                    s.editingId, roomId, s.formMessageText, scheduledAt,
                    s.formWarningMinutes, s.formIndicatorText
                )
            } else {
                repository.createScheduledMessage(
                    roomId, s.formMessageText, scheduledAt,
                    s.formWarningMinutes, s.formIndicatorText
                )
            }
            when (result) {
                is BreakroomResult.Success -> {
                    val updated = _state.value.messages.toMutableList()
                    if (_state.value.editingId != null) {
                        val idx = updated.indexOfFirst { it.id == result.data.id }
                        if (idx >= 0) updated[idx] = result.data else updated.add(0, result.data)
                    } else {
                        updated.add(0, result.data)
                    }
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        messages = updated,
                        editingId = null,
                        formRoomId = null,
                        formMessageText = "",
                        formScheduledDate = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) },
                        formWarningMinutes = 10,
                        formIndicatorText = "- sent via scheduled message",
                        successMessage = if (_state.value.editingId != null) "Updated" else "Scheduled"
                    )
                }
                is BreakroomResult.Error -> {
                    _state.value = _state.value.copy(isSubmitting = false, error = result.message)
                }
                else -> {
                    _state.value = _state.value.copy(isSubmitting = false, error = "Unexpected error")
                }
            }
        }
    }

    fun cancelMessage(id: Int) {
        viewModelScope.launch {
            when (repository.cancelScheduledMessage(id)) {
                is BreakroomResult.Success -> {
                    _state.value = _state.value.copy(
                        messages = _state.value.messages.filter { it.id != id }
                    )
                }
                else -> {
                    _state.value = _state.value.copy(error = "Failed to cancel")
                }
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun clearSuccess() { _state.value = _state.value.copy(successMessage = null) }
}
