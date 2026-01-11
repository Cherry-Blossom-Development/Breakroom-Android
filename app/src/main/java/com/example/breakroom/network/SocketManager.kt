package com.example.breakroom.network

import android.util.Log
import com.example.breakroom.data.TokenManager
import com.example.breakroom.data.models.ChatMessage
import com.example.breakroom.data.models.SocketConnectionState
import com.example.breakroom.data.models.SocketEvent
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class SocketManager(
    private val tokenManager: TokenManager,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "SocketManager"
    }

    private var socket: Socket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Connection state
    private val _connectionState = MutableStateFlow(SocketConnectionState.DISCONNECTED)
    val connectionState: StateFlow<SocketConnectionState> = _connectionState

    // Event stream
    private val _events = MutableSharedFlow<SocketEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<SocketEvent> = _events

    // Currently joined rooms (for auto-rejoin on reconnect)
    private val joinedRooms = mutableSetOf<Int>()

    fun connect() {
        val token = tokenManager.getToken()
        if (token == null) {
            Log.w(TAG, "Cannot connect: no token available")
            return
        }

        if (socket?.connected() == true) {
            Log.d(TAG, "Already connected")
            return
        }

        _connectionState.value = SocketConnectionState.CONNECTING
        Log.d(TAG, "Connecting to socket...")

        try {
            val options = IO.Options().apply {
                auth = mapOf("token" to token)
                reconnection = true
                reconnectionDelay = 1000
                reconnectionAttempts = 10
            }

            socket = IO.socket(RetrofitClient.BASE_URL, options).apply {
                on(Socket.EVENT_CONNECT, onConnect)
                on(Socket.EVENT_DISCONNECT, onDisconnect)
                on(Socket.EVENT_CONNECT_ERROR, onConnectError)
                on("new_message", onNewMessage)
                on("user_joined", onUserJoined)
                on("user_left", onUserLeft)
                on("user_typing", onUserTyping)
                on("user_stopped_typing", onUserStoppedTyping)
                on("error", onError)
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            _connectionState.value = SocketConnectionState.ERROR
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting socket")
        joinedRooms.clear()
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionState.value = SocketConnectionState.DISCONNECTED
    }

    fun isConnected(): Boolean = socket?.connected() == true

    // Room operations
    fun joinRoom(roomId: Int) {
        Log.d(TAG, "Joining room $roomId")
        socket?.emit("join_room", roomId)
        joinedRooms.add(roomId)
    }

    fun leaveRoom(roomId: Int) {
        Log.d(TAG, "Leaving room $roomId")
        socket?.emit("leave_room", roomId)
        joinedRooms.remove(roomId)
    }

    // Message operations
    fun sendMessage(roomId: Int, content: String): Boolean {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send message: not connected")
            return false
        }

        val data = JSONObject().apply {
            put("roomId", roomId)
            put("message", content)
        }
        Log.d(TAG, "Sending message to room $roomId")
        socket?.emit("send_message", data)
        return true
    }

    // Typing indicators
    fun startTyping(roomId: Int) {
        socket?.emit("typing_start", roomId)
    }

    fun stopTyping(roomId: Int) {
        socket?.emit("typing_stop", roomId)
    }

    // Rejoin rooms after reconnection
    private fun rejoinRooms() {
        Log.d(TAG, "Rejoining ${joinedRooms.size} rooms")
        joinedRooms.forEach { roomId ->
            socket?.emit("join_room", roomId)
        }
    }

    // Event listeners
    private val onConnect = Emitter.Listener {
        Log.d(TAG, "Socket connected")
        _connectionState.value = SocketConnectionState.CONNECTED
        rejoinRooms()
    }

    private val onDisconnect = Emitter.Listener {
        Log.d(TAG, "Socket disconnected")
        _connectionState.value = SocketConnectionState.RECONNECTING
    }

    private val onConnectError = Emitter.Listener { args ->
        val error = args.firstOrNull()?.toString() ?: "Unknown error"
        Log.e(TAG, "Socket connection error: $error")
        _connectionState.value = SocketConnectionState.ERROR
        scope.launch {
            _events.emit(SocketEvent.Error(error))
        }
    }

    private val onNewMessage = Emitter.Listener { args ->
        scope.launch {
            try {
                val data = JSONObject(args[0].toString())
                val roomId = data.getInt("roomId")
                val messageJson = data.getJSONObject("message")
                val message = ChatMessage(
                    id = messageJson.getInt("id"),
                    message = messageJson.optString("message", null),
                    image_path = messageJson.optString("image_path", null),
                    created_at = messageJson.getString("created_at"),
                    user_id = messageJson.getInt("user_id"),
                    handle = messageJson.getString("handle")
                )
                Log.d(TAG, "Received message in room $roomId from ${message.handle}")
                _events.emit(SocketEvent.NewMessage(roomId, message))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing new_message", e)
            }
        }
    }

    private val onUserJoined = Emitter.Listener { args ->
        scope.launch {
            try {
                val data = JSONObject(args[0].toString())
                val roomId = data.getInt("roomId")
                val user = data.getString("user")
                Log.d(TAG, "$user joined room $roomId")
                _events.emit(SocketEvent.UserJoined(roomId, user))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing user_joined", e)
            }
        }
    }

    private val onUserLeft = Emitter.Listener { args ->
        scope.launch {
            try {
                val data = JSONObject(args[0].toString())
                val roomId = data.getInt("roomId")
                val user = data.getString("user")
                Log.d(TAG, "$user left room $roomId")
                _events.emit(SocketEvent.UserLeft(roomId, user))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing user_left", e)
            }
        }
    }

    private val onUserTyping = Emitter.Listener { args ->
        scope.launch {
            try {
                val data = JSONObject(args[0].toString())
                val roomId = data.getInt("roomId")
                val user = data.getString("user")
                _events.emit(SocketEvent.UserTyping(roomId, user))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing user_typing", e)
            }
        }
    }

    private val onUserStoppedTyping = Emitter.Listener { args ->
        scope.launch {
            try {
                val data = JSONObject(args[0].toString())
                val roomId = data.getInt("roomId")
                val user = data.getString("user")
                _events.emit(SocketEvent.UserStoppedTyping(roomId, user))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing user_stopped_typing", e)
            }
        }
    }

    private val onError = Emitter.Listener { args ->
        scope.launch {
            val message = try {
                val data = JSONObject(args[0].toString())
                data.getString("message")
            } catch (e: Exception) {
                args.firstOrNull()?.toString() ?: "Unknown error"
            }
            Log.e(TAG, "Socket error: $message")
            _events.emit(SocketEvent.Error(message))
        }
    }
}
