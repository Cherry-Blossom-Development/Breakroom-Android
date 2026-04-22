package com.cherryblossomdev.breakroom.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.AuthRepository
import com.cherryblossomdev.breakroom.data.ProfileRepository
import com.cherryblossomdev.breakroom.data.models.*
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
    val isSearchingSkills: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val deletionRequestSent: Boolean = false
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
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired - please log in again",
                        isLoading = false
                    )
                }
                else -> { }
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
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired - please log in again",
                        isSaving = false
                    )
                }
                else -> { }
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
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired - please log in again",
                        isSaving = false
                    )
                }
                else -> { }
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
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired - please log in again",
                        isSaving = false
                    )
                }
                else -> { }
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
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired - please log in again",
                        isSaving = false
                    )
                }
                else -> { }
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
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isSearchingSkills = false,
                        error = "Session expired - please log in again"
                    )
                }
                else -> { }
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
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired - please log in again",
                        isSaving = false
                    )
                }
                else -> { }
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
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired - please log in again")
                }
                else -> { }
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
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired - please log in again",
                        isSaving = false
                    )
                }
                else -> { }
            }
        }
    }

    fun updateJob(
        jobId: Int,
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
            when (val result = profileRepository.updateJob(
                jobId, title, company, location, startDate, endDate, isCurrent, description
            )) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, successMessage = "Job updated")
                    loadProfile()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message, isSaving = false)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired", isSaving = false)
                }
                else -> { }
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
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired - please log in again")
                }
                else -> { }
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }

    fun submitDeletionRequest(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeletingAccount = true, error = null)
            when (val result = profileRepository.submitDeletionRequest()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isDeletingAccount = false,
                        deletionRequestSent = true
                    )
                    // Brief pause so the user sees the confirmation, then log out
                    kotlinx.coroutines.delay(2000)
                    authRepository.logout()
                    onLoggedOut()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isDeletingAccount = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isDeletingAccount = false,
                        error = "Session expired - please log in again"
                    )
                }
                else -> { }
            }
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
