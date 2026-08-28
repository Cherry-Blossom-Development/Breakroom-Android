package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.network.BreakroomApiService

class FeaturesRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    suspend fun getMyFeatures(): BreakroomResult<List<String>> {
        val auth = tokenManager.getBearerToken() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getMyFeatures(auth)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.features ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load features")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
