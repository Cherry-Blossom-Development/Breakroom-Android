package com.cherryblossomdev.breakroom.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.BlogRepository
import com.cherryblossomdev.breakroom.data.models.BlogPost
import com.cherryblossomdev.breakroom.data.models.BlogSettings
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BlogUiState(
    val posts: List<BlogPost> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPost: BlogPost? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saveSuccess: Boolean = false,
    val isUploadingImage: Boolean = false,
    val uploadedImageUrl: String? = null,
    // Blog settings
    val settings: BlogSettings? = null,
    val showSettingsPanel: Boolean = false,
    val blogUrlInput: String = "",
    val blogNameInput: String = "",
    val isSavingSettings: Boolean = false,
    val settingsError: String? = null,
    val settingsSuccess: Boolean = false
)

class BlogViewModel(
    private val blogRepository: BlogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlogUiState())
    val uiState: StateFlow<BlogUiState> = _uiState.asStateFlow()

    init {
        loadPosts()
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            when (val result = blogRepository.getSettings()) {
                is BreakroomResult.Success -> {
                    val s = result.data
                    _uiState.value = _uiState.value.copy(
                        settings = s,
                        blogUrlInput = s?.blog_url ?: "",
                        blogNameInput = s?.blog_name ?: ""
                    )
                }
                else -> {}
            }
        }
    }

    fun toggleSettingsPanel() {
        val current = _uiState.value
        _uiState.value = current.copy(
            showSettingsPanel = !current.showSettingsPanel,
            blogUrlInput = current.settings?.blog_url ?: "",
            blogNameInput = current.settings?.blog_name ?: "",
            settingsError = null,
            settingsSuccess = false
        )
    }

    fun setBlogUrlInput(value: String) {
        _uiState.value = _uiState.value.copy(blogUrlInput = value)
    }

    fun setBlogNameInput(value: String) {
        _uiState.value = _uiState.value.copy(blogNameInput = value)
    }

    fun saveSettings() {
        val state = _uiState.value
        val url = state.blogUrlInput.trim()
        val name = state.blogNameInput.trim().ifEmpty { "$url's Blog" }
        if (url.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingSettings = true, settingsError = null)
            val result = if (state.settings == null) {
                blogRepository.createSettings(url, name)
            } else {
                blogRepository.updateSettings(url, name)
            }
            when (result) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        settings = result.data,
                        blogUrlInput = result.data.blog_url,
                        blogNameInput = result.data.blog_name,
                        isSavingSettings = false,
                        showSettingsPanel = false,
                        settingsSuccess = true
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSavingSettings = false,
                        settingsError = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isSavingSettings = false,
                        settingsError = "Session expired"
                    )
                }
            }
        }
    }

    fun clearSettingsSuccess() {
        _uiState.value = _uiState.value.copy(settingsSuccess = false)
    }

    fun loadPosts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = blogRepository.getMyPosts()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        posts = result.data,
                        isLoading = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoading = false
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired - please log in again",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun loadPost(postId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = blogRepository.getPost(postId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        currentPost = result.data,
                        isLoading = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoading = false
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired - please log in again",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun createPost(title: String, content: String, isPublished: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null, saveSuccess = false)
            when (val result = blogRepository.createPost(title, content, isPublished)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveSuccess = true
                    )
                    loadPosts()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        saveError = result.message,
                        isSaving = false
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        saveError = "Session expired - please log in again",
                        isSaving = false
                    )
                }
            }
        }
    }

    fun updatePost(postId: Int, title: String, content: String, isPublished: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null, saveSuccess = false)
            when (val result = blogRepository.updatePost(postId, title, content, isPublished)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveSuccess = true
                    )
                    loadPosts()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        saveError = result.message,
                        isSaving = false
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        saveError = "Session expired - please log in again",
                        isSaving = false
                    )
                }
            }
        }
    }

    fun deletePost(postId: Int) {
        viewModelScope.launch {
            when (val result = blogRepository.deletePost(postId)) {
                is BreakroomResult.Success -> {
                    loadPosts()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired - please log in again")
                }
            }
        }
    }

    fun clearCurrentPost() {
        _uiState.value = _uiState.value.copy(currentPost = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSaveState() {
        _uiState.value = _uiState.value.copy(saveError = null, saveSuccess = false)
    }

    fun uploadImage(uri: Uri, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingImage = true)
            when (val result = blogRepository.uploadImage(uri)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(isUploadingImage = false, uploadedImageUrl = result.data)
                    onSuccess(result.data)
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(isUploadingImage = false, saveError = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(isUploadingImage = false, saveError = "Session expired")
                }
            }
        }
    }

    fun clearUploadedImage() {
        _uiState.value = _uiState.value.copy(uploadedImageUrl = null)
    }

    fun setCurrentPost(post: BlogPost?) {
        _uiState.value = _uiState.value.copy(currentPost = post)
    }
}
