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
    val employees: List<CompanyEmployee> = emptyList(),
    val positions: List<Position> = emptyList(),
    val selectedPosition: Position? = null,
    val editingPosition: Position? = null,
    val activeTab: CompanyTab = CompanyTab.INFO,
    val isLoading: Boolean = false,
    val isLoadingEmployees: Boolean = false,
    val isLoadingPositions: Boolean = false,
    val isSavingEmployee: Boolean = false,
    val isCreatingPosition: Boolean = false,
    val isDeletingPosition: Boolean = false,
    val isUpdatingPosition: Boolean = false,
    val showCreatePositionDialog: Boolean = false,
    val editingEmployee: CompanyEmployee? = null,
    val error: String? = null,
    val successMessage: String? = null
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
        // Load data when switching to tabs (if not already loaded)
        if (tab == CompanyTab.EMPLOYEES && _uiState.value.employees.isEmpty() && !_uiState.value.isLoadingEmployees) {
            loadEmployees()
        }
        if (tab == CompanyTab.POSITIONS && _uiState.value.positions.isEmpty() && !_uiState.value.isLoadingPositions) {
            loadPositions()
        }
    }

    fun loadEmployees() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingEmployees = true)
            when (val result = companyRepository.getCompanyEmployees(companyId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        employees = result.data,
                        isLoadingEmployees = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingEmployees = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingEmployees = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun loadPositions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPositions = true)
            when (val result = companyRepository.getCompanyPositions(companyId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        positions = result.data,
                        isLoadingPositions = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPositions = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPositions = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun startEditEmployee(employee: CompanyEmployee) {
        _uiState.value = _uiState.value.copy(editingEmployee = employee)
    }

    fun cancelEditEmployee() {
        _uiState.value = _uiState.value.copy(editingEmployee = null)
    }

    fun updateEmployee(employeeId: Int, title: String?, department: String?, hireDate: String?, isAdmin: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingEmployee = true, error = null)
            when (val result = companyRepository.updateCompanyEmployee(companyId, employeeId, title, department, hireDate, isAdmin)) {
                is BreakroomResult.Success -> {
                    // Update the employee in the list
                    val updatedEmployees = _uiState.value.employees.map { emp ->
                        if (emp.id == employeeId) result.data else emp
                    }
                    _uiState.value = _uiState.value.copy(
                        employees = updatedEmployees,
                        isSavingEmployee = false,
                        editingEmployee = null,
                        successMessage = "Employee updated"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSavingEmployee = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isSavingEmployee = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun selectPosition(position: Position?) {
        _uiState.value = _uiState.value.copy(selectedPosition = position)
    }

    fun showCreatePositionDialog() {
        _uiState.value = _uiState.value.copy(showCreatePositionDialog = true)
    }

    fun hideCreatePositionDialog() {
        _uiState.value = _uiState.value.copy(showCreatePositionDialog = false)
    }

    fun createPosition(
        title: String,
        description: String?,
        requirements: String?,
        benefits: String?,
        department: String?,
        employmentType: String?,
        locationType: String?,
        city: String?,
        state: String?,
        payType: String?,
        payRateMin: Double?,
        payRateMax: Double?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingPosition = true, error = null)
            when (val result = companyRepository.createPosition(
                companyId = companyId,
                title = title,
                description = description,
                requirements = requirements,
                benefits = benefits,
                department = department,
                employmentType = employmentType,
                locationType = locationType,
                city = city,
                state = state,
                payType = payType,
                payRateMin = payRateMin,
                payRateMax = payRateMax
            )) {
                is BreakroomResult.Success -> {
                    // Add the new position to the list
                    val updatedPositions = _uiState.value.positions + result.data
                    _uiState.value = _uiState.value.copy(
                        positions = updatedPositions,
                        isCreatingPosition = false,
                        showCreatePositionDialog = false,
                        successMessage = "Position created"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isCreatingPosition = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isCreatingPosition = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun deletePosition(positionId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeletingPosition = true, error = null)
            when (val result = companyRepository.deletePosition(positionId)) {
                is BreakroomResult.Success -> {
                    // Remove the position from the list
                    val updatedPositions = _uiState.value.positions.filter { it.id != positionId }
                    _uiState.value = _uiState.value.copy(
                        positions = updatedPositions,
                        isDeletingPosition = false,
                        selectedPosition = null,
                        successMessage = "Position deleted"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isDeletingPosition = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isDeletingPosition = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun startEditPosition(position: Position) {
        _uiState.value = _uiState.value.copy(
            editingPosition = position,
            selectedPosition = null
        )
    }

    fun cancelEditPosition() {
        _uiState.value = _uiState.value.copy(editingPosition = null)
    }

    fun updatePosition(
        positionId: Int,
        title: String,
        description: String?,
        requirements: String?,
        benefits: String?,
        department: String?,
        employmentType: String?,
        locationType: String?,
        city: String?,
        state: String?,
        country: String?,
        payType: String?,
        payRateMin: Double?,
        payRateMax: Double?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdatingPosition = true, error = null)
            when (val result = companyRepository.updatePosition(
                positionId = positionId,
                title = title,
                description = description,
                requirements = requirements,
                benefits = benefits,
                department = department,
                employmentType = employmentType,
                locationType = locationType,
                city = city,
                state = state,
                country = country,
                payType = payType,
                payRateMin = payRateMin,
                payRateMax = payRateMax
            )) {
                is BreakroomResult.Success -> {
                    // Update the position in the list
                    val updatedPositions = _uiState.value.positions.map { pos ->
                        if (pos.id == positionId) result.data else pos
                    }
                    _uiState.value = _uiState.value.copy(
                        positions = updatedPositions,
                        isUpdatingPosition = false,
                        editingPosition = null,
                        successMessage = "Position updated"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdatingPosition = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdatingPosition = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }
}
