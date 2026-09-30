package com.cherryblossomdev.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.KanbanRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.Ticket
import com.cherryblossomdev.breakroom.ui.components.AccessibilityAnnouncement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== Redirect ViewModel ====================

sealed class KanbanRedirectState {
    object Loading : KanbanRedirectState()
    data class Ready(val projectId: Int, val projectTitle: String) : KanbanRedirectState()
    data class NoActiveProjects(val companyId: Int) : KanbanRedirectState()
    data class Error(val message: String) : KanbanRedirectState()
}

class KanbanRedirectViewModel(
    private val repository: KanbanRepository
) : ViewModel() {

    private val _state = MutableStateFlow<KanbanRedirectState>(KanbanRedirectState.Loading)
    val state: StateFlow<KanbanRedirectState> = _state.asStateFlow()

    init {
        determineDestination()
    }

    fun retry() = determineDestination()

    private fun determineDestination() {
        _state.value = KanbanRedirectState.Loading
        viewModelScope.launch {
            // Step 1: Get user's companies
            val companiesResult = repository.getMyCompanies()
            if (companiesResult is BreakroomResult.Error) {
                _state.value = KanbanRedirectState.Error(companiesResult.message)
                return@launch
            }
            val companies = (companiesResult as BreakroomResult.Success).data

            if (companies.isNotEmpty()) {
                // Step 2: Get first company's active projects
                val firstCompany = companies[0]
                val projectsResult = repository.getCompanyProjects(firstCompany.id)
                if (projectsResult is BreakroomResult.Error) {
                    _state.value = KanbanRedirectState.Error(projectsResult.message)
                    return@launch
                }
                val projects = (projectsResult as BreakroomResult.Success).data
                val activeProjects = projects.filter { it.isActive }

                if (activeProjects.isNotEmpty()) {
                    // Prefer non-default project, fall back to first active
                    val project = activeProjects.firstOrNull { !it.isDefault } ?: activeProjects[0]
                    _state.value = KanbanRedirectState.Ready(project.id, project.title)
                } else {
                    _state.value = KanbanRedirectState.NoActiveProjects(firstCompany.id)
                }
            } else {
                // Step 3: No companies — create "Personal Workspace"
                val createResult = repository.createCompany(
                    name = "Personal Workspace",
                    description = "My personal project management workspace",
                    employeeTitle = "Owner"
                )
                if (createResult is BreakroomResult.Error) {
                    _state.value = KanbanRedirectState.Error(createResult.message)
                    return@launch
                }
                val newCompany = (createResult as BreakroomResult.Success).data
                val projectsResult = repository.getCompanyProjects(newCompany.id)
                if (projectsResult is BreakroomResult.Error) {
                    _state.value = KanbanRedirectState.Error(projectsResult.message)
                    return@launch
                }
                val projects = (projectsResult as BreakroomResult.Success).data
                if (projects.isNotEmpty()) {
                    val project = projects[0]
                    _state.value = KanbanRedirectState.Ready(project.id, project.title)
                } else {
                    _state.value = KanbanRedirectState.NoActiveProjects(newCompany.id)
                }
            }
        }
    }
}
