package com.cherryblossomdev.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.TokenManager
import com.cherryblossomdev.breakroom.data.models.SocketEvent
import com.cherryblossomdev.breakroom.network.BreakroomApiService
import com.cherryblossomdev.breakroom.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BadgeState(
    val chatUnread: Map<Int, Int> = emptyMap(),
    val friendRequestsUnread: Int = 0,
    val blogCommentsUnread: Int = 0,
    val blogUnreadByPost: Map<Int, Int> = emptyMap()
) {
    val totalChatUnread: Int get() = chatUnread.values.sum()
}

class BadgeViewModel(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager,
    private val socketManager: SocketManager
) : ViewModel() {

    private val _state = MutableStateFlow(BadgeState())
    val state: StateFlow<BadgeState> = _state.asStateFlow()

    init {
        collectSocketEvents()
    }

    fun fetchAll() {
        viewModelScope.launch {
            val token = tokenManager.getBearerToken() ?: return@launch
            try {
                val response = apiService.getBadgeCounts(token)
                if (response.isSuccessful) {
                    val body = response.body() ?: return@launch
                    _state.value = BadgeState(
                        chatUnread = body.chatUnread.mapKeys { it.key.toIntOrNull() ?: 0 },
                        friendRequestsUnread = body.friendRequestsUnread,
                        blogCommentsUnread = body.blogCommentsUnread,
                        blogUnreadByPost = body.blogUnreadByPost.mapKeys { it.key.toIntOrNull() ?: 0 }
                    )
                }
            } catch (_: Exception) { }
        }
    }

    fun reset() {
        _state.value = BadgeState()
    }

    fun markRoomRead(roomId: Int) {
        val current = _state.value
        if (!current.chatUnread.containsKey(roomId)) return
        _state.value = current.copy(chatUnread = current.chatUnread - roomId)
        viewModelScope.launch {
            try {
                val token = tokenManager.getBearerToken() ?: return@launch
                apiService.markRoomRead(token, roomId)
            } catch (_: Exception) { }
        }
    }

    fun markAllRoomsRead() {
        if (_state.value.totalChatUnread == 0) return
        _state.value = _state.value.copy(chatUnread = emptyMap())
        viewModelScope.launch {
            try {
                val token = tokenManager.getBearerToken() ?: return@launch
                apiService.markAllRoomsRead(token)
            } catch (_: Exception) { }
        }
    }

    fun markFriendsRead() {
        if (_state.value.friendRequestsUnread == 0) return
        _state.value = _state.value.copy(friendRequestsUnread = 0)
        viewModelScope.launch {
            try {
                val token = tokenManager.getBearerToken() ?: return@launch
                apiService.markFriendsSeen(token)
            } catch (_: Exception) { }
        }
    }

    fun markBlogPostRead(postId: Int) {
        val current = _state.value
        if (!current.blogUnreadByPost.containsKey(postId)) return
        val newByPost = current.blogUnreadByPost - postId
        _state.value = current.copy(
            blogUnreadByPost = newByPost,
            blogCommentsUnread = newByPost.size
        )
        viewModelScope.launch {
            try {
                val token = tokenManager.getBearerToken() ?: return@launch
                apiService.markBlogPostRead(token, postId)
            } catch (_: Exception) { }
        }
    }

    private fun collectSocketEvents() {
        viewModelScope.launch {
            socketManager.events.collect { event ->
                when (event) {
                    is SocketEvent.ChatBadgeUpdate -> {
                        val current = _state.value
                        val newCount = (current.chatUnread[event.roomId] ?: 0) + 1
                        _state.value = current.copy(
                            chatUnread = current.chatUnread + (event.roomId to newCount)
                        )
                    }
                    is SocketEvent.FriendBadgeUpdate -> {
                        _state.value = _state.value.copy(
                            friendRequestsUnread = _state.value.friendRequestsUnread + 1
                        )
                    }
                    is SocketEvent.BlogBadgeUpdate -> {
                        val current = _state.value
                        val wasZero = !current.blogUnreadByPost.containsKey(event.postId)
                        val newCount = (current.blogUnreadByPost[event.postId] ?: 0) + 1
                        val newByPost = current.blogUnreadByPost + (event.postId to newCount)
                        _state.value = current.copy(
                            blogUnreadByPost = newByPost,
                            blogCommentsUnread = if (wasZero) current.blogCommentsUnread + 1
                                                else current.blogCommentsUnread
                        )
                    }
                    else -> { /* not badge-related */ }
                }
            }
        }
    }
}
