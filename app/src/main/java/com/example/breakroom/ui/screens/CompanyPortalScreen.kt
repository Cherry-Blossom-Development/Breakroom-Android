package com.example.breakroom.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.breakroom.data.models.Company

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyPortalScreen(
    viewModel: CompanyPortalViewModel,
    onNavigateToCompany: (Company) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.newCompanyForm.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Company Portal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs
        TabRow(
            selectedTabIndex = uiState.activeTab.ordinal
        ) {
            Tab(
                selected = uiState.activeTab == CompanyPortalTab.SEARCH,
                onClick = { viewModel.setActiveTab(CompanyPortalTab.SEARCH) },
                text = { Text("Search") }
            )
            Tab(
                selected = uiState.activeTab == CompanyPortalTab.MY_COMPANIES,
                onClick = { viewModel.setActiveTab(CompanyPortalTab.MY_COMPANIES) },
                text = { Text("My Companies (${uiState.myCompanies.size})") }
            )
            Tab(
                selected = uiState.activeTab == CompanyPortalTab.CREATE,
                onClick = { viewModel.setActiveTab(CompanyPortalTab.CREATE) },
                text = { Text("Create") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Content
        when (uiState.activeTab) {
            CompanyPortalTab.SEARCH -> SearchTab(
                searchQuery = uiState.searchQuery,
                searchResults = uiState.searchResults,
                isSearching = uiState.isSearching,
                onSearchQueryChange = viewModel::setSearchQuery
            )
            CompanyPortalTab.MY_COMPANIES -> MyCompaniesTab(
                companies = uiState.myCompanies,
                isLoading = uiState.isLoadingMyCompanies,
                onRefresh = viewModel::loadMyCompanies,
                onCompanyClick = onNavigateToCompany
            )
            CompanyPortalTab.CREATE -> CreateCompanyTab(
                form = form,
                isCreating = uiState.isCreating,
                createError = uiState.createError,
                createSuccess = uiState.createSuccess,
                onNameChange = viewModel::updateFormName,
                onDescriptionChange = viewModel::updateFormDescription,
                onAddressChange = viewModel::updateFormAddress,
                onCityChange = viewModel::updateFormCity,
                onStateChange = viewModel::updateFormState,
                onCountryChange = viewModel::updateFormCountry,
                onPostalCodeChange = viewModel::updateFormPostalCode,
                onPhoneChange = viewModel::updateFormPhone,
                onEmailChange = viewModel::updateFormEmail,
                onWebsiteChange = viewModel::updateFormWebsite,
                onEmployeeTitleChange = viewModel::updateFormEmployeeTitle,
                onSubmit = viewModel::createCompany
            )
        }
    }
}

@Composable
private fun SearchTab(
    searchQuery: String,
    searchResults: List<Company>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search companies by name...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isSearching -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            searchQuery.length >= 2 && searchResults.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No companies found matching \"$searchQuery\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            searchResults.isNotEmpty() -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults) { company ->
                        CompanyCard(company = company)
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Business,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Enter at least 2 characters to search",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MyCompaniesTab(
    companies: List<Company>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onCompanyClick: (Company) -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        companies.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Business,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "You are not associated with any companies yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Search for a company to join or create a new one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(companies) { company ->
                    MyCompanyCard(
                        company = company,
                        onClick = { onCompanyClick(company) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanyCard(company: Company) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Navigate to company detail */ },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = company.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = company.locationString,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!company.description.isNullOrBlank()) {
                Text(
                    text = company.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun MyCompanyCard(
    company: Company,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (company.isOwner) {
                        RoleBadge(text = "Owner", color = MaterialTheme.colorScheme.primary)
                    } else if (company.isAdmin) {
                        RoleBadge(text = "Admin", color = Color(0xFF667EEA))
                    }
                }
            }
            if (!company.title.isNullOrBlank()) {
                Text(
                    text = company.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = company.locationString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun RoleBadge(text: String, color: Color) {
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CreateCompanyTab(
    form: NewCompanyForm,
    isCreating: Boolean,
    createError: String?,
    createSuccess: String?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onStateChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onPostalCodeChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onWebsiteChange: (String) -> Unit,
    onEmployeeTitleChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Create a New Company",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Company Information Section
        FormSection(title = "Company Information") {
            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = { Text("Company Name *") },
                placeholder = { Text("Enter company name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = form.description,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                placeholder = { Text("Brief description of the company") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = form.phone,
                    onValueChange = onPhoneChange,
                    label = { Text("Phone") },
                    placeholder = { Text("Phone number") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = form.email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    placeholder = { Text("Contact email") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = form.website,
                onValueChange = onWebsiteChange,
                label = { Text("Website") },
                placeholder = { Text("https://...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Location Section
        FormSection(title = "Location") {
            OutlinedTextField(
                value = form.address,
                onValueChange = onAddressChange,
                label = { Text("Address") },
                placeholder = { Text("Street address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = form.city,
                    onValueChange = onCityChange,
                    label = { Text("City") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = form.state,
                    onValueChange = onStateChange,
                    label = { Text("State/Province") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = form.country,
                    onValueChange = onCountryChange,
                    label = { Text("Country") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = form.postalCode,
                    onValueChange = onPostalCodeChange,
                    label = { Text("Postal Code") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Your Role Section
        FormSection(title = "Your Role") {
            Text(
                text = "As the creator, you will be the owner of this company.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = form.employeeTitle,
                onValueChange = onEmployeeTitleChange,
                label = { Text("Your Title/Position *") },
                placeholder = { Text("e.g., CEO, Founder, President") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Error message
        if (createError != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = createError,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Success message
        if (createSuccess != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF28A745).copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = createSuccess,
                    color = Color(0xFF28A745),
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Submit button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onSubmit,
                enabled = !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isCreating) "Creating..." else "Create Company")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun FormSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
