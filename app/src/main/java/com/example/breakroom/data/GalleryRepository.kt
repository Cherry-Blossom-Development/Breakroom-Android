package com.example.breakroom.data

import android.content.Context
import android.net.Uri
import com.example.breakroom.data.models.*
import com.example.breakroom.network.BreakroomApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class GalleryRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager,
    private val context: Context
) {
    private fun getAuthHeader(): String? = tokenManager.getBearerToken()

    suspend fun getSettings(): BreakroomResult<GallerySettings?> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getGallerySettings(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.settings)
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to load gallery settings")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createSettings(galleryUrl: String, galleryName: String): BreakroomResult<GallerySettings> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = GallerySettingsRequest(gallery_url = galleryUrl, gallery_name = galleryName)
            val response = apiService.createGallerySettings(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.settings?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to create gallery settings")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateSettings(galleryUrl: String, galleryName: String): BreakroomResult<GallerySettings> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = GallerySettingsRequest(gallery_url = galleryUrl, gallery_name = galleryName)
            val response = apiService.updateGallerySettings(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.settings?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to update gallery settings")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun checkUrl(galleryUrl: String): BreakroomResult<GalleryUrlCheckResponse> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.checkGalleryUrl(authHeader, galleryUrl)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No response")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to check URL")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getArtworks(): BreakroomResult<List<Artwork>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getArtworks(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.artworks ?: emptyList())
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to load artworks")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun uploadArtwork(
        imageUri: Uri,
        title: String,
        description: String?,
        isPublished: Boolean
    ): BreakroomResult<Artwork> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            // Copy content URI to a temp file
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return BreakroomResult.Error("Cannot read image")
            val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
            val ext = when (mimeType) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val tempFile = File.createTempFile("artwork_", ".$ext", context.cacheDir)
            FileOutputStream(tempFile).use { output -> inputStream.copyTo(output) }
            inputStream.close()

            val imageBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", "artwork.$ext", imageBody)
            val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
            val isPublishedBody = isPublished.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val descriptionBody = description?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = apiService.uploadArtwork(authHeader, titleBody, descriptionBody, isPublishedBody, imagePart)
            tempFile.delete()

            if (response.isSuccessful) {
                response.body()?.artwork?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to upload artwork")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateArtwork(
        artworkId: Int,
        title: String,
        description: String?,
        isPublished: Boolean
    ): BreakroomResult<Artwork> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateArtworkRequest(title = title, description = description, isPublished = isPublished)
            val response = apiService.updateArtwork(authHeader, artworkId, request)
            if (response.isSuccessful) {
                response.body()?.artwork?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to update artwork")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteArtwork(artworkId: Int): BreakroomResult<Unit> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deleteArtwork(authHeader, artworkId)
            if (response.isSuccessful) {
                BreakroomResult.Success(Unit)
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to delete artwork")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
