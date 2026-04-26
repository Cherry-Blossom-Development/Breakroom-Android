package com.cherryblossomdev.breakroom.data

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
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
    fun getRawToken(): String? = tokenManager.getToken()

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
            else if (response.code() == 402) BreakroomResult.SubscriptionRequired
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
            else if (response.code() == 402) BreakroomResult.SubscriptionRequired
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

    // ==================== Audio Defaults ====================

    suspend fun getAudioDefaults(): BreakroomResult<AudioDefaults> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getAudioDefaults(auth)
            if (response.isSuccessful) BreakroomResult.Success(response.body() ?: AudioDefaults())
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to load audio defaults")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun saveAudioDefaults(defaults: AudioDefaults): BreakroomResult<AudioDefaults> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.updateAudioDefaults(auth, AudioDefaultsRequest(
                echo_cancellation = defaults.echo_cancellation,
                noise_suppression = defaults.noise_suppression,
                auto_gain_control = defaults.auto_gain_control,
                playback_volume = defaults.playback_volume
            ))
            if (response.isSuccessful) BreakroomResult.Success(response.body() ?: defaults)
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to save audio defaults")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    // ==================== User Devices ====================

    @SuppressLint("HardwareIds")
    private fun getDeviceToken(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    private fun buildSystemName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val display = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        return "$display · Android ${Build.VERSION.RELEASE}"
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.contains("emulator") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for") ||
        Build.MANUFACTURER.contains("Genymotion") ||
        Build.PRODUCT.startsWith("sdk") ||
        Build.HARDWARE == "goldfish" ||
        Build.HARDWARE == "ranchu"

    suspend fun registerDevice(): BreakroomResult<UserDevice> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        val request = DeviceRegistrationRequest(
            deviceToken = getDeviceToken(),
            systemName = buildSystemName(),
            platform = "android",
            isEmulator = isEmulator(),
            deviceInfo = mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "device" to Build.DEVICE,
                "androidVersion" to Build.VERSION.RELEASE,
                "sdkInt" to Build.VERSION.SDK_INT.toString()
            )
        )
        return try {
            val response = apiService.registerDevice(auth, request)
            if (response.isSuccessful) BreakroomResult.Success(response.body()?.device ?: return BreakroomResult.Error("Empty response"))
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to register device")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }

    suspend fun saveDeviceName(deviceToken: String, userName: String?): BreakroomResult<Unit> {
        val auth = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.updateDeviceName(auth, deviceToken, DeviceNameRequest(userName))
            if (response.isSuccessful) BreakroomResult.Success(Unit)
            else if (response.code() == 401) BreakroomResult.AuthenticationError
            else BreakroomResult.Error("Failed to update device name")
        } catch (e: Exception) { BreakroomResult.Error(e.message ?: "Unknown error") }
    }
}
