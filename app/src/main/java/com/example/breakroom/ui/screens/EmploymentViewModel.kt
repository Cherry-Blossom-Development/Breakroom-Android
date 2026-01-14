package com.example.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.EmploymentRepository
import com.example.breakroom.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class EmploymentUiState(
    val positions: List<Position> = emptyList(),
    val filteredPositions: List<Position> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val locationFilter: String = "",  // "", "remote", "onsite", "hybrid"
    val employmentFilter: String = "", // "", "full-time", "part-time", "contract", "internship", "temporary"
    val selectedPosition: Position? = null
)

class EmploymentViewModel(
    private val employmentRepository: EmploymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmploymentUiState())
    val uiState: StateFlow<EmploymentUiState> = _uiState.asStateFlow()

    init {
        loadPositions()
    }

    fun loadPositions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = employmentRepository.getPositions()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        positions = result.data,
                        isLoading = false
                    )
                    applyFilters()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun setLocationFilter(filter: String) {
        _uiState.value = _uiState.value.copy(locationFilter = filter)
        applyFilters()
    }

    fun setEmploymentFilter(filter: String) {
        _uiState.value = _uiState.value.copy(employmentFilter = filter)
        applyFilters()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            locationFilter = "",
            employmentFilter = ""
        )
        applyFilters()
    }

    fun selectPosition(position: Position?) {
        _uiState.value = _uiState.value.copy(selectedPosition = position)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun applyFilters() {
        val state = _uiState.value
        var result = state.positions

        // Apply search query
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            result = result.filter { pos ->
                pos.title.lowercase().contains(query) ||
                pos.company_name.lowercase().contains(query) ||
                (pos.description?.lowercase()?.contains(query) == true)
            }
        }

        // Apply location filter
        if (state.locationFilter.isNotBlank()) {
            result = result.filter { it.location_type == state.locationFilter }
        }

        // Apply employment type filter
        if (state.employmentFilter.isNotBlank()) {
            result = result.filter { it.employment_type == state.employmentFilter }
        }

        _uiState.value = _uiState.value.copy(filteredPositions = result)
    }

    companion object {
        fun formatDate(dateStr: String?): String {
            if (dateStr.isNullOrBlank()) return ""

            return try {
                val date = ZonedDateTime.parse(dateStr).toLocalDate()
                val now = LocalDate.now()
                val diffDays = ChronoUnit.DAYS.between(date, now)

                when {
                    diffDays == 0L -> "Today"
                    diffDays == 1L -> "Yesterday"
                    diffDays < 7 -> "$diffDays days ago"
                    diffDays < 30 -> "${diffDays / 7} weeks ago"
                    else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                }
            } catch (e: Exception) {
                try {
                    // Try parsing without timezone
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    val date = LocalDate.parse(dateStr.substringBefore("T"))
                    val now = LocalDate.now()
                    val diffDays = ChronoUnit.DAYS.between(date, now)

                    when {
                        diffDays == 0L -> "Today"
                        diffDays == 1L -> "Yesterday"
                        diffDays < 7 -> "$diffDays days ago"
                        diffDays < 30 -> "${diffDays / 7} weeks ago"
                        else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                    }
                } catch (e2: Exception) {
                    ""
                }
            }
        }
    }
}
