package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.BreakroomApiService

class SubscriptionApiRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    private fun auth(): String? = tokenManager.getBearerToken()

    suspend fun getStatus(): BreakroomResult<SubscriptionStatus> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getSubscriptionStatus(auth)
            if (response.isSuccessful) response.body()?.let {
                BreakroomResult.Success(SubscriptionStatus(it.subscribed))
            } ?: BreakroomResult.Error("No data")
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to check subscription")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun verifyGooglePurchase(purchaseToken: String, productId: String): BreakroomResult<Unit> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.verifyGooglePurchase(auth, GoogleVerifyRequest(purchaseToken, productId))
            if (response.isSuccessful) BreakroomResult.Success(Unit)
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Verification failed")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }
}
