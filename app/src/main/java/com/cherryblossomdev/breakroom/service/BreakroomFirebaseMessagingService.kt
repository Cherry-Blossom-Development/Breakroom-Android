package com.cherryblossomdev.breakroom.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cherryblossomdev.breakroom.MainActivity
import com.cherryblossomdev.breakroom.data.TokenManager
import com.cherryblossomdev.breakroom.network.FcmTokenRequest
import com.cherryblossomdev.breakroom.network.RetrofitClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BreakroomFirebaseMessagingService : FirebaseMessagingService() {

    /**
     * Called when FCM issues a new registration token (app install, token refresh, etc.).
     * If the user is already logged in, re-register the token with the backend.
     */
    override fun onNewToken(token: String) {
        val tokenManager = TokenManager(applicationContext)
        val bearerToken = tokenManager.getBearerToken() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.apiService.registerFcmToken(bearerToken, FcmTokenRequest(token))
            } catch (e: Exception) { /* non-fatal — will retry on next login */ }
        }
    }

    /**
     * Called when a data message arrives.
     * Skipped if the Socket.IO service is connected (it handles in-app notifications to avoid duplicates).
     */
    override fun onMessageReceived(message: RemoteMessage) {
        if (ChatService.isSocketConnected) return

        val data = message.data
        val type = data["type"] ?: return

        when (type) {
            "chat_message" -> {
                val senderHandle = data["senderHandle"] ?: "Someone"
                val messageText = data["message"] ?: "Sent a message"
                val roomName = data["roomName"] ?: "Chat"
                showNotification(
                    channelId = ChatService.MESSAGE_CHANNEL_ID,
                    title = "$senderHandle in #$roomName",
                    body = messageText
                )
            }
            "friend_request" -> {
                val senderHandle = data["senderHandle"] ?: "Someone"
                showNotification(
                    channelId = ChatService.FRIEND_CHANNEL_ID,
                    title = "Friend Request",
                    body = "$senderHandle sent you a friend request"
                )
            }
            "blog_comment" -> {
                val commenterHandle = data["commenterHandle"] ?: "Someone"
                val preview = data["preview"] ?: ""
                showNotification(
                    channelId = ChatService.BLOG_CHANNEL_ID,
                    title = "New comment from $commenterHandle",
                    body = preview
                )
            }
        }
    }

    private fun showNotification(channelId: String, title: String, body: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
