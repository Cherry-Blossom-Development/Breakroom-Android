package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.network.BreakroomApiService

class FeaturesRepository(
    private val api: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    private var cachedFeatures: List<String>? = null

    suspend fun getMyFeatures(): List<String> {
        cachedFeatures?.let { return it }
        return try {
            val token = tokenManager.getToken() ?: return emptyList()
            val response = api.getMyFeatures("Bearer $token")
            if (response.isSuccessful) {
                val features = response.body()?.features ?: emptyList()
                cachedFeatures = features
                features
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun hasFeature(features: List<String>, key: String): Boolean = features.contains(key)

    fun clearCache() {
        cachedFeatures = null
    }
}
