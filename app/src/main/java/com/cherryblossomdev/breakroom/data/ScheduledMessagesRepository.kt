package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.BreakroomApiService
import com.cherryblossomdev.breakroom.network.ChatApiService

class ScheduledMessagesRepository(
    private val apiService: BreakroomApiService,
    private val chatApiService: ChatApiService,
    private val tokenManager: TokenManager
) {
    private fun auth(): String? = tokenManager.getBearerToken()

    suspend fun getScheduledMessages(): BreakroomResult<List<ScheduledMessage>> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getScheduledMessages(token)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.scheduled_messages ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load scheduled messages")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createScheduledMessage(
        roomId: Int,
        messageText: String,
        scheduledAt: String,
        warningMinutes: Int,
        indicatorText: String
    ): BreakroomResult<ScheduledMessage> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = CreateScheduledMessageRequest(
                room_id = roomId,
                message_text = messageText,
                scheduled_at = scheduledAt,
                warning_minutes = warningMinutes,
                indicator_text = indicatorText
            )
            val response = apiService.createScheduledMessage(token, request)
            if (response.isSuccessful) {
                response.body()?.scheduled_message?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No data returned")
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                BreakroomResult.Error(if (errorBody.contains("future")) "Scheduled time must be in the future" else "Failed to create scheduled message")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateScheduledMessage(
        id: Int,
        roomId: Int,
        messageText: String,
        scheduledAt: String,
        warningMinutes: Int,
        indicatorText: String
    ): BreakroomResult<ScheduledMessage> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateScheduledMessageRequest(
                room_id = roomId,
                message_text = messageText,
                scheduled_at = scheduledAt,
                warning_minutes = warningMinutes,
                indicator_text = indicatorText
            )
            val response = apiService.updateScheduledMessage(token, id, request)
            if (response.isSuccessful) {
                response.body()?.scheduled_message?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No data returned")
            } else {
                BreakroomResult.Error("Failed to update scheduled message")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun cancelScheduledMessage(id: Int): BreakroomResult<Unit> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.cancelScheduledMessage(token, id)
            if (response.isSuccessful) BreakroomResult.Success(Unit)
            else BreakroomResult.Error("Failed to cancel scheduled message")
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun confirmScheduledMessage(id: Int): BreakroomResult<Unit> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.confirmScheduledMessage(token, id)
            if (response.isSuccessful) BreakroomResult.Success(Unit)
            else BreakroomResult.Error("Failed to confirm")
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun pauseEditScheduledMessage(id: Int): BreakroomResult<Unit> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.pauseEditScheduledMessage(token, id)
            if (response.isSuccessful) BreakroomResult.Success(Unit)
            else BreakroomResult.Error("Failed to pause for editing")
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getRooms(): BreakroomResult<List<ChatRoom>> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = chatApiService.getRooms(token)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.rooms ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load rooms")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
