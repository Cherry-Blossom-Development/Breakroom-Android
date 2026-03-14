package com.cherryblossomdev.breakroom.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// Request/Response data classes
data class LoginRequest(
    val handle: String,
    val password: String
)

data class SignupRequest(
    val handle: String,
    val first_name: String,
    val last_name: String,
    val email: String,
    val hash: String,
    val salt: String
)

data class VerifyRequest(
    val token: String
)

data class ResendVerificationRequest(
    val token: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    val token: String,
    val password: String,
    val salt: String,
    val hash: String
)

data class AuthResponse(
    val message: String,
    val token: String? = null
)

data class MeResponse(
    val username: String,
    val userId: Int
)

data class ErrorResponse(
    val message: String
)

data class EulaStatusResponse(
    val accepted: Boolean,
    val notificationId: Int?
)

data class NotificationStatusRequest(
    val status: String
)

interface ApiService {
    
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @POST("api/auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<AuthResponse>
    
    @POST("api/auth/verify")
    suspend fun verify(@Body request: VerifyRequest): Response<AuthResponse>
    
    @POST("api/auth/resend-verification")
    suspend fun resendVerification(@Body request: ResendVerificationRequest): Response<AuthResponse>
    
    @GET("api/auth/me")
    suspend fun getMe(@Header("Authorization") token: String): Response<MeResponse>
    
    @POST("api/auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<AuthResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<AuthResponse>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<AuthResponse>

    @GET("api/eula/status")
    suspend fun getEulaStatus(@Header("Authorization") token: String): Response<EulaStatusResponse>

    @PUT("api/notification/{id}/status")
    suspend fun updateNotificationStatus(
        @Header("Authorization") token: String,
        @Path("id") notificationId: Int,
        @Body request: NotificationStatusRequest
    ): Response<AuthResponse>
}
