package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.data.models.ImpersonateResponse
import com.cherryblossomdev.breakroom.data.models.ImpersonateStopRequest
import com.cherryblossomdev.breakroom.data.models.SearchUser
import com.cherryblossomdev.breakroom.network.ApiService
import com.cherryblossomdev.breakroom.network.BreakroomApiService

class AdminRepository(
    private val apiService: ApiService,
    private val breakroomApiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    suspend fun checkAdminAccess(): Boolean {
        val token = tokenManager.getBearerToken() ?: return false
        return try {
            val response = apiService.checkPermission(token, "admin_access")
            response.isSuccessful && response.body()?.has_permission == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getAllUsers(): List<SearchUser> {
        val token = tokenManager.getBearerToken() ?: return emptyList()
        return try {
            val response = breakroomApiService.getAllUsers(token)
            if (response.isSuccessful) response.body()?.users ?: emptyList()
            else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun startImpersonation(userId: Int): Result<ImpersonateResponse> {
        val currentToken = tokenManager.getToken() ?: return Result.failure(Exception("Not authenticated"))
        val bearerToken = "Bearer $currentToken"
        return try {
            val response = breakroomApiService.startImpersonation(bearerToken, userId)
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Empty response"))
                tokenManager.saveAdminToken(currentToken)
                tokenManager.saveToken(body.token)
                tokenManager.saveImpersonatedHandle(body.handle)
                Result.success(body)
            } else {
                Result.failure(Exception("Impersonation failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopImpersonation(): Result<Unit> {
        val adminToken = tokenManager.getAdminToken() ?: return Result.failure(Exception("Not impersonating"))
        val bearerToken = tokenManager.getBearerToken() ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val response = breakroomApiService.stopImpersonation(bearerToken, ImpersonateStopRequest(adminToken))
            if (response.isSuccessful) {
                tokenManager.saveToken(adminToken)
                tokenManager.clearImpersonation()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Stop impersonation failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
