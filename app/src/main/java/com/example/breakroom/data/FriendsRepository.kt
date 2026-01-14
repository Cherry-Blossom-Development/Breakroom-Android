package com.example.breakroom.data

import com.example.breakroom.data.models.*
import com.example.breakroom.network.BreakroomApiService

class FriendsRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    private fun getAuthHeader(): String? {
        return tokenManager.getBearerToken()
    }

    suspend fun getFriends(): BreakroomResult<List<Friend>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getFriends(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.friends ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load friends")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getFriendRequests(): BreakroomResult<List<FriendRequest>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getFriendRequests(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.requests ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load friend requests")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getSentRequests(): BreakroomResult<List<FriendRequest>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getSentRequests(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.requests ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load sent requests")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getBlockedUsers(): BreakroomResult<List<BlockedUser>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getBlockedUsers(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.blocked ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load blocked users")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getAllUsers(): BreakroomResult<List<SearchUser>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getAllUsers(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.users ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load users")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun sendFriendRequest(userId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.sendFriendRequest(authHeader, userId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Friend request sent")
            } else {
                BreakroomResult.Error("Failed to send friend request")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun acceptFriendRequest(userId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.acceptFriendRequest(authHeader, userId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Friend request accepted")
            } else {
                BreakroomResult.Error("Failed to accept friend request")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun declineFriendRequest(userId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.declineFriendRequest(authHeader, userId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Friend request declined")
            } else {
                BreakroomResult.Error("Failed to decline friend request")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun cancelFriendRequest(userId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.cancelFriendRequest(authHeader, userId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Friend request cancelled")
            } else {
                BreakroomResult.Error("Failed to cancel friend request")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun removeFriend(userId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.removeFriend(authHeader, userId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Friend removed")
            } else {
                BreakroomResult.Error("Failed to remove friend")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun blockUser(userId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.blockUser(authHeader, userId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "User blocked")
            } else {
                BreakroomResult.Error("Failed to block user")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun unblockUser(userId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.unblockUser(authHeader, userId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "User unblocked")
            } else {
                BreakroomResult.Error("Failed to unblock user")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
