package com.example.breakroom.data

import com.example.breakroom.data.models.*
import com.example.breakroom.network.BreakroomApiService

class LyricsRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    private fun getAuthHeader(): String? {
        return tokenManager.getBearerToken()
    }

    // ==================== Songs ====================

    suspend fun getSongs(): BreakroomResult<List<Song>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getSongs(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.songs ?: emptyList())
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to load songs")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getSong(songId: Int): BreakroomResult<SongDetailResponse> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getSong(authHeader, songId)
            if (response.isSuccessful) {
                response.body()?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No song data")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to load song")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createSong(
        title: String,
        description: String? = null,
        genre: String? = null,
        status: String = "idea",
        visibility: String = "private"
    ): BreakroomResult<Song> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = CreateSongRequest(
                title = title,
                description = description,
                genre = genre,
                status = status,
                visibility = visibility
            )
            val response = apiService.createSong(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.song?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No song data")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to create song")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateSong(
        songId: Int,
        title: String? = null,
        description: String? = null,
        genre: String? = null,
        status: String? = null,
        visibility: String? = null
    ): BreakroomResult<Song> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateSongRequest(
                title = title,
                description = description,
                genre = genre,
                status = status,
                visibility = visibility
            )
            val response = apiService.updateSong(authHeader, songId, request)
            if (response.isSuccessful) {
                response.body()?.song?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No song data")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to update song")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteSong(songId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deleteSong(authHeader, songId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Song deleted")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to delete song")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== Collaborators ====================

    suspend fun addCollaborator(
        songId: Int,
        handle: String,
        role: String = "editor"
    ): BreakroomResult<SongCollaborator> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = AddCollaboratorRequest(handle = handle, role = role)
            val response = apiService.addCollaborator(authHeader, songId, request)
            if (response.isSuccessful) {
                response.body()?.collaborator?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No collaborator data")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else if (response.code() == 404) {
                BreakroomResult.Error("User not found")
            } else {
                BreakroomResult.Error("Failed to add collaborator")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun removeCollaborator(songId: Int, userId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.removeCollaborator(authHeader, songId, userId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Collaborator removed")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to remove collaborator")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // ==================== Lyrics ====================

    suspend fun getStandaloneLyrics(): BreakroomResult<List<Lyric>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getStandaloneLyrics(authHeader)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.lyrics ?: emptyList())
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to load lyrics")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getLyric(lyricId: Int): BreakroomResult<Lyric> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getLyric(authHeader, lyricId)
            if (response.isSuccessful) {
                response.body()?.lyric?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No lyric data")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to load lyric")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createLyric(
        content: String,
        songId: Int? = null,
        title: String? = null,
        sectionType: String = "idea",
        sectionOrder: Int? = null,
        mood: String? = null,
        notes: String? = null,
        status: String = "draft"
    ): BreakroomResult<Lyric> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = CreateLyricRequest(
                content = content,
                song_id = songId,
                title = title,
                section_type = sectionType,
                section_order = sectionOrder,
                mood = mood,
                notes = notes,
                status = status
            )
            val response = apiService.createLyric(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.lyric?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No lyric data")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to create lyric")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateLyric(
        lyricId: Int,
        content: String? = null,
        songId: Int? = null,
        title: String? = null,
        sectionType: String? = null,
        sectionOrder: Int? = null,
        mood: String? = null,
        notes: String? = null,
        status: String? = null
    ): BreakroomResult<Lyric> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateLyricRequest(
                content = content,
                song_id = songId,
                title = title,
                section_type = sectionType,
                section_order = sectionOrder,
                mood = mood,
                notes = notes,
                status = status
            )
            val response = apiService.updateLyric(authHeader, lyricId, request)
            if (response.isSuccessful) {
                response.body()?.lyric?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No lyric data")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to update lyric")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteLyric(lyricId: Int): BreakroomResult<String> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deleteLyric(authHeader, lyricId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.message ?: "Lyric deleted")
            } else if (response.code() == 401) {
                BreakroomResult.AuthenticationError
            } else {
                BreakroomResult.Error("Failed to delete lyric")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
