package com.example.breakroom.data

import com.example.breakroom.data.models.*
import com.example.breakroom.network.BreakroomApiService

class EmploymentRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    private fun getAuthHeader(): String? {
        return tokenManager.getBearerToken()
    }

    suspend fun getPositions(): BreakroomResult<List<Position>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getPositions(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.positions ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load positions")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
