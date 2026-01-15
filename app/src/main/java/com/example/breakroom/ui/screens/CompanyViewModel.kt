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
            // For now, we'll use a placeholder - we'll need to add a getCompany API call
            // TODO: Add getCompany(companyId) to repository
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun setActiveTab(tab: CompanyTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
