package com.cherryblossomdev.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.ProjectRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// The project workspace's own menu (web: ProjectWorkspacePage.vue menuItems,
// with Settings pinned below them)
enum class ProjectSection(val route: String, val label: String) {
    KANBAN("kanban", "Kanban Board"),
    GANTT("gantt", "GANTT Chart"),
    BURNDOWN("burndown", "Burndown Chart"),
    SETTINGS("settings", "Settings");

    companion object {
        fun fromRoute(route: String?): ProjectSection = entries.firstOrNull { it.route == route } ?: KANBAN
    }
}

data class ProjectWorkspaceUiState(
    val project: Project? = null,
    val isLoading: Boolean = true,
    // Set when the project can't be shown at all (404 / 403 / network)
    val error: String? = null,
    val canWork: Boolean = false,
    val canManage: Boolean = false
)

// Loads the project header (title, company) and the user's access to it.
// Each section loads its own data.
class ProjectWorkspaceViewModel(
    private val projectRepository: ProjectRepository,
    private val projectId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectWorkspaceUiState())
    val uiState: StateFlow<ProjectWorkspaceUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = projectRepository.getProject(projectId)) {
                is BreakroomResult.Success -> _uiState.update {
                    it.copy(
                        project = result.data.project,
                        canWork = result.data.canWork,
                        canManage = result.data.canManage,
                        isLoading = false
                    )
                }
                is BreakroomResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                else -> _uiState.update { it.copy(isLoading = false, error = "Session expired - please log in again") }
            }
        }
    }
}
