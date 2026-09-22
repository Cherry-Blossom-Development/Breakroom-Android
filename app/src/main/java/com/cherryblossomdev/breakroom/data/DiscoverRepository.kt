package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.DiscoverBlog
import com.cherryblossomdev.breakroom.data.models.DiscoverGallery
import com.cherryblossomdev.breakroom.data.models.DiscoverShowcase
import com.cherryblossomdev.breakroom.network.BreakroomApiService

data class DiscoverPage<T>(val items: List<T>, val total: Int)

class DiscoverRepository(
    private val apiService: BreakroomApiService
) {
    suspend fun getGalleries(limit: Int, offset: Int, q: String?): BreakroomResult<DiscoverPage<DiscoverGallery>> {
        return try {
            val response = apiService.getPublicGalleries(limit, offset, q?.takeIf { it.isNotBlank() })
            if (response.isSuccessful) {
                val body = response.body()
                BreakroomResult.Success(DiscoverPage(body?.galleries ?: emptyList(), body?.total ?: 0))
            } else {
                BreakroomResult.Error("Failed to load galleries")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getShowcases(limit: Int, offset: Int, q: String?): BreakroomResult<DiscoverPage<DiscoverShowcase>> {
        return try {
            val response = apiService.getPublicStorefronts(limit, offset, q?.takeIf { it.isNotBlank() })
            if (response.isSuccessful) {
                val body = response.body()
                BreakroomResult.Success(DiscoverPage(body?.storefronts ?: emptyList(), body?.total ?: 0))
            } else {
                BreakroomResult.Error("Failed to load showcases")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getBlogs(limit: Int, offset: Int, q: String?): BreakroomResult<DiscoverPage<DiscoverBlog>> {
        return try {
            val response = apiService.getPublicBlogs(limit, offset, q?.takeIf { it.isNotBlank() })
            if (response.isSuccessful) {
                val body = response.body()
                BreakroomResult.Success(DiscoverPage(body?.blogs ?: emptyList(), body?.total ?: 0))
            } else {
                BreakroomResult.Error("Failed to load blogs")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
