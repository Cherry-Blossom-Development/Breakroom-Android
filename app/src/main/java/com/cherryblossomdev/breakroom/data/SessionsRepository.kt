package com.cherryblossomdev.breakroom.data

import android.util.Base64
import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.BreakroomApiService
import com.cherryblossomdev.breakroom.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class SessionsRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager,
    private val context: android.content.Context
) {
    private fun auth(): String? = tokenManager.getBearerToken()

    fun buildStreamUrl(sessionId: Int): String =
        "${RetrofitClient.BASE_URL}api/sessions/$sessionId/stream"

    fun getAuthCookie(): String? = tokenManager.getToken()?.let { "jwtToken=$it" }

    fun getMyHandle(): String? {
        val token = tokenManager.getToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val padded = parts[1].let {
                when (it.length % 4) {
                    2 -> "$it=="
                    3 -> "$it="
                    else -> it
                }
            }
            val payload = String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP))
            JSONObject(payload).optString("username").takeIf { it.isNotEmpty() }
        } catch (e: Exception) { null }
    }

    // ==================== Sessions ====================

    suspend fun getSessions(): BreakroomResult<List<Session>> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getSessions(auth)
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.sessions ?: emptyList())
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to load sessions")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun getBandMemberSessions(): BreakroomResult<List<Session>> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getBandMemberSessions(auth)
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.sessions ?: emptyList())
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to load band member sessions")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun uploadSession(
        file: File,
        name: String,
        recordedAt: String?,
        sessionType: String,
        bandId: Int?,
        instrumentId: Int?
    ): BreakroomResult<Session> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val mimeType = "audio/m4a"
            val audioPart = MultipartBody.Part.createFormData(
                "audio", file.name,
                file.asRequestBody(mimeType.toMediaTypeOrNull())
            )
            val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val recordedAtPart = recordedAt?.toRequestBody("text/plain".toMediaTypeOrNull())
            val sessionTypePart = sessionType.toRequestBody("text/plain".toMediaTypeOrNull())
            val bandIdPart = bandId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val instrumentIdPart = instrumentId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = apiService.uploadSession(
                auth, audioPart, namePart, recordedAtPart,
                sessionTypePart, bandIdPart, instrumentIdPart
            )
            if (response.isSuccessful) {
                response.body()?.session?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No session data returned")
            } else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Upload failed")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun rateSession(sessionId: Int, rating: Int?): BreakroomResult<SessionRatingResponse> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.rateSession(auth, sessionId, RateSessionRequest(rating))
            if (response.isSuccessful) response.body()?.let { BreakroomResult.Success(it) }
                ?: BreakroomResult.Error("No rating data")
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to rate session")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun updateSession(
        sessionId: Int,
        name: String? = null,
        recordedAt: String? = null,
        bandId: Int? = null,
        instrumentId: Int? = null
    ): BreakroomResult<Session> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateSessionRequest(name, recordedAt, bandId, instrumentId)
            val response = apiService.updateSession(auth, sessionId, request)
            if (response.isSuccessful) response.body()?.session?.let { BreakroomResult.Success(it) }
                ?: BreakroomResult.Error("No session data")
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to update session")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun deleteSession(sessionId: Int): BreakroomResult<String> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deleteSession(auth, sessionId)
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.message ?: "Deleted")
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to delete session")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    // ==================== Bands ====================

    suspend fun getBands(): BreakroomResult<List<BandListEntry>> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getBands(auth)
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.bands ?: emptyList())
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to load bands")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun getBandDetail(bandId: Int): BreakroomResult<BandDetail> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getBandDetail(auth, bandId)
            if (response.isSuccessful) response.body()?.band?.let { BreakroomResult.Success(it) }
                ?: BreakroomResult.Error("No band data")
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to load band")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun createBand(name: String): BreakroomResult<BandDetail> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.createBand(auth, CreateBandRequest(name))
            if (response.isSuccessful) response.body()?.band?.let { BreakroomResult.Success(it) }
                ?: BreakroomResult.Error("No band data")
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to create band")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun inviteBandMember(bandId: Int, handle: String): BreakroomResult<String> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.inviteBandMember(auth, bandId, InviteMemberRequest(handle))
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.message ?: "Invited")
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else {
                val msg = try {
                    JSONObject(response.errorBody()?.string() ?: "").optString("message")
                } catch (e: Exception) { null }
                BreakroomResult.Error(msg?.takeIf { it.isNotEmpty() } ?: "Failed to invite member")
            }
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun respondBandInvite(bandId: Int, accept: Boolean): BreakroomResult<String> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val action = if (accept) "accept" else "decline"
            val response = apiService.respondBandInvite(auth, bandId, BandInviteActionRequest(action))
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.message ?: action)
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to respond to invite")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun removeBandMember(bandId: Int, userId: Int): BreakroomResult<String> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.removeBandMember(auth, bandId, userId)
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.message ?: "Removed")
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to remove member")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    // ==================== Instruments ====================

    suspend fun getInstruments(): BreakroomResult<List<Instrument>> {
        return try {
            val response = apiService.getInstruments()
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.instruments ?: emptyList())
            else BreakroomResult.Error("Failed to load instruments")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }
}
