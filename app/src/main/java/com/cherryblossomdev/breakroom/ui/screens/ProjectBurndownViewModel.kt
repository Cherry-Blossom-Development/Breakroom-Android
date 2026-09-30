package com.cherryblossomdev.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.ProjectRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.BurndownResponse
import com.cherryblossomdev.breakroom.projects.BurndownCalculator
import com.cherryblossomdev.breakroom.projects.BurndownMeasure
import com.cherryblossomdev.breakroom.projects.BurndownResult
import com.cherryblossomdev.breakroom.projects.SprintBounds
import com.cherryblossomdev.breakroom.projects.parseApiTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BurndownView { CHART, TABLE }

data class ProjectBurndownUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val data: BurndownResponse? = null,
    val sprintDays: Int = 14,
    val anchor: Long = 0L,
    val currentIndex: Int = 0,
    val sprintIndex: Int = 0,
    val sprint: SprintBounds? = null,
    val measure: BurndownMeasure = BurndownMeasure.WORK,
    val view: BurndownView = BurndownView.CHART,
    val burndown: BurndownResult? = null,
    // Day picked on the chart (tap / drag), shown in the detail card
    val selectedDay: Int? = null,
    val now: Long = System.currentTimeMillis()
)

// Burndown section of the project workspace (web: ProjectBurndownPage.vue).
// The rules live in projects/Burndown.kt; this only loads and recomputes.
class ProjectBurndownViewModel(
    private val projectRepository: ProjectRepository,
    private val projectId: Int,
    private val calculator: BurndownCalculator = BurndownCalculator()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectBurndownUiState())
    val uiState: StateFlow<ProjectBurndownUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = projectRepository.getBurndown(projectId)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    val now = System.currentTimeMillis()
                    val sprintDays = data.project.sprint_duration_days ?: 14
                    val anchor = calculator.sprintAnchor(parseApiTime(data.project.created_at) ?: now)
                    val current = calculator.sprintIndexAt(anchor, sprintDays, now)
                    _uiState.update {
                        recompute(
                            it.copy(
                                isLoading = false,
                                data = data,
                                now = now,
                                sprintDays = sprintDays,
                                anchor = anchor,
                                currentIndex = current,
                                sprintIndex = current
                            )
                        )
                    }
                }
                is BreakroomResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                else -> _uiState.update { it.copy(isLoading = false, error = "Session expired - please log in again") }
            }
        }
    }

    private fun recompute(state: ProjectBurndownUiState): ProjectBurndownUiState {
        val data = state.data ?: return state
        val sprint = calculator.sprintBounds(state.anchor, state.sprintDays, state.sprintIndex)
        return state.copy(
            sprint = sprint,
            selectedDay = null,
            burndown = calculator.build(data.tickets, data.history, sprint.start, sprint.end, state.now, state.measure)
        )
    }

    fun previousSprint() = goToSprint(_uiState.value.sprintIndex - 1)

    fun nextSprint() = goToSprint(_uiState.value.sprintIndex + 1)

    fun currentSprint() = goToSprint(_uiState.value.currentIndex)

    private fun goToSprint(index: Int) {
        _uiState.update {
            if (index < 0 || index > it.currentIndex) it else recompute(it.copy(sprintIndex = index))
        }
    }

    fun setMeasure(measure: BurndownMeasure) {
        _uiState.update { recompute(it.copy(measure = measure)) }
    }

    fun setView(view: BurndownView) {
        _uiState.update { it.copy(view = view, selectedDay = null) }
    }

    fun selectDay(index: Int?) {
        _uiState.update { it.copy(selectedDay = index) }
    }
}
