package com.example.breakroom.data

import android.content.Context
import android.net.Uri
import com.example.breakroom.data.models.*
import com.example.breakroom.network.BreakroomApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ProfileRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager,
    private val context: Context
) {
    private fun getAuthHeader(): String? {
        return tokenManager.getBearerToken()
    }

    suspend fun getProfile(): BreakroomResult<UserProfile> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getProfile(authHeader)
            if (response.isSuccessful) {
                response.body()?.let {
                    BreakroomResult.Success(it.toUserProfile())
                } ?: BreakroomResult.Error("Failed to load profile")
            } else {
                BreakroomResult.Error("Failed to load profile")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateProfile(
        firstName: String,
        lastName: String,
        bio: String?,
        workBio: String?
    ): BreakroomResult<UserProfile> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateProfileRequest(firstName, lastName, bio, workBio)
            val response = apiService.updateProfile(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.let {
                    BreakroomResult.Success(it.toUserProfile())
                } ?: BreakroomResult.Error("Failed to update profile")
            } else {
                BreakroomResult.Error("Failed to update profile")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateLocation(city: String): BreakroomResult<UserProfile> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateLocationRequest(city)
            val response = apiService.updateLocation(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.let {
                    BreakroomResult.Success(it.toUserProfile())
                } ?: BreakroomResult.Error("Failed to update location")
            } else {
                BreakroomResult.Error("Failed to update location")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateTimezone(timezone: String): BreakroomResult<UserProfile> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateTimezoneRequest(timezone)
            val response = apiService.updateTimezone(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.let {
                    BreakroomResult.Success(it.toUserProfile())
                } ?: BreakroomResult.Error("Failed to update timezone")
            } else {
                BreakroomResult.Error("Failed to update timezone")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun uploadPhoto(uri: Uri): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return BreakroomResult.Error("Could not open image")

            val bytes = inputStream.readBytes()
            inputStream.close()

            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val fileName = "photo.${mimeType.substringAfter("/")}"
            val part = MultipartBody.Part.createFormData("photo", fileName, requestBody)

            val response = apiService.uploadPhoto(authHeader, part)
            if (response.isSuccessful) {
                response.body()?.let {
                    BreakroomResult.Success(it.photo_path)
                } ?: BreakroomResult.Error("Failed to upload photo")
            } else {
                BreakroomResult.Error("Failed to upload photo")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deletePhoto(): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deletePhoto(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Photo deleted")
            } else {
                BreakroomResult.Error("Failed to delete photo")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun searchSkills(query: String): BreakroomResult<List<Skill>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.searchSkills(authHeader, query)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.skills ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to search skills")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun addSkill(name: String): BreakroomResult<Skill> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = AddSkillRequest(name)
            val response = apiService.addSkill(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.let {
                    BreakroomResult.Success(Skill(it.id, it.name))
                } ?: BreakroomResult.Error("Failed to add skill")
            } else {
                BreakroomResult.Error("Failed to add skill")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun removeSkill(skillId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.removeSkill(authHeader, skillId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Skill removed")
            } else {
                BreakroomResult.Error("Failed to remove skill")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun addJob(
        title: String,
        company: String,
        location: String?,
        startDate: String,
        endDate: String?,
        isCurrent: Boolean,
        description: String?
    ): BreakroomResult<UserJob> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = AddJobRequest(title, company, location, startDate, endDate, isCurrent, description)
            val response = apiService.addJob(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.let {
                    BreakroomResult.Success(it.job)
                } ?: BreakroomResult.Error("Failed to add job")
            } else {
                BreakroomResult.Error("Failed to add job")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteJob(jobId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deleteJob(authHeader, jobId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Job deleted")
            } else {
                BreakroomResult.Error("Failed to delete job")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
