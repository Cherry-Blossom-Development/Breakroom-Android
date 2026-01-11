package com.example.breakroom.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

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
}
