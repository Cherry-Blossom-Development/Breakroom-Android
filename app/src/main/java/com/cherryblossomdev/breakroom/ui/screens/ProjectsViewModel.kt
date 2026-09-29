package com.cherryblossomdev.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.BreakroomRepository
import com.cherryblossomdev.breakroom.data.ProjectRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.Project
import com.cherryblossomdev.breakroom.data.models.ProjectInvite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// The Help Desk page is hardcoded to Cherry Blossom Development (company 1),
// so only that company's default project opens it -- every other project,
// including other companies' default projects, uses the project board.
// Mirrors web's utilities/projectLinks.js.
private const val HELP_DESK_COMPANY_ID = 1

fun Project.isHelpDeskProject(): Boolean = isDefault && company_id == HELP_DESK_COMPANY_ID

// Shortcut URL for a project, same as web's getProjectHomepageLink()
fun Project.homepageUrl(): String = if (isHelpDeskProject()) "/help-desk" else "/project/$id"

data class CompanyFilterOption(val id: Int, val name: String)

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val invites: List<ProjectInvite> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val inviteError: String? = null,
    val respondingTo: Int? = null,
    val companyFilter: Int? = null,  // null = all companies
    // Shortcut URL -> shortcut id
    val shortcutIds: Map<String, Int> = emptyMap(),
    val togglingShortcutFor: Int? = null,
    val message: String? = null
) {
    val companies: List<CompanyFilterOption>
        get() = projects
            .associate { it.company_id to (it.company_name ?: "Company") }
            .map { (id, name) -> CompanyFilterOption(id, name) }
            .sortedBy { it.name.lowercase() }

    val filteredProjects: List<Project>
        get() = companyFilter?.let { id -> projects.filter { it.company_id == id } } ?: projects

    val activeCount: Int
        get() = filteredProjects.count { it.isActive }
}

// Cross-company view of the projects the user can reach (every company they
// work for, plus projects they've joined as a member), with pending project
// invites to answer. Management stays on the company screen.
class ProjectsViewModel(
    private val projectRepository: ProjectRepository,
    private val breakroomRepository: BreakroomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        loadProjects()
        loadInvites()
        loadShortcuts()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = projectRepository.getMyProjects()) {
                is BreakroomResult.Success -> _uiState.update { state ->
                    // Drop a company filter that no longer matches anything
                    val filter = state.companyFilter?.takeIf { id -> result.data.any { it.company_id == id } }
                    state.copy(projects = result.data, companyFilter = filter, isLoading = false)
                }
                is BreakroomResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                else ->
                    _uiState.update { it.copy(isLoading = false, error = "Session expired - please log in again") }
            }
        }
    }

    private fun loadInvites() {
        viewModelScope.launch {
            when (val result = projectRepository.getMyInvites()) {
                is BreakroomResult.Success -> _uiState.update { it.copy(invites = result.data) }
                else -> { /* invites are optional; the list still works without them */ }
            }
        }
    }

    private fun loadShortcuts() {
        viewModelScope.launch {
            when (val result = breakroomRepository.loadShortcuts()) {
                is BreakroomResult.Success ->
                    _uiState.update { it.copy(shortcutIds = result.data.associate { s -> s.url to s.id }) }
                else -> { /* silently fail */ }
            }
        }
    }

    fun setCompanyFilter(companyId: Int?) {
        _uiState.update { it.copy(companyFilter = companyId) }
    }

    fun respondToInvite(invite: ProjectInvite, accept: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(respondingTo = invite.project_id, inviteError = null) }
            when (val result = projectRepository.respondToInvite(invite.project_id, accept)) {
                is BreakroomResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            invites = state.invites.filter { it.project_id != invite.project_id },
                            respondingTo = null
                        )
                    }
                    if (accept) loadProjects()
                }
                is BreakroomResult.Error ->
                    _uiState.update { it.copy(respondingTo = null, inviteError = result.message) }
                else ->
                    _uiState.update { it.copy(respondingTo = null, inviteError = "Session expired - please log in again") }
            }
        }
    }

    fun toggleShortcut(project: Project, onChanged: () -> Unit) {
        val url = project.homepageUrl()
        val existingId = _uiState.value.shortcutIds[url]
        viewModelScope.launch {
            _uiState.update { it.copy(togglingShortcutFor = project.id) }
            if (existingId != null) {
                when (val result = breakroomRepository.deleteShortcut(existingId)) {
                    is BreakroomResult.Success -> {
                        _uiState.update { it.copy(shortcutIds = it.shortcutIds - url, togglingShortcutFor = null) }
                        onChanged()
                    }
                    is BreakroomResult.Error ->
                        _uiState.update { it.copy(togglingShortcutFor = null, message = result.message) }
                    else -> _uiState.update { it.copy(togglingShortcutFor = null, message = "Failed to remove shortcut") }
                }
            } else {
                when (val result = breakroomRepository.createShortcut(project.title, url)) {
                    is BreakroomResult.Success -> {
                        _uiState.update {
                            it.copy(shortcutIds = it.shortcutIds + (url to result.data.id), togglingShortcutFor = null)
                        }
                        onChanged()
                    }
                    is BreakroomResult.Error ->
                        _uiState.update { it.copy(togglingShortcutFor = null, message = result.message) }
                    else -> _uiState.update { it.copy(togglingShortcutFor = null, message = "Failed to create shortcut") }
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
