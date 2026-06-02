package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.CollectionsRepository
import com.cherryblossomdev.breakroom.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== Option constants ====================

private val destinationOptions = listOf(
    "us_only" to "US Only",
    "us_canada" to "US & Canada",
    "worldwide" to "Worldwide"
)

private val processingTimeOptions = listOf(
    "same_day" to "Same day",
    "1_2_days" to "1–2 business days",
    "3_5_days" to "3–5 business days",
    "1_2_weeks" to "1–2 weeks",
    "2_4_weeks" to "2–4 weeks"
)

// ==================== ViewModel ====================

data class CollectionsShippingUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val country: String = "US",
    val destinations: String = "us_only",
    val processingTime: String = "1_2_days"
)

class CollectionsShippingViewModel(
    private val repo: CollectionsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsShippingUiState())
    val uiState: StateFlow<CollectionsShippingUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val r = repo.getShippingSettings()) {
                is BreakroomResult.Success -> {
                    val s = r.data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        addressLine1 = s?.address_line1 ?: "",
                        addressLine2 = s?.address_line2 ?: "",
                        city = s?.city ?: "",
                        state = s?.state ?: "",
                        zip = s?.zip ?: "",
                        country = s?.country ?: "US",
                        destinations = s?.destinations ?: "us_only",
                        processingTime = s?.processing_time ?: "1_2_days"
                    )
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = r.message
                )
                is BreakroomResult.AuthenticationError -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Session expired"
                )
                else -> _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun setAddressLine1(v: String) { _uiState.value = _uiState.value.copy(addressLine1 = v) }
    fun setAddressLine2(v: String) { _uiState.value = _uiState.value.copy(addressLine2 = v) }
    fun setCity(v: String) { _uiState.value = _uiState.value.copy(city = v) }
    fun setState(v: String) { _uiState.value = _uiState.value.copy(state = v) }
    fun setZip(v: String) { _uiState.value = _uiState.value.copy(zip = v) }
    fun setCountry(v: String) { _uiState.value = _uiState.value.copy(country = v) }
    fun setDestinations(v: String) { _uiState.value = _uiState.value.copy(destinations = v) }
    fun setProcessingTime(v: String) { _uiState.value = _uiState.value.copy(processingTime = v) }

    fun save() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            when (val r = repo.saveShippingSettings(
                addressLine1 = s.addressLine1.trim().ifEmpty { null },
                addressLine2 = s.addressLine2.trim().ifEmpty { null },
                city = s.city.trim().ifEmpty { null },
                state = s.state.trim().ifEmpty { null },
                zip = s.zip.trim().ifEmpty { null },
                country = s.country.trim().ifEmpty { null },
                destinations = s.destinations,
                processingTime = s.processingTime
            )) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = "Shipping settings saved"
                )
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isSaving = false, error = r.message
                )
                is BreakroomResult.AuthenticationError -> _uiState.value = _uiState.value.copy(
                    isSaving = false, error = "Session expired"
                )
                else -> _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}

// ==================== Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsShippingScreen(viewModel: CollectionsShippingViewModel, onNavigateBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearSuccess() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shipping Setup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Ship-from address
                SectionHeader("Ship-from Address")

                OutlinedTextField(
                    value = state.addressLine1,
                    onValueChange = viewModel::setAddressLine1,
                    label = { Text("Address line 1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.addressLine2,
                    onValueChange = viewModel::setAddressLine2,
                    label = { Text("Address line 2 (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.city,
                        onValueChange = viewModel::setCity,
                        label = { Text("City") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.state,
                        onValueChange = viewModel::setState,
                        label = { Text("State") },
                        modifier = Modifier.weight(0.5f),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.zip,
                        onValueChange = viewModel::setZip,
                        label = { Text("ZIP") },
                        modifier = Modifier.weight(0.5f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.country,
                        onValueChange = viewModel::setCountry,
                        label = { Text("Country") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Divider()

                // Shipping destinations
                SectionHeader("Shipping Destinations")
                destinationOptions.forEach { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.destinations == key,
                            onClick = { viewModel.setDestinations(key) }
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Divider()

                // Processing time
                SectionHeader("Processing Time")
                processingTimeOptions.forEach { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.processingTime == key,
                            onClick = { viewModel.setProcessingTime(key) }
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary)
}
