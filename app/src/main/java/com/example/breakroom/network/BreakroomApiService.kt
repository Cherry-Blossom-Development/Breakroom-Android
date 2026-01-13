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

    // Blog feed (friends posts)
    @GET("api/blog/feed")
    suspend fun getBlogFeed(
        @Header("Authorization") token: String
    ): Response<BlogFeedResponse>

    // Blog management - Get users own posts
    @GET("api/blog/posts")
    suspend fun getMyBlogPosts(
        @Header("Authorization") token: String
    ): Response<BlogPostsResponse>

    // Blog management - Get a single post
    @GET("api/blog/posts/{postId}")
    suspend fun getBlogPost(
        @Header("Authorization") token: String,
        @Path("postId") postId: Int
    ): Response<BlogPostResponse>

    // Blog management - Create a new post
    @POST("api/blog/posts")
    suspend fun createBlogPost(
        @Header("Authorization") token: String,
        @Body request: CreateBlogPostRequest
    ): Response<BlogPostResponse>

    // Blog management - Update a post
    @PUT("api/blog/posts/{postId}")
    suspend fun updateBlogPost(
        @Header("Authorization") token: String,
        @Path("postId") postId: Int,
        @Body request: UpdateBlogPostRequest
    ): Response<BlogPostResponse>

    // Blog management - Delete a post
    @DELETE("api/blog/posts/{postId}")
    suspend fun deleteBlogPost(
        @Header("Authorization") token: String,
        @Path("postId") postId: Int
    ): Response<Unit>

    // Blog - View a published post (with author info)
    @GET("api/blog/view/{postId}")
    suspend fun viewBlogPost(
        @Header("Authorization") token: String,
        @Path("postId") postId: Int
    ): Response<BlogViewResponse>
}
