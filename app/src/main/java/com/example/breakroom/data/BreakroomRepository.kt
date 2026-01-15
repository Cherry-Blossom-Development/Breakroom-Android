package com.example.breakroom.data

import android.util.Log
import com.example.breakroom.data.models.*
import com.example.breakroom.network.BreakroomApiService
import com.example.breakroom.network.ErrorResponse
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BreakroomRepository(
    private val breakroomApiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "BreakroomRepository"
    }

    // Cached blocks
    private val _blocks = MutableStateFlow<List<BreakroomBlock>>(emptyList())
    val blocks: StateFlow<List<BreakroomBlock>> = _blocks.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    suspend fun loadLayout(): BreakroomResult<List<BreakroomBlock>> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return BreakroomResult.Error("Not logged in")

        _isLoading.value = true

        return try {
            Log.d(TAG, "Loading breakroom layout...")
            val response = breakroomApiService.getLayout(bearerToken)

            if (response.isSuccessful) {
                val blockList = response.body()?.blocks ?: emptyList()
                Log.d(TAG, "Loaded ${blockList.size} blocks")
                _blocks.value = blockList
                BreakroomResult.Success(blockList)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Error loading layout: code=${response.code()}, body=$errorBody")
                parseError(response.code(), errorBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading layout", e)
            BreakroomResult.Error(e.message ?: "Network error")
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun addBlock(
        blockType: String,
        contentId: Int? = null,
        title: String? = null,
        width: Int = 2,
        height: Int = 2
    ): BreakroomResult<BreakroomBlock> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return BreakroomResult.Error("Not logged in")

        return try {
            val request = AddBlockRequest(
                block_type = blockType,
                content_id = contentId,
                title = title,
                w = width,
                h = height
            )
            val response = breakroomApiService.addBlock(bearerToken, request)

            if (response.isSuccessful) {
                val block = response.body()!!
                _blocks.value = _blocks.value + block
                BreakroomResult.Success(block)
            } else {
                parseError(response.code(), response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding block", e)
            BreakroomResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun removeBlock(blockId: Int): BreakroomResult<Unit> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return BreakroomResult.Error("Not logged in")

        return try {
            val response = breakroomApiService.removeBlock(bearerToken, blockId)

            if (response.isSuccessful) {
                _blocks.value = _blocks.value.filter { it.id != blockId }
                BreakroomResult.Success(Unit)
            } else {
                parseError(response.code(), response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing block", e)
            BreakroomResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun updateLayout(blocks: List<BreakroomBlock>): BreakroomResult<Unit> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return BreakroomResult.Error("Not logged in")

        return try {
            val positions = blocks.map { block ->
                BlockPosition(
                    id = block.id,
                    x = block.x,
                    y = block.y,
                    w = block.w,
                    h = block.h
                )
            }
            val response = breakroomApiService.updateLayout(bearerToken, UpdateLayoutRequest(positions))

            if (response.isSuccessful) {
                _blocks.value = blocks
                BreakroomResult.Success(Unit)
            } else {
                parseError(response.code(), response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating layout", e)
            BreakroomResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun loadUpdates(limit: Int = 20): BreakroomResult<List<BreakroomUpdate>> {
        val bearerToken = tokenManager.getBearerToken()
            ?: return BreakroomResult.Error("Not logged in")

        return try {
            val response = breakroomApiService.getUpdates(bearerToken, limit)

            if (response.isSuccessful) {
                val updates = response.body()?.updates ?: emptyList()
                BreakroomResult.Success(updates)
            } else {
                parseError(response.code(), response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading updates", e)
            BreakroomResult.Error(e.message ?: "Network error")
        }
    }

    private fun <T> parseError(responseCode: Int, errorBody: String?): BreakroomResult<T> {
        // Check for authentication errors (401 Unauthorized or 403 Forbidden)
        if (responseCode == 401 || responseCode == 403) {
            Log.w(TAG, "Authentication error: $responseCode")
            return BreakroomResult.AuthenticationError
        }

        val message = try {
            val errorResponse = Gson().fromJson(errorBody, ErrorResponse::class.java)
            // Also check for token-related error messages
            if (errorResponse.message.contains("token", ignoreCase = true) ||
                errorResponse.message.contains("unauthorized", ignoreCase = true) ||
                errorResponse.message.contains("not logged in", ignoreCase = true)) {
                Log.w(TAG, "Authentication error from message: ${errorResponse.message}")
                return BreakroomResult.AuthenticationError
            }
            errorResponse.message
        } catch (e: Exception) {
            "Operation failed"
        }
        return BreakroomResult.Error(message)
    }
}
