package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.network.ApiService
import com.cherryblossomdev.breakroom.network.VisitRequest

class AnalyticsRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    // Records one visit for this app process. Safe for anonymous users —
    // the Authorization header is included only when a session exists, mirroring
    // web's credentials:'include' behavior (see backend/routes/analytics.js POST /visit).
    suspend fun recordVisit() {
        try {
            apiService.recordVisit(tokenManager.getBearerToken(), VisitRequest(tokenManager.getOrCreateVisitorId()))
        } catch (e: Exception) {
            // Analytics must never disrupt the user experience
        }
    }
}
