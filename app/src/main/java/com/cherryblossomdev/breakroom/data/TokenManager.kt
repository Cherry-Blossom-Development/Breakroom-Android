package com.cherryblossomdev.breakroom.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class TokenManager(private val context: Context) {

    private val sharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "breakroom_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    companion object {
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_VERIFICATION_TOKEN = "verification_token"
        private const val KEY_EULA_ACCEPTED = "eula_accepted"
        private const val KEY_ADMIN_TOKEN = "admin_token"
        private const val KEY_IMPERSONATED_HANDLE = "impersonated_handle"
        private const val KEY_VISITOR_ID = "visitor_id"
    }
    
    fun saveToken(token: String) {
        sharedPreferences.edit().putString(KEY_JWT_TOKEN, token).apply()
    }
    
    fun getToken(): String? {
        return sharedPreferences.getString(KEY_JWT_TOKEN, null)
    }
    
    fun getBearerToken(): String? {
        val token = getToken()
        return if (token != null) "Bearer $token" else null
    }
    
    fun saveUsername(username: String) {
        sharedPreferences.edit().putString(KEY_USERNAME, username).apply()
    }
    
    fun getUsername(): String? {
        return sharedPreferences.getString(KEY_USERNAME, null)
    }
    
    fun saveVerificationToken(token: String) {
        sharedPreferences.edit().putString(KEY_VERIFICATION_TOKEN, token).apply()
    }
    
    fun getVerificationToken(): String? {
        return sharedPreferences.getString(KEY_VERIFICATION_TOKEN, null)
    }
    
    fun saveAdminToken(token: String) {
        sharedPreferences.edit().putString(KEY_ADMIN_TOKEN, token).apply()
    }

    fun getAdminToken(): String? = sharedPreferences.getString(KEY_ADMIN_TOKEN, null)

    fun saveImpersonatedHandle(handle: String) {
        sharedPreferences.edit().putString(KEY_IMPERSONATED_HANDLE, handle).apply()
    }

    fun getImpersonatedHandle(): String? = sharedPreferences.getString(KEY_IMPERSONATED_HANDLE, null)

    fun isImpersonating(): Boolean = getAdminToken() != null

    fun clearImpersonation() {
        sharedPreferences.edit()
            .remove(KEY_ADMIN_TOKEN)
            .remove(KEY_IMPERSONATED_HANDLE)
            .apply()
    }

    fun clearAll() {
        sharedPreferences.edit()
            .remove(KEY_JWT_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_VERIFICATION_TOKEN)
            .remove(KEY_ADMIN_TOKEN)
            .remove(KEY_IMPERSONATED_HANDLE)
            .apply()
        // KEY_EULA_ACCEPTED is intentionally preserved across logout —
        // once accepted on this device it should not re-prompt on next login.
    }
    
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    fun saveEulaAccepted(accepted: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_EULA_ACCEPTED, accepted).apply()
    }

    fun isEulaAccepted(): Boolean {
        return sharedPreferences.getBoolean(KEY_EULA_ACCEPTED, false)
    }

    // Persistent device identifier for analytics, independent of login state.
    // Survives logout (parallels the web client's localStorage-based visitor id).
    fun getOrCreateVisitorId(): String {
        sharedPreferences.getString(KEY_VISITOR_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        sharedPreferences.edit().putString(KEY_VISITOR_ID, id).apply()
        return id
    }
}
