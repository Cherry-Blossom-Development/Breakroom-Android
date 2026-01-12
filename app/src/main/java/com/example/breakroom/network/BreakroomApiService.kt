package com.example.breakroom.network

import com.example.breakroom.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface BreakroomApiService {

    // Layout endpoints
    @GET("api/breakroom/layout")
    suspend fun getLayout(
        @Header("Authorization") token: String
    ): Response<BreakroomLayoutResponse>

    @PUT("api/breakroom/layout")
    suspend fun updateLayout(
        @Header("Authorization") token: String,
        @Body request: UpdateLayoutRequest
    ): Response<Unit>

    @POST("api/breakroom/blocks")
    suspend fun addBlock(
        @Header("Authorization") token: String,
        @Body request: AddBlockRequest
    ): Response<BreakroomBlock>

    @DELETE("api/breakroom/blocks/{blockId}")
    suspend fun removeBlock(
        @Header("Authorization") token: String,
        @Path("blockId") blockId: Int
    ): Response<Unit>

    // Updates feed
    @GET("api/breakroom/updates")
    suspend fun getUpdates(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 20
    ): Response<BreakroomUpdatesResponse>

    // Profile for user location
    @GET("api/profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<ProfileResponse>

    // News feed
    @GET("api/breakroom/news")
    suspend fun getNews(
        @Header("Authorization") token: String
    ): Response<NewsResponse>
}
