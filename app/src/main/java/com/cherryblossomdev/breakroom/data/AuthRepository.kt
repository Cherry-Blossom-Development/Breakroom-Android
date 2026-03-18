package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.network.ApiService
import com.cherryblossomdev.breakroom.network.AuthResponse
import com.cherryblossomdev.breakroom.network.EulaStatusResponse
import com.cherryblossomdev.breakroom.network.LoginRequest
import com.cherryblossomdev.breakroom.network.MeResponse
import com.cherryblossomdev.breakroom.network.ForgotPasswordRequest
import com.cherryblossomdev.breakroom.network.NotificationStatusRequest
import com.cherryblossomdev.breakroom.network.ResendVerificationRequest
import com.cherryblossomdev.breakroom.network.ResetPasswordRequest
import com.cherryblossomdev.breakroom.network.SignupRequest
import com.cherryblossomdev.breakroom.network.VerifyRequest
import com.google.gson.Gson
import com.cherryblossomdev.breakroom.network.ErrorResponse
import java.security.MessageDigest

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    
    suspend fun login(handle: String, password: String): AuthResult<AuthResponse> {
        return try {
            val response = apiService.login(LoginRequest(handle, password))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.token != null) {
                    tokenManager.saveToken(body.token)
                    tokenManager.saveUsername(handle)
                    AuthResult.Success(body)
                } else {
                    AuthResult.Error("No token received")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    Gson().fromJson(errorBody, ErrorResponse::class.java).message
                } catch (e: Exception) {
                    "Login failed"
                }
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun signup(
        handle: String,
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ): AuthResult<AuthResponse> {
        return try {
            // Generate salt and hash password (matching web app behavior)
            val salt = generateSalt()
            val hash = hashPassword(password, salt)
            
            val response = apiService.signup(
                SignupRequest(
                    handle = handle,
                    first_name = firstName,
                    last_name = lastName,
                    email = email,
                    hash = hash,
                    salt = salt
                )
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.token != null) {
                    tokenManager.saveToken(body.token)
                    tokenManager.saveUsername(handle)
                    AuthResult.Success(body)
                } else {
                    AuthResult.Error("Signup succeeded but no token received")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    Gson().fromJson(errorBody, ErrorResponse::class.java).message
                } catch (e: Exception) {
                    "Signup failed"
                }
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun verify(verificationToken: String): AuthResult<AuthResponse> {
        return try {
            val response = apiService.verify(VerifyRequest(verificationToken))
            if (response.isSuccessful) {
                AuthResult.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    Gson().fromJson(errorBody, ErrorResponse::class.java).message
                } catch (e: Exception) {
                    "Verification failed"
                }
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun resendVerification(verificationToken: String): AuthResult<AuthResponse> {
        return try {
            val response = apiService.resendVerification(ResendVerificationRequest(verificationToken))
            if (response.isSuccessful) {
                AuthResult.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    Gson().fromJson(errorBody, ErrorResponse::class.java).message
                } catch (e: Exception) {
                    "Failed to resend verification"
                }
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun getMe(): AuthResult<MeResponse> {
        val bearerToken = tokenManager.getBearerToken() 
            ?: return AuthResult.Error("Not logged in")
        
        return try {
            val response = apiService.getMe(bearerToken)
            if (response.isSuccessful) {
                AuthResult.Success(response.body()!!)
            } else {
                AuthResult.Error("Failed to get user info")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }
    
    suspend fun logout(): AuthResult<AuthResponse> {
        val bearerToken = tokenManager.getBearerToken()
        
        return try {
            if (bearerToken != null) {
                apiService.logout(bearerToken)
            }
            tokenManager.clearAll()
            AuthResult.Success(AuthResponse("Logged out successfully"))
        } catch (e: Exception) {
            // Still clear local tokens even if network call fails
            tokenManager.clearAll()
            AuthResult.Success(AuthResponse("Logged out locally"))
        }
    }
    
    suspend fun forgotPassword(email: String): AuthResult<AuthResponse> {
        return try {
            val response = apiService.forgotPassword(ForgotPasswordRequest(email))
            if (response.isSuccessful) {
                AuthResult.Success(response.body()!!)
            } else {
                AuthResult.Error("Request failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun resetPassword(token: String, password: String): AuthResult<AuthResponse> {
        return try {
            val salt = generateSalt()
            val hash = hashPassword(password, salt)
            val response = apiService.resetPassword(ResetPasswordRequest(token, password, salt, hash))
            if (response.isSuccessful) {
                AuthResult.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    Gson().fromJson(errorBody, ErrorResponse::class.java).message
                } catch (e: Exception) {
                    "Reset failed"
                }
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun getEulaStatus(): AuthResult<EulaStatusResponse> {
        return try {
            val token = tokenManager.getBearerToken() ?: return AuthResult.Error("Not authenticated")
            val response = apiService.getEulaStatus(token)
            if (response.isSuccessful) {
                val body = response.body()!!
                if (body.accepted) tokenManager.saveEulaAccepted(true)
                AuthResult.Success(body)
            } else {
                AuthResult.Error("Failed to fetch EULA status")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun acceptEula(): AuthResult<AuthResponse> {
        return try {
            val token = tokenManager.getBearerToken() ?: return AuthResult.Error("Not authenticated")
            val response = apiService.acceptEula(token)
            if (response.isSuccessful) {
                tokenManager.saveEulaAccepted(true)
                AuthResult.Success(response.body()!!)
            } else {
                AuthResult.Error("Failed to accept EULA")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()
    
    fun getStoredUsername(): String? = tokenManager.getUsername()
    
    // Helper functions for password hashing (matching web app)
    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun hashPassword(password: String, salt: String): String {
        val combined = password + salt
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(combined.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
