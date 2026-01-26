package com.example.breakroom.data.models

import java.util.Date

// Sealed class for chat operation results (matching AuthResult pattern)
sealed class ChatResult<out T> {
    data class Success<T>(val data: T) : ChatResult<T>()
    data class Error(val message: String) : ChatResult<Nothing>()
}

// Room data class matching backend schema
data class ChatRoom(
    val id: Int,
    val name: String,
    val description: String? = null,
    val is_active: Int = 1,  // Database uses 1/0 for boolean
    val created_at: String? = null,
    val owner_id: Int? = null,
    val owner_handle: String? = null
) {
    val isActive: Boolean get() = is_active == 1
}

// Message data class matching backend schema
data class ChatMessage(
    val id: Int,
    val message: String?,
    val image_path: String? = null,
    val video_path: String? = null,
    val created_at: String,
    val user_id: Int,
    val handle: String
)

// Invite data class
data class ChatInvite(
    val room_id: Int,
    val room_name: String,
    val room_description: String? = null,
    val invited_by_handle: String? = null,
    val invited_at: String
)

// Room member
data class RoomMember(
    val id: Int,
    val handle: String,
    val role: String? = null,
    val joined_at: String? = null
)

// Typing indicator
data class TypingUser(
    val roomId: Int,
    val user: String
)

// Socket connection state
enum class SocketConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

// Socket events sealed class
sealed class SocketEvent {
    data class NewMessage(val roomId: Int, val message: ChatMessage) : SocketEvent()
    data class UserJoined(val roomId: Int, val user: String) : SocketEvent()
    data class UserLeft(val roomId: Int, val user: String) : SocketEvent()
    data class UserTyping(val roomId: Int, val user: String) : SocketEvent()
    data class UserStoppedTyping(val roomId: Int, val user: String) : SocketEvent()
    data class Error(val message: String) : SocketEvent()
}

// API Request DTOs
data class SendMessageRequest(
    val message: String
)

data class CreateRoomRequest(
    val name: String,
    val description: String? = null
)

data class UpdateRoomRequest(
    val name: String,
    val description: String? = null
)

data class InviteUserRequest(
    val userId: Int
)

// API Response wrappers
data class RoomsResponse(
    val rooms: List<ChatRoom>
)

data class MessagesResponse(
    val messages: List<ChatMessage>
)

data class MessageResponse(
    val message: ChatMessage
)

data class RoomResponse(
    val room: ChatRoom
)

data class InvitesResponse(
    val invites: List<ChatInvite>
)

data class MembersResponse(
    val members: List<RoomMember>
)
