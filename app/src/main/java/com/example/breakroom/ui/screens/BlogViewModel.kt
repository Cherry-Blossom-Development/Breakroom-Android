package com.example.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.BlogRepository
import com.example.breakroom.data.models.BlogPost
import com.example.breakroom.data.models.BreakroomResult
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
    val saveSuccess: Boolean = false
)

class BlogViewModel(
    private val blogRepository: BlogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlogUiState())
    val uiState: StateFlow<BlogUiState> = _uiState.asStateFlow()

    init {
        loadPosts()
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

    fun setCurrentPost(post: BlogPost?) {
        _uiState.value = _uiState.value.copy(currentPost = post)
    }
}
