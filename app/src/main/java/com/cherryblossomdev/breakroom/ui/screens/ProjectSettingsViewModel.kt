package com.cherryblossomdev.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.ProjectRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.InviteSuggestion
import com.cherryblossomdev.breakroom.data.models.ProjectMember
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.cherryblossomdev.breakroom.data.models.ProjectRoles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object ProjectRoleText {
    fun label(role: String) = role.replaceFirstChar { it.uppercase() }

    fun hint(role: String) = when (role) {
        "owner" -> "Manages settings and members, including owners"
        "manager" -> "Manages settings and members"
        "member" -> "Works on tickets"
        "viewer" -> "Read-only"
        else -> ""
    }
}

data class ProjectSettingsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val canManage: Boolean = false,
    val canManageOwners: Boolean = false,
    val currentUserId: Int? = null,
    val roles: List<String> = ProjectRoles.ALL,
    val members: List<ProjectMember> = emptyList(),
    // Sprint duration
    val sprintDurations: List<Int> = listOf(7, 14, 21, 28),
    val savedSprintDays: Int? = null,
    val sprintDays: Int? = null,
    val savingSettings: Boolean = false,
    val settingsMessage: String? = null,
    val settingsError: String? = null,
    // Members
    val busyMember: Int? = null,
    val memberError: String? = null,
    // Invite
    val inviteIdentifier: String = "",
    // People matching what's typed (web a142f34); picking fills the handle
    val inviteSuggestions: List<InviteSuggestion> = emptyList(),
    val inviteRole: String = "member",
    val inviting: Boolean = false,
    val inviteMessage: String? = null,
    val inviteError: String? = null,
    // One-shot: the user left the project and may no longer have access
    val leftProject: Boolean = false
) {
    // Roles the current user may hand out
    val assignableRoles: List<String>
        get() = roles.filter { it != "owner" || canManageOwners }

    fun canEditMember(m: ProjectMember) = canManage && (canManageOwners || m.role != "owner")

    fun canRemoveMember(m: ProjectMember) = m.user_id == currentUserId || canEditMember(m)

    fun removeLabel(m: ProjectMember) = when {
        m.isInvited -> "Cancel invite"
        m.user_id == currentUserId -> "Leave"
        else -> "Remove"
    }
}

// Project settings (web: ProjectSettingsPage.vue, migration 082): sprint
// duration and project members. The backend enforces roles
// (utilities/projectAccess.js); this only hides controls the user can't use.
class ProjectSettingsViewModel(
    private val projectRepository: ProjectRepository,
    private val projectId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectSettingsUiState())
    val uiState: StateFlow<ProjectSettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            when (val result = projectRepository.getSettings(projectId)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            canManage = data.can_manage,
                            canManageOwners = data.can_manage_owners,
                            currentUserId = data.current_user_id,
                            roles = data.roles ?: ProjectRoles.ALL,
                            members = data.members,
                            sprintDurations = data.sprint_durations ?: it.sprintDurations,
                            savedSprintDays = data.settings.sprint_duration_days,
                            sprintDays = data.settings.sprint_duration_days
                        )
                    }
                }
                is BreakroomResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                else -> _uiState.update { it.copy(isLoading = false, error = "Session expired - please log in again") }
            }
        }
    }

    // ---- Sprint duration ----

    fun chooseSprintDays(days: Int) {
        _uiState.update { it.copy(sprintDays = days, settingsMessage = null, settingsError = null) }
    }

    fun saveSettings() {
        val days = _uiState.value.sprintDays ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(savingSettings = true, settingsMessage = null, settingsError = null) }
            when (val result = projectRepository.updateSprintDuration(projectId, days)) {
                is BreakroomResult.Success -> _uiState.update {
                    it.copy(savingSettings = false, savedSprintDays = result.data.sprint_duration_days, settingsMessage = "Saved")
                }
                is BreakroomResult.Error -> _uiState.update { it.copy(savingSettings = false, settingsError = result.message) }
                else -> _uiState.update { it.copy(savingSettings = false, settingsError = "Failed to save settings") }
            }
        }
    }

    // ---- Members ----

    fun changeRole(member: ProjectMember, role: String) {
        if (role == member.role) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyMember = member.user_id, memberError = null) }
            when (val result = projectRepository.changeMemberRole(projectId, member.user_id, role)) {
                is BreakroomResult.Success -> {
                    _uiState.update { it.copy(members = result.data, busyMember = null) }
                    // Changing your own role can change what you may do here
                    if (member.user_id == _uiState.value.currentUserId) load()
                }
                is BreakroomResult.Error -> _uiState.update { it.copy(busyMember = null, memberError = result.message) }
                else -> _uiState.update { it.copy(busyMember = null, memberError = "Failed to change role") }
            }
        }
    }

    fun removeMember(member: ProjectMember) {
        val isSelf = member.user_id == _uiState.value.currentUserId
        viewModelScope.launch {
            _uiState.update { it.copy(busyMember = member.user_id, memberError = null) }
            when (val result = projectRepository.removeMember(projectId, member.user_id)) {
                is BreakroomResult.Success -> {
                    if (isSelf && !member.isInvited) {
                        // You may no longer have access to this project
                        _uiState.update { it.copy(busyMember = null, leftProject = true) }
                    } else {
                        _uiState.update { it.copy(members = result.data, busyMember = null) }
                    }
                }
                is BreakroomResult.Error -> _uiState.update { it.copy(busyMember = null, memberError = result.message) }
                else -> _uiState.update { it.copy(busyMember = null, memberError = "Failed to remove member") }
            }
        }
    }

    fun onLeftProjectHandled() {
        _uiState.update { it.copy(leftProject = false) }
    }

    // ---- Invite ----

    private var suggestJob: Job? = null

    // 2+ characters, after a short pause; an email (an @ past the start)
    // skips the lookup -- the server never matches or returns emails
    fun updateInviteIdentifier(text: String) {
        _uiState.update { it.copy(inviteIdentifier = text, inviteError = null) }
        suggestJob?.cancel()
        val q = text.trim()
        if (q.length < 2 || q.indexOf('@') > 0) {
            _uiState.update { it.copy(inviteSuggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(200)
            when (val result = projectRepository.getInviteSuggestions(projectId, q)) {
                is BreakroomResult.Success -> _uiState.update {
                    if (it.inviteIdentifier.trim() == q) it.copy(inviteSuggestions = result.data) else it
                }
                else -> _uiState.update { it.copy(inviteSuggestions = emptyList()) }
            }
        }
    }

    fun pickInviteSuggestion(user: InviteSuggestion) {
        suggestJob?.cancel()
        _uiState.update { it.copy(inviteIdentifier = user.handle, inviteSuggestions = emptyList(), inviteError = null) }
    }

    fun dismissInviteSuggestions() {
        suggestJob?.cancel()
        _uiState.update { it.copy(inviteSuggestions = emptyList()) }
    }

    fun updateInviteRole(role: String) {
        _uiState.update { it.copy(inviteRole = role) }
    }

    fun sendInvite() {
        val identifier = _uiState.value.inviteIdentifier.trim()
        if (identifier.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(inviting = true, inviteMessage = null, inviteError = null) }
            when (val result = projectRepository.inviteMember(projectId, identifier, _uiState.value.inviteRole)) {
                is BreakroomResult.Success -> _uiState.update {
                    it.copy(
                        inviting = false,
                        members = result.data.members,
                        inviteMessage = result.data.message ?: "Invite sent",
                        inviteIdentifier = "",
                        inviteSuggestions = emptyList(),
                        inviteRole = "member"
                    )
                }
                is BreakroomResult.Error -> _uiState.update { it.copy(inviting = false, inviteError = result.message) }
                else -> _uiState.update { it.copy(inviting = false, inviteError = "Failed to send invite") }
            }
        }
    }
}
