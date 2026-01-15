package com.example.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.CompanyRepository
import com.example.breakroom.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CompanyTab {
    INFO, EMPLOYEES, POSITIONS, PROJECTS
}

data class CompanyUiState(
    val company: Company? = null,
    val activeTab: CompanyTab = CompanyTab.INFO,
    val isLoading: Boolean = false,
    val error: String? = null
)

class CompanyViewModel(
    private val companyRepository: CompanyRepository,
    private val companyId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanyUiState())
    val uiState: StateFlow<CompanyUiState> = _uiState.asStateFlow()

    init {
        loadCompany()
    }

    fun loadCompany() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = companyRepository.getCompany(companyId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        company = result.data,
                        isLoading = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun setActiveTab(tab: CompanyTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
