package com.example.breakroom.data

import com.example.breakroom.data.models.*
import com.example.breakroom.network.BreakroomApiService

class BlogRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    private fun getAuthHeader(): String? {
        return tokenManager.getBearerToken()
    }

    suspend fun getMyPosts(): BreakroomResult<List<BlogPost>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getMyBlogPosts(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.posts ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load posts")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getPost(postId: Int): BreakroomResult<BlogPost> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getBlogPost(authHeader, postId)
            if (response.isSuccessful) {
                response.body()?.post?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("Post not found")
            } else {
                BreakroomResult.Error("Failed to load post")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createPost(title: String, content: String, isPublished: Boolean): BreakroomResult<BlogPost> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = CreateBlogPostRequest(title, content, isPublished)
            val response = apiService.createBlogPost(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.post?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("Failed to create post")
            } else {
                BreakroomResult.Error("Failed to create post")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updatePost(postId: Int, title: String, content: String, isPublished: Boolean): BreakroomResult<BlogPost> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateBlogPostRequest(title, content, isPublished)
            val response = apiService.updateBlogPost(authHeader, postId, request)
            if (response.isSuccessful) {
                response.body()?.post?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("Failed to update post")
            } else {
                BreakroomResult.Error("Failed to update post")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deletePost(postId: Int): BreakroomResult<Unit> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deleteBlogPost(authHeader, postId)
            if (response.isSuccessful) {
                BreakroomResult.Success(Unit)
            } else {
                BreakroomResult.Error("Failed to delete post")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun viewPost(postId: Int): BreakroomResult<BlogPost> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.viewBlogPost(authHeader, postId)
            if (response.isSuccessful) {
                response.body()?.post?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("Post not found")
            } else {
                BreakroomResult.Error("Failed to load post")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
