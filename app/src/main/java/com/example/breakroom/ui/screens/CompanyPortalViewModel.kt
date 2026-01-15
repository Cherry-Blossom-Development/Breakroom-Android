package com.example.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.CompanyRepository
import com.example.breakroom.data.models.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CompanyPortalTab {
    SEARCH, MY_COMPANIES, CREATE
}

data class CompanyPortalUiState(
    val activeTab: CompanyPortalTab = CompanyPortalTab.SEARCH,
    // Search state
    val searchQuery: String = "",
    val searchResults: List<Company> = emptyList(),
    val isSearching: Boolean = false,
    // My companies state
    val myCompanies: List<Company> = emptyList(),
    val isLoadingMyCompanies: Boolean = false,
    // Create company state
    val isCreating: Boolean = false,
    val createError: String? = null,
    val createSuccess: String? = null,
    // General
    val error: String? = null
)

data class NewCompanyForm(
    val name: String = "",
    val description: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val postalCode: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val employeeTitle: String = ""
)

class CompanyPortalViewModel(
    private val companyRepository: CompanyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanyPortalUiState())
    val uiState: StateFlow<CompanyPortalUiState> = _uiState.asStateFlow()

    private val _newCompanyForm = MutableStateFlow(NewCompanyForm())
    val newCompanyForm: StateFlow<NewCompanyForm> = _newCompanyForm.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadMyCompanies()
    }

    fun setActiveTab(tab: CompanyPortalTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        // Debounce search
        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                delay(300) // Debounce
                searchCompanies(query)
            }
        } else {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }

    private suspend fun searchCompanies(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true)

        when (val result = companyRepository.searchCompanies(query)) {
            is BreakroomResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    searchResults = result.data,
                    isSearching = false
                )
            }
            is BreakroomResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = result.message
                )
            }
            is BreakroomResult.AuthenticationError -> {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = "Session expired - please log in again"
                )
            }
        }
    }

    fun loadMyCompanies() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMyCompanies = true)

            when (val result = companyRepository.getMyCompanies()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        myCompanies = result.data,
                        isLoadingMyCompanies = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMyCompanies = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMyCompanies = false,
                        error = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    // Form field setters
    fun updateFormName(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(name = value)
    }

    fun updateFormDescription(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(description = value)
    }

    fun updateFormAddress(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(address = value)
    }

    fun updateFormCity(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(city = value)
    }

    fun updateFormState(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(state = value)
    }

    fun updateFormCountry(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(country = value)
    }

    fun updateFormPostalCode(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(postalCode = value)
    }

    fun updateFormPhone(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(phone = value)
    }

    fun updateFormEmail(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(email = value)
    }

    fun updateFormWebsite(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(website = value)
    }

    fun updateFormEmployeeTitle(value: String) {
        _newCompanyForm.value = _newCompanyForm.value.copy(employeeTitle = value)
    }

    fun createCompany() {
        val form = _newCompanyForm.value

        if (form.name.isBlank()) {
            _uiState.value = _uiState.value.copy(createError = "Company name is required")
            return
        }

        if (form.employeeTitle.isBlank()) {
            _uiState.value = _uiState.value.copy(createError = "Your title/role is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, createError = null)

            when (val result = companyRepository.createCompany(
                name = form.name,
                description = form.description.ifBlank { null },
                address = form.address.ifBlank { null },
                city = form.city.ifBlank { null },
                state = form.state.ifBlank { null },
                country = form.country.ifBlank { null },
                postalCode = form.postalCode.ifBlank { null },
                phone = form.phone.ifBlank { null },
                email = form.email.ifBlank { null },
                website = form.website.ifBlank { null },
                employeeTitle = form.employeeTitle
            )) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createSuccess = "Company \"${result.data.name}\" created successfully!"
                    )
                    // Reset form
                    _newCompanyForm.value = NewCompanyForm()
                    // Refresh my companies
                    loadMyCompanies()
                    // Switch to my companies tab after delay
                    delay(2000)
                    _uiState.value = _uiState.value.copy(
                        activeTab = CompanyPortalTab.MY_COMPANIES,
                        createSuccess = null
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createError = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createError = "Session expired - please log in again"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, createError = null)
    }

    fun clearCreateSuccess() {
        _uiState.value = _uiState.value.copy(createSuccess = null)
    }
}
