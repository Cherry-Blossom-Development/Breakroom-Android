package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cherryblossomdev.breakroom.data.models.Company
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyPortalScreen(
    viewModel: CompanyPortalViewModel,
    onNavigateToCompany: (Company) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.newCompanyForm.collectAsState()
    var createExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen-company-portal"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── My Companies ──────────────────────────────────────────────────────
        item {
            Text(
                text = "My Companies",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        when {
            uiState.isLoadingMyCompanies -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            uiState.myCompanies.isEmpty() -> item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "You are not associated with any companies yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> items(uiState.myCompanies) { company ->
                MyCompanyCard(company = company, onClick = { onNavigateToCompany(company) })
            }
        }

        // ── Find a Company ────────────────────────────────────────────────────
        item { Divider(modifier = Modifier.padding(vertical = 4.dp)) }
        item {
            Text(
                text = "Find a Company",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search companies by name...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
        }
        when {
            uiState.isSearching -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            uiState.searchQuery.length >= 2 && uiState.searchResults.isEmpty() -> item {
                Text(
                    text = "No companies found matching \"${uiState.searchQuery}\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            uiState.searchResults.isNotEmpty() -> items(uiState.searchResults) { company ->
                CompanyCard(company = company, onClick = { onNavigateToCompany(company) })
            }
        }

        // ── Create a Company ──────────────────────────────────────────────────
        item { Divider(modifier = Modifier.padding(vertical = 4.dp)) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { createExpanded = !createExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create a Company",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (createExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (createExpanded) "Collapse" else "Expand"
                )
            }
        }
        if (createExpanded) {
            item {
                CreateCompanyForm(
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

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun CompanyCard(company: Company, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCompanyForm(
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = form.email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
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

        FormSection(title = "Location") {
            OutlinedTextField(
                value = form.address,
                onValueChange = onAddressChange,
                label = { Text("Address") },
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
        }

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
        }

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
