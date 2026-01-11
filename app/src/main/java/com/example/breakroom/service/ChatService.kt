package com.example.breakroom.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.breakroom.MainActivity
import com.example.breakroom.R
import com.example.breakroom.data.TokenManager
import com.example.breakroom.data.models.SocketEvent
import com.example.breakroom.network.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChatService : Service() {

    companion object {
        private const val TAG = "ChatService"
        const val CHANNEL_ID = "chat_service_channel"
        const val MESSAGE_CHANNEL_ID = "chat_message_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.example.breakroom.START_CHAT_SERVICE"
        const val ACTION_STOP = "com.example.breakroom.STOP_CHAT_SERVICE"
    }

    private var socketManager: SocketManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ChatService created")
        createNotificationChannels()

        val tokenManager = TokenManager(applicationContext)
        socketManager = SocketManager(tokenManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createServiceNotification())
                connectSocket()
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun connectSocket() {
        Log.d(TAG, "Connecting socket from service")
        socketManager?.connect()

        scope.launch {
            socketManager?.events?.collect { event ->
                when (event) {
                    is SocketEvent.NewMessage -> {
                        // Only show notification if message is from someone else
                        // We don't have currentUserId here, so we show all for now
                        // The app can filter when in foreground
                        showMessageNotification(
                            sender = event.message.handle,
                            message = event.message.message ?: "Sent an image"
                        )
                    }
                    is SocketEvent.Error -> {
                        Log.e(TAG, "Socket error: ${event.message}")
                    }
                    else -> { /* Ignore other events in service */ }
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Service channel (low priority, silent)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Chat Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps chat connection active"
                setShowBadge(false)
            }

            // Message channel (high priority, sound)
            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New chat message notifications"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(messageChannel)
        }
    }

    private fun createServiceNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Breakroom Chat")
            .setContentText("Connected and listening for messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email) // Use default icon for now
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun showMessageNotification(sender: String, message: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setContentTitle(sender)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        // Use unique ID based on time to show multiple notifications
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        Log.d(TAG, "ChatService destroyed")
        scope.cancel()
        socketManager?.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
