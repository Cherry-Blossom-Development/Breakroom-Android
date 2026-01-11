package com.example.breakroom.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.ChatRepository
import com.example.breakroom.data.models.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// UI State for room list
data class RoomListUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val invites: List<ChatInvite> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val connectionState: SocketConnectionState = SocketConnectionState.DISCONNECTED
)

// UI State for active chat room
data class ChatRoomUiState(
    val room: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(),
    val typingUsers: List<String> = emptyList(),
    val isLoadingMessages: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val error: String? = null
)

// UI State for message input
data class MessageInputState(
    val text: String = "",
    val selectedImageUri: Uri? = null,
    val isSending: Boolean = false
)

// Dialog state
data class DialogState(
    val showCreateRoom: Boolean = false,
    val showRoomOptions: Boolean = false,
    val selectedRoomForOptions: ChatRoom? = null
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val currentUserId: Int
) : ViewModel() {

    // Room list state
    private val _roomListState = MutableStateFlow(RoomListUiState())
    val roomListState: StateFlow<RoomListUiState> = _roomListState.asStateFlow()

    // Current room state
    private val _chatRoomState = MutableStateFlow(ChatRoomUiState())
    val chatRoomState: StateFlow<ChatRoomUiState> = _chatRoomState.asStateFlow()

    // Message input state
    private val _inputState = MutableStateFlow(MessageInputState())
    val inputState: StateFlow<MessageInputState> = _inputState.asStateFlow()

    // Dialog state
    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    // Currently selected room ID
    private var currentRoomId: Int? = null

    // Typing debounce job
    private var typingJob: Job? = null
    private var isTyping = false

    // Message flow collection job
    private var messageCollectionJob: Job? = null

    init {
        chatRepository.setCurrentUserId(currentUserId)

        // Observe connection state
        viewModelScope.launch {
            chatRepository.connectionState.collect { state ->
                _roomListState.value = _roomListState.value.copy(connectionState = state)
            }
        }

        // Observe rooms
        viewModelScope.launch {
            chatRepository.rooms.collect { rooms ->
                _roomListState.value = _roomListState.value.copy(rooms = rooms)
            }
        }

        // Observe invites
        viewModelScope.launch {
            chatRepository.invites.collect { invites ->
                _roomListState.value = _roomListState.value.copy(invites = invites)
            }
        }

        // Connect and load initial data
        connectAndLoad()
    }

    fun connectAndLoad() {
        viewModelScope.launch {
            _roomListState.value = _roomListState.value.copy(isLoading = true, error = null)

            chatRepository.connect()

            when (val result = chatRepository.loadRooms()) {
                is ChatResult.Success -> {
                    _roomListState.value = _roomListState.value.copy(isLoading = false)

                    // Auto-select the General room (owner_id is null)
                    val generalRoom = result.data.find { it.owner_id == null }
                    if (generalRoom != null && currentRoomId == null) {
                        selectRoom(generalRoom)
                    }
                }
                is ChatResult.Error -> {
                    _roomListState.value = _roomListState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }

            // Also load invites
            chatRepository.loadInvites()
        }
    }

    fun selectRoom(room: ChatRoom) {
        // Leave previous room
        currentRoomId?.let { chatRepository.leaveRoom(it) }

        currentRoomId = room.id
        _chatRoomState.value = ChatRoomUiState(room = room, isLoadingMessages = true)
        _inputState.value = MessageInputState()

        // Join new room via socket
        chatRepository.joinRoom(room.id)

        // Cancel previous message collection
        messageCollectionJob?.cancel()

        // Observe messages for this room
        messageCollectionJob = viewModelScope.launch {
            chatRepository.getMessagesFlow(room.id).collect { messages ->
                _chatRoomState.value = _chatRoomState.value.copy(messages = messages)
            }
        }

        // Observe typing users for this room
        viewModelScope.launch {
            chatRepository.typingUsers.collect { typingMap ->
                val roomTypers = typingMap[room.id]?.toList() ?: emptyList()
                _chatRoomState.value = _chatRoomState.value.copy(typingUsers = roomTypers)
            }
        }

        // Load messages
        loadMessages()
    }

    fun leaveRoom() {
        currentRoomId?.let { roomId ->
            chatRepository.leaveRoom(roomId)
            if (isTyping) {
                chatRepository.stopTyping(roomId)
                isTyping = false
            }
        }
        messageCollectionJob?.cancel()
        currentRoomId = null
        _chatRoomState.value = ChatRoomUiState()
        _inputState.value = MessageInputState()
    }

    private fun loadMessages(beforeId: Int? = null) {
        val roomId = currentRoomId ?: return

        viewModelScope.launch {
            if (beforeId == null) {
                _chatRoomState.value = _chatRoomState.value.copy(isLoadingMessages = true)
            } else {
                _chatRoomState.value = _chatRoomState.value.copy(isLoadingMore = true)
            }

            when (val result = chatRepository.loadMessages(roomId, beforeId = beforeId)) {
                is ChatResult.Success -> {
                    _chatRoomState.value = _chatRoomState.value.copy(
                        isLoadingMessages = false,
                        isLoadingMore = false,
                        hasMoreMessages = result.data.size >= 50
                    )
                }
                is ChatResult.Error -> {
                    _chatRoomState.value = _chatRoomState.value.copy(
                        isLoadingMessages = false,
                        isLoadingMore = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun loadMoreMessages() {
        val messages = _chatRoomState.value.messages
        if (messages.isNotEmpty() && _chatRoomState.value.hasMoreMessages && !_chatRoomState.value.isLoadingMore) {
            // Get the oldest message (first in the list since messages are sorted oldest to newest)
            loadMessages(beforeId = messages.first().id)
        }
    }

    // Message input handling
    fun updateMessageText(text: String) {
        _inputState.value = _inputState.value.copy(text = text)

        // Handle typing indicator with debounce
        val roomId = currentRoomId ?: return

        typingJob?.cancel()

        if (text.isNotEmpty() && !isTyping) {
            chatRepository.startTyping(roomId)
            isTyping = true
        }

        typingJob = viewModelScope.launch {
            delay(2000) // Stop typing after 2 seconds of inactivity
            if (isTyping) {
                chatRepository.stopTyping(roomId)
                isTyping = false
            }
        }
    }

    fun setSelectedImage(uri: Uri?) {
        _inputState.value = _inputState.value.copy(selectedImageUri = uri)
    }

    fun sendMessage() {
        val roomId = currentRoomId ?: return
        val text = _inputState.value.text.trim()
        val imageUri = _inputState.value.selectedImageUri

        if (text.isEmpty() && imageUri == null) return

        viewModelScope.launch {
            _inputState.value = _inputState.value.copy(isSending = true)

            // Stop typing indicator
            if (isTyping) {
                chatRepository.stopTyping(roomId)
                isTyping = false
            }
            typingJob?.cancel()

            val result = if (imageUri != null) {
                chatRepository.uploadImage(roomId, imageUri, text.ifEmpty { null })
            } else {
                chatRepository.sendMessage(roomId, text)
            }

            when (result) {
                is ChatResult.Success -> {
                    _inputState.value = MessageInputState() // Clear input
                }
                is ChatResult.Error -> {
                    _chatRoomState.value = _chatRoomState.value.copy(error = result.message)
                    _inputState.value = _inputState.value.copy(isSending = false)
                }
            }
        }
    }

    // Invite handling
    fun acceptInvite(roomId: Int) {
        viewModelScope.launch {
            chatRepository.acceptInvite(roomId)
        }
    }

    fun declineInvite(roomId: Int) {
        viewModelScope.launch {
            chatRepository.declineInvite(roomId)
        }
    }

    // Room creation
    fun showCreateRoomDialog() {
        _dialogState.value = _dialogState.value.copy(showCreateRoom = true)
    }

    fun hideCreateRoomDialog() {
        _dialogState.value = _dialogState.value.copy(showCreateRoom = false)
    }

    fun createRoom(name: String, description: String?) {
        viewModelScope.launch {
            when (val result = chatRepository.createRoom(name, description)) {
                is ChatResult.Success -> {
                    hideCreateRoomDialog()
                    selectRoom(result.data)
                }
                is ChatResult.Error -> {
                    _roomListState.value = _roomListState.value.copy(error = result.message)
                }
            }
        }
    }

    // Room options (edit/delete)
    fun showRoomOptions(room: ChatRoom) {
        _dialogState.value = _dialogState.value.copy(
            showRoomOptions = true,
            selectedRoomForOptions = room
        )
    }

    fun hideRoomOptions() {
        _dialogState.value = _dialogState.value.copy(
            showRoomOptions = false,
            selectedRoomForOptions = null
        )
    }

    fun updateRoom(roomId: Int, name: String, description: String?) {
        viewModelScope.launch {
            when (val result = chatRepository.updateRoom(roomId, name, description)) {
                is ChatResult.Success -> {
                    hideRoomOptions()
                    // Update current room state if it's the one being edited
                    if (_chatRoomState.value.room?.id == roomId) {
                        _chatRoomState.value = _chatRoomState.value.copy(room = result.data)
                    }
                }
                is ChatResult.Error -> {
                    _roomListState.value = _roomListState.value.copy(error = result.message)
                }
            }
        }
    }

    fun deleteRoom(roomId: Int) {
        viewModelScope.launch {
            when (val result = chatRepository.deleteRoom(roomId)) {
                is ChatResult.Success -> {
                    hideRoomOptions()
                    // If we deleted the current room, go back to room list
                    if (currentRoomId == roomId) {
                        leaveRoom()
                    }
                }
                is ChatResult.Error -> {
                    _roomListState.value = _roomListState.value.copy(error = result.message)
                }
            }
        }
    }

    // Check if current user owns a room
    fun isRoomOwner(room: ChatRoom): Boolean {
        return room.owner_id == currentUserId
    }

    fun clearError() {
        _roomListState.value = _roomListState.value.copy(error = null)
        _chatRoomState.value = _chatRoomState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        currentRoomId?.let { chatRepository.leaveRoom(it) }
        chatRepository.disconnect()
    }
}
