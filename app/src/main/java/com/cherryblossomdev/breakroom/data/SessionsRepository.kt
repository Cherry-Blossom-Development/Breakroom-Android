package com.cherryblossomdev.breakroom.data

import android.content.Context
import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.BreakroomApiService
import com.cherryblossomdev.breakroom.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class SessionsRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager,
    private val context: Context
) {
    private fun getAuthHeader(): String? = tokenManager.getBearerToken()

    // OkHttp client that does NOT follow redirects — used to get S3 URL from the 302 Location header
    private val noRedirectClient = OkHttpClient.Builder()
        .dns(RetrofitClient.fallbackDns)
        .followRedirects(false)
        .build()

    suspend fun getSessions(): BreakroomResult<List<Session>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getSessions(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.sessions ?: emptyList())
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to load sessions")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Returns the S3 URL by following the 302 redirect from /api/sessions/:id/stream
    suspend fun getStreamUrl(sessionId: Int): BreakroomResult<String> {
        val token = tokenManager.getToken() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val url = "${RetrofitClient.BASE_URL}api/sessions/$sessionId/stream"
            val request = Request.Builder()
                .url(url)
                .addHeader("Cookie", "jwtToken=$token")
                .build()
            val response = noRedirectClient.newCall(request).execute()
            response.use {
                val location = it.header("Location")
                if (it.code == 302 && location != null) {
                    BreakroomResult.Success(location)
                } else if (it.code == 401) {
                    BreakroomResult.AuthenticationError
                } else {
                    BreakroomResult.Error("Could not get stream URL (status ${it.code})")
                }
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun uploadSession(
        file: File,
        name: String,
        recordedAt: String?
    ): BreakroomResult<Session> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val mimeType = "audio/m4a"
            val audioPart = MultipartBody.Part.createFormData(
                "audio", file.name,
                file.asRequestBody(mimeType.toMediaTypeOrNull())
            )
            val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val recordedAtPart = recordedAt?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = apiService.uploadSession(authHeader, audioPart, namePart, recordedAtPart)
            if (response.isSuccessful) {
                response.body()?.session?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No session data returned")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Upload failed")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun rateSession(sessionId: Int, rating: Int?): BreakroomResult<SessionRatingResponse> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.rateSession(authHeader, sessionId, RateSessionRequest(rating))
            if (response.isSuccessful) {
                response.body()?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No rating data returned")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to rate session")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateSession(
        sessionId: Int,
        name: String? = null,
        recordedAt: String? = null
    ): BreakroomResult<Session> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateSessionRequest(name = name, recorded_at = recordedAt)
            val response = apiService.updateSession(authHeader, sessionId, request)
            if (response.isSuccessful) {
                response.body()?.session?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No session data returned")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to update session")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteSession(sessionId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deleteSession(authHeader, sessionId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Deleted")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to delete session")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
