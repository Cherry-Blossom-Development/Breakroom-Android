package com.example.breakroom.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.breakroom.data.models.*
import com.example.breakroom.network.ChatApiService
import com.example.breakroom.network.ErrorResponse
import com.example.breakroom.network.SocketManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ChatRepository(
    private val chatApiService: ChatApiService,
    private val socketManager: SocketManager,
    private val tokenManager: TokenManager,
    private val context: Context
) {
    companion object {
        private const val TAG = "ChatRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cached rooms
    private val _rooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val rooms: StateFlow<List<ChatRoom>> = _rooms.asStateFlow()

    // Cached messages per room
    private val _messagesByRoom = mutableMapOf<Int, MutableStateFlow<List<ChatMessage>>>()

    // Typing users per room
    private val _typingUsers = MutableStateFlow<Map<Int, Set<String>>>(emptyMap())
    val typingUsers: StateFlow<Map<Int, Set<String>>> = _typingUsers.asStateFlow()

    // Pending invites
    private val _invites = MutableStateFlow<List<ChatInvite>>(emptyList())
    val invites: StateFlow<List<ChatInvite>> = _invites.asStateFlow()

    // Socket connection state passthrough
    val connectionState = socketManager.connectionState

    // Current user ID for determining message ownership
    private var currentUserId: Int? = null

    init {
        observeSocketEvents()
    }

    private fun observeSocketEvents() {
        scope.launch {
            socketManager.events.collect { event ->
                when (event) {
                    is SocketEvent.NewMessage -> handleNewMessage(event.roomId, event.message)
                    is SocketEvent.UserTyping -> handleUserTyping(event.roomId, event.user, true)
                    is SocketEvent.UserStoppedTyping -> handleUserTyping(event.roomId, event.user, false)
                    is SocketEvent.UserJoined -> Log.d(TAG, "${event.user} joined room ${event.roomId}")
                    is SocketEvent.UserLeft -> Log.d(TAG, "${event.user} left room ${event.roomId}")
                    is SocketEvent.Error -> Log.e(TAG, "Socket error: ${event.message}")
                }
            }
        }
    }

    private fun handleNewMessage(roomId: Int, message: ChatMessage) {
        val roomMessages = _messagesByRoom.getOrPut(roomId) { MutableStateFlow(emptyList()) }
        // Prevent duplicates
        if (roomMessages.value.none { it.id == message.id }) {
            roomMessages.value = roomMessages.value + message
        }
    }

    private fun handleUserTyping(roomId: Int, user: String, isTyping: Boolean) {
        val currentMap = _typingUsers.value.toMutableMap()
        val roomTypers = currentMap.getOrDefault(roomId, emptySet()).toMutableSet()

        if (isTyping) {
            roomTypers.add(user)
        } else {
            roomTypers.remove(user)
        }

        currentMap[roomId] = roomTypers
        _typingUsers.value = currentMap
    }

    // Connection management
    fun connect() = socketManager.connect()
    fun disconnect() = socketManager.disconnect()
    fun isConnected() = socketManager.isConnected()

    // Set current user ID (call after login)
    fun setCurrentUserId(userId: Int) {
        currentUserId = userId
    }

    // Room operations
    suspend fun loadRooms(): ChatResult<List<ChatRoom>> {
        val bearerToken = tokenManager.getBearerToken()
        Log.d(TAG, "loadRooms: bearerToken = ${bearerToken?.take(20)}...")

        if (bearerToken == null) {
            Log.e(TAG, "loadRooms: Not logged in - no bearer token")
            return ChatResult.Error("Not logged in")
        }

        return try {
            Log.d(TAG, "loadRooms: Calling API...")
            val response = chatApiService.getRooms(bearerToken)
            Log.d(TAG, "loadRooms: Response code = ${response.code()}")

            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "loadRooms: Response body = $body")
                val roomList = body?.rooms ?: emptyList()
                Log.d(TAG, "loadRooms: Found ${roomList.size} rooms")
                roomList.forEach { room ->
                    Log.d(TAG, "loadRooms: Room - id=${room.id}, name=${room.name}, owner_id=${room.owner_id}")
                }
                _rooms.value = roomList
                ChatResult.Success(roomList)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "loadRooms: Error response = $errorBody")
                parseError(errorBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading rooms", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun createRoom(name: String, description: String?): ChatResult<ChatRoom> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            val response = chatApiService.createRoom(
                bearerToken,
                CreateRoomRequest(name, description)
            )
            if (response.isSuccessful) {
                val room = response.body()?.room!!
                _rooms.value = _rooms.value + room
                ChatResult.Success(room)
            } else {
                parseError(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating room", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun updateRoom(roomId: Int, name: String, description: String?): ChatResult<ChatRoom> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            val response = chatApiService.updateRoom(
                bearerToken,
                roomId,
                UpdateRoomRequest(name, description)
            )
            if (response.isSuccessful) {
                val room = response.body()?.room!!
                _rooms.value = _rooms.value.map { if (it.id == roomId) room else it }
                ChatResult.Success(room)
            } else {
                parseError(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating room", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun deleteRoom(roomId: Int): ChatResult<Unit> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            val response = chatApiService.deleteRoom(bearerToken, roomId)
            if (response.isSuccessful) {
                _rooms.value = _rooms.value.filter { it.id != roomId }
                ChatResult.Success(Unit)
            } else {
                parseError(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting room", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    fun joinRoom(roomId: Int) {
        socketManager.joinRoom(roomId)
    }

    fun leaveRoom(roomId: Int) {
        socketManager.leaveRoom(roomId)
        // Clear typing users for this room
        val currentMap = _typingUsers.value.toMutableMap()
        currentMap.remove(roomId)
        _typingUsers.value = currentMap
    }

    // Message operations
    fun getMessagesFlow(roomId: Int): StateFlow<List<ChatMessage>> {
        return _messagesByRoom.getOrPut(roomId) { MutableStateFlow(emptyList()) }
    }

    suspend fun loadMessages(
        roomId: Int,
        limit: Int = 50,
        beforeId: Int? = null
    ): ChatResult<List<ChatMessage>> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            val response = chatApiService.getMessages(bearerToken, roomId, limit, beforeId)
            if (response.isSuccessful) {
                val messages = response.body()?.messages ?: emptyList()

                val roomMessages = _messagesByRoom.getOrPut(roomId) { MutableStateFlow(emptyList()) }
                if (beforeId == null) {
                    // Initial load - replace messages
                    roomMessages.value = messages
                } else {
                    // Pagination - prepend older messages (avoid duplicates)
                    val existingIds = roomMessages.value.map { it.id }.toSet()
                    val newMessages = messages.filter { it.id !in existingIds }
                    roomMessages.value = newMessages + roomMessages.value
                }

                ChatResult.Success(messages)
            } else {
                parseError(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun sendMessage(roomId: Int, content: String): ChatResult<ChatMessage> {
        // Try socket first
        if (socketManager.sendMessage(roomId, content)) {
            Log.d(TAG, "Message sent via socket")
            // Return optimistic message - real one will arrive via socket event
            val optimisticMessage = ChatMessage(
                id = -System.currentTimeMillis().toInt(), // Temporary negative ID
                message = content,
                image_path = null,
                created_at = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    .format(java.util.Date()),
                user_id = currentUserId ?: 0,
                handle = tokenManager.getUsername() ?: ""
            )
            return ChatResult.Success(optimisticMessage)
        }

        // Fallback to REST
        Log.d(TAG, "Sending message via REST (socket not connected)")
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            val response = chatApiService.sendMessage(
                bearerToken,
                roomId,
                SendMessageRequest(content)
            )
            if (response.isSuccessful) {
                val message = response.body()?.message!!
                handleNewMessage(roomId, message)
                ChatResult.Success(message)
            } else {
                parseError(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun uploadImage(roomId: Int, imageUri: Uri, message: String?): ChatResult<ChatMessage> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            Log.d(TAG, "uploadImage: Starting upload for URI: $imageUri")

            val inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e(TAG, "uploadImage: Could not open input stream for URI")
                return ChatResult.Error("Could not read image")
            }

            val file = File.createTempFile("upload", ".jpg", context.cacheDir)
            file.outputStream().use { outputStream ->
                val bytesCopied = inputStream.copyTo(outputStream)
                Log.d(TAG, "uploadImage: Copied $bytesCopied bytes to temp file")
            }
            inputStream.close()

            Log.d(TAG, "uploadImage: Temp file size: ${file.length()} bytes")

            if (file.length() == 0L) {
                Log.e(TAG, "uploadImage: Temp file is empty!")
                file.delete()
                return ChatResult.Error("Image file is empty")
            }

            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", file.name, requestFile)
            val messagePart = message?.toRequestBody("text/plain".toMediaTypeOrNull())

            Log.d(TAG, "uploadImage: Sending request to server...")
            val response = chatApiService.uploadImage(bearerToken, roomId, imagePart, messagePart)
            file.delete()

            Log.d(TAG, "uploadImage: Response code: ${response.code()}")
            if (response.isSuccessful) {
                val chatMessage = response.body()?.message!!
                handleNewMessage(roomId, chatMessage)
                ChatResult.Success(chatMessage)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "uploadImage: Error response: $errorBody")
                parseError(errorBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun uploadVideo(roomId: Int, videoUri: Uri, message: String?): ChatResult<ChatMessage> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            Log.d(TAG, "uploadVideo: Starting upload for URI: $videoUri")

            val inputStream = context.contentResolver.openInputStream(videoUri)
            if (inputStream == null) {
                Log.e(TAG, "uploadVideo: Could not open input stream for URI")
                return ChatResult.Error("Could not read video")
            }

            val file = File.createTempFile("upload", ".mp4", context.cacheDir)
            file.outputStream().use { outputStream ->
                val bytesCopied = inputStream.copyTo(outputStream)
                Log.d(TAG, "uploadVideo: Copied $bytesCopied bytes to temp file")
            }
            inputStream.close()

            Log.d(TAG, "uploadVideo: Temp file size: ${file.length()} bytes")

            if (file.length() == 0L) {
                Log.e(TAG, "uploadVideo: Temp file is empty!")
                file.delete()
                return ChatResult.Error("Video file is empty")
            }

            val requestFile = file.asRequestBody("video/mp4".toMediaTypeOrNull())
            val videoPart = MultipartBody.Part.createFormData("video", file.name, requestFile)
            val messagePart = message?.toRequestBody("text/plain".toMediaTypeOrNull())

            Log.d(TAG, "uploadVideo: Sending request to server...")
            val response = chatApiService.uploadVideo(bearerToken, roomId, videoPart, messagePart)
            file.delete()

            Log.d(TAG, "uploadVideo: Response code: ${response.code()}")
            if (response.isSuccessful) {
                val chatMessage = response.body()?.message!!
                handleNewMessage(roomId, chatMessage)
                ChatResult.Success(chatMessage)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "uploadVideo: Error response: $errorBody")
                parseError(errorBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading video", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    // Typing indicators
    fun startTyping(roomId: Int) = socketManager.startTyping(roomId)
    fun stopTyping(roomId: Int) = socketManager.stopTyping(roomId)

    // Invite operations
    suspend fun loadInvites(): ChatResult<List<ChatInvite>> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            val response = chatApiService.getPendingInvites(bearerToken)
            if (response.isSuccessful) {
                val inviteList = response.body()?.invites ?: emptyList()
                _invites.value = inviteList
                ChatResult.Success(inviteList)
            } else {
                parseError(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading invites", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun acceptInvite(roomId: Int): ChatResult<ChatRoom> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            val response = chatApiService.acceptInvite(bearerToken, roomId)
            if (response.isSuccessful) {
                val room = response.body()?.room!!
                _rooms.value = _rooms.value + room
                _invites.value = _invites.value.filter { it.room_id != roomId }
                ChatResult.Success(room)
            } else {
                parseError(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting invite", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun declineInvite(roomId: Int): ChatResult<Unit> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            val response = chatApiService.declineInvite(bearerToken, roomId)
            if (response.isSuccessful) {
                _invites.value = _invites.value.filter { it.room_id != roomId }
                ChatResult.Success(Unit)
            } else {
                parseError(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error declining invite", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun inviteUser(roomId: Int, userId: Int): ChatResult<Unit> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            val response = chatApiService.inviteUser(bearerToken, roomId, InviteUserRequest(userId))
            if (response.isSuccessful) {
                ChatResult.Success(Unit)
            } else {
                parseError(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inviting user", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    // Room member operations
    suspend fun loadRoomMembers(roomId: Int): ChatResult<List<RoomMember>> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return ChatResult.Error("Not logged in")

        return try {
            val response = chatApiService.getRoomMembers(bearerToken, roomId)
            if (response.isSuccessful) {
                val members = response.body()?.members ?: emptyList()
                ChatResult.Success(members)
            } else {
                parseError(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading room members", e)
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    private fun <T> parseError(errorBody: String?): ChatResult<T> {
        val message = try {
            Gson().fromJson(errorBody, ErrorResponse::class.java).message
        } catch (e: Exception) {
            "Operation failed"
        }
        return ChatResult.Error(message)
    }
}
