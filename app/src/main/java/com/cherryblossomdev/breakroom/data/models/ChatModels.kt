package com.cherryblossomdev.breakroom.data.models

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
    val owner_handle: String? = null,
    val discoverable: Int = 0,
    val is_default: Int = 0
) {
    val isActive: Boolean get() = is_active == 1
    val isDiscoverable: Boolean get() = discoverable == 1
    val isDefault: Boolean get() = is_default == 1
}

// Message data class matching backend schema
data class ChatMessage(
    val id: Int,
    val message: String?,
    val image_path: String? = null,
    val video_path: String? = null,
    val created_at: String,
    val user_id: Int,
    val handle: String,
    val is_scheduled: Int = 0
)

// Scheduled message
data class ScheduledMessage(
    val id: Int,
    val user_id: Int,
    val room_id: Int,
    val message_text: String,
    val scheduled_at: String,
    val warning_minutes: Int,
    val indicator_text: String,
    val status: String,
    val is_editing: Int,
    val created_at: String?,
    val updated_at: String?,
    val room_name: String?
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

// Unread room summary (for Chat Summary Widget)
data class ChatUnreadRoom(
    val id: Int,
    val name: String,
    val last_read_at: String?,
    val unread_count: Int
)

// Recent room entry for the all-done view (one message per joined room)
data class ChatRecentRoom(
    val room_id: Int,
    val room_name: String,
    val message: String?,
    val handle: String,
    val created_at: String,
    val unread_count: Int
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
    data class MessageEdited(val roomId: Int, val message: ChatMessage) : SocketEvent()
    data class MessageDeleted(val roomId: Int, val messageId: Int) : SocketEvent()
    data class UserJoined(val roomId: Int, val user: String) : SocketEvent()
    data class UserLeft(val roomId: Int, val user: String) : SocketEvent()
    data class UserTyping(val roomId: Int, val user: String) : SocketEvent()
    data class UserStoppedTyping(val roomId: Int, val user: String) : SocketEvent()
    data class Error(val message: String) : SocketEvent()
    // Badge update events (sent to user-specific socket room)
    data class ChatBadgeUpdate(val roomId: Int) : SocketEvent()
    object FriendBadgeUpdate : SocketEvent()
    data class BlogBadgeUpdate(val postId: Int) : SocketEvent()
    // Scheduled message events (sent to user-specific socket room)
    data class ScheduledMessageWarning(
        val id: Int,
        val roomName: String,
        val messagePreview: String,
        val scheduledAt: String,
        val minutesRemaining: Int
    ) : SocketEvent()
    data class ScheduledMessageMissed(
        val id: Int,
        val messagePreview: String
    ) : SocketEvent()
}

// Scheduled message request/response DTOs
data class CreateScheduledMessageRequest(
    val room_id: Int,
    val message_text: String,
    val scheduled_at: String,
    val warning_minutes: Int = 10,
    val indicator_text: String = "- sent via scheduled message"
)

data class UpdateScheduledMessageRequest(
    val room_id: Int? = null,
    val message_text: String? = null,
    val scheduled_at: String? = null,
    val warning_minutes: Int? = null,
    val indicator_text: String? = null
)

data class ScheduledMessageResponse(
    val scheduled_message: ScheduledMessage
)

data class ScheduledMessagesResponse(
    val scheduled_messages: List<ScheduledMessage>
)

// API Request DTOs
data class SendMessageRequest(
    val message: String
)

data class EditMessageRequest(
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
    val messages: List<ChatMessage>,
    val hasMore: Boolean = false
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
