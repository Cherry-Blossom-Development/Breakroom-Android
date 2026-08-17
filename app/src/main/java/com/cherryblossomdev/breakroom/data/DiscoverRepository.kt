package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.DiscoverGallery
import com.cherryblossomdev.breakroom.data.models.DiscoverShowcase
import com.cherryblossomdev.breakroom.network.BreakroomApiService

class DiscoverRepository(
    private val apiService: BreakroomApiService
) {
    suspend fun getGalleries(): BreakroomResult<List<DiscoverGallery>> {
        return try {
            val response = apiService.getPublicGalleries()
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.galleries ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load galleries")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getShowcases(): BreakroomResult<List<DiscoverShowcase>> {
        return try {
            val response = apiService.getPublicStorefronts()
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.storefronts ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load showcases")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
