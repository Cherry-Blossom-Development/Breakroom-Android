package com.example.breakroom.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.AuthRepository
import com.example.breakroom.data.ProfileRepository
import com.example.breakroom.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val isEditMode: Boolean = false,
    val skillSearchResults: List<Skill> = emptyList(),
    val isSearchingSkills: Boolean = false
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = profileRepository.getProfile()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        profile = result.data,
                        isLoading = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setEditMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isEditMode = enabled)
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        bio: String?,
        workBio: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            when (val result = profileRepository.updateProfile(firstName, lastName, bio, workBio)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        profile = result.data,
                        isSaving = false,
                        isEditMode = false,
                        successMessage = "Profile updated"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isSaving = false
                    )
                }
            }
        }
    }

    fun updateLocation(city: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            when (val result = profileRepository.updateLocation(city)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        profile = result.data,
                        isSaving = false,
                        successMessage = "Location updated"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isSaving = false
                    )
                }
            }
        }
    }

    fun uploadPhoto(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            when (val result = profileRepository.uploadPhoto(uri)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Photo uploaded"
                    )
                    loadProfile()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isSaving = false
                    )
                }
            }
        }
    }

    fun deletePhoto() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            when (val result = profileRepository.deletePhoto()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Photo deleted"
                    )
                    loadProfile()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isSaving = false
                    )
                }
            }
        }
    }

    fun searchSkills(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(skillSearchResults = emptyList())
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingSkills = true)
            when (val result = profileRepository.searchSkills(query)) {
                is BreakroomResult.Success -> {
                    // Filter out skills the user already has
                    val currentSkillIds = _uiState.value.profile?.skills?.map { it.id }?.toSet() ?: emptySet()
                    val filteredSkills = result.data.filter { it.id !in currentSkillIds }
                    _uiState.value = _uiState.value.copy(
                        skillSearchResults = filteredSkills,
                        isSearchingSkills = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSearchingSkills = false
                    )
                }
            }
        }
    }

    fun addSkill(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            when (val result = profileRepository.addSkill(name)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Skill added",
                        skillSearchResults = emptyList()
                    )
                    loadProfile()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isSaving = false
                    )
                }
            }
        }
    }

    fun removeSkill(skillId: Int) {
        viewModelScope.launch {
            when (val result = profileRepository.removeSkill(skillId)) {
                is BreakroomResult.Success -> {
                    loadProfile()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
            }
        }
    }

    fun addJob(
        title: String,
        company: String,
        location: String?,
        startDate: String,
        endDate: String?,
        isCurrent: Boolean,
        description: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            when (val result = profileRepository.addJob(
                title, company, location, startDate, endDate, isCurrent, description
            )) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Job added"
                    )
                    loadProfile()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isSaving = false
                    )
                }
            }
        }
    }

    fun deleteJob(jobId: Int) {
        viewModelScope.launch {
            when (val result = profileRepository.deleteJob(jobId)) {
                is BreakroomResult.Success -> {
                    loadProfile()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun clearSkillSearch() {
        _uiState.value = _uiState.value.copy(skillSearchResults = emptyList())
    }
}
