package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.BreakroomApiService

class ModerationRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    fun getToken(): String? = tokenManager.getBearerToken()

    private fun getAuthHeader(): String? = tokenManager.getBearerToken()

    suspend fun flagContent(contentType: String, contentId: Int?, reason: String?): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.flagContent(authHeader, FlagRequest(contentType, contentId, reason))
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.message ?: "Reported")
            else BreakroomResult.Error("Failed to submit report")
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun blockUser(userId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.moderationBlockUser(authHeader, userId)
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.message ?: "User blocked")
            else BreakroomResult.Error("Failed to block user")
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun unblockUser(userId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.moderationUnblockUser(authHeader, userId)
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.message ?: "User unblocked")
            else BreakroomResult.Error("Failed to unblock user")
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getBlockList(): BreakroomResult<List<Int>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getModerationBlocks(authHeader)
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.blocked_ids ?: emptyList())
            else BreakroomResult.Error("Failed to load block list")
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
