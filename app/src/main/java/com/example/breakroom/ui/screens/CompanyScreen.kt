package com.example.breakroom.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.example.breakroom.data.models.Company
import com.example.breakroom.data.models.CompanyEmployee

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyScreen(
    viewModel: CompanyViewModel,
    companyName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = companyName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // Tabs
        TabRow(
            selectedTabIndex = uiState.activeTab.ordinal
        ) {
            Tab(
                selected = uiState.activeTab == CompanyTab.INFO,
                onClick = { viewModel.setActiveTab(CompanyTab.INFO) },
                text = { Text("Info") },
                icon = { Icon(Icons.Outlined.Info, contentDescription = null) }
            )
            Tab(
                selected = uiState.activeTab == CompanyTab.EMPLOYEES,
                onClick = { viewModel.setActiveTab(CompanyTab.EMPLOYEES) },
                text = { Text("Team") },
                icon = { Icon(Icons.Outlined.People, contentDescription = null) }
            )
            Tab(
                selected = uiState.activeTab == CompanyTab.POSITIONS,
                onClick = { viewModel.setActiveTab(CompanyTab.POSITIONS) },
                text = { Text("Jobs") },
                icon = { Icon(Icons.Outlined.Work, contentDescription = null) }
            )
            Tab(
                selected = uiState.activeTab == CompanyTab.PROJECTS,
                onClick = { viewModel.setActiveTab(CompanyTab.PROJECTS) },
                text = { Text("Projects") },
                icon = { Icon(Icons.Outlined.Folder, contentDescription = null) }
            )
        }

        // Error message
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                when (uiState.activeTab) {
                    CompanyTab.INFO -> CompanyInfoTab(company = uiState.company)
                    CompanyTab.EMPLOYEES -> CompanyEmployeesTab(
                        employees = uiState.employees,
                        isLoading = uiState.isLoadingEmployees
                    )
                    CompanyTab.POSITIONS -> CompanyPositionsTab()
                    CompanyTab.PROJECTS -> CompanyProjectsTab()
                }
            }
        }
    }
}

@Composable
private fun CompanyInfoTab(company: Company?) {
    val context = LocalContext.current

    if (company == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Company information not available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Description section
        if (!company.description.isNullOrBlank()) {
            InfoSection(title = "About") {
                Text(
                    text = company.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Contact Information section
        val hasContactInfo = !company.phone.isNullOrBlank() ||
                            !company.email.isNullOrBlank() ||
                            !company.website.isNullOrBlank()

        if (hasContactInfo) {
            InfoSection(title = "Contact Information") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Phone
                    if (!company.phone.isNullOrBlank()) {
                        InfoRow(
                            icon = Icons.Outlined.Phone,
                            label = "Phone",
                            value = company.phone,
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${company.phone}")
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    // Email
                    if (!company.email.isNullOrBlank()) {
                        InfoRow(
                            icon = Icons.Outlined.Email,
                            label = "Email",
                            value = company.email,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:${company.email}")
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    // Website
                    if (!company.website.isNullOrBlank()) {
                        InfoRow(
                            icon = Icons.Outlined.Language,
                            label = "Website",
                            value = company.website,
                            onClick = {
                                val url = if (company.website.startsWith("http")) {
                                    company.website
                                } else {
                                    "https://${company.website}"
                                }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Location section
        val hasLocation = !company.address.isNullOrBlank() ||
                         !company.city.isNullOrBlank() ||
                         !company.state.isNullOrBlank() ||
                         !company.country.isNullOrBlank()

        if (hasLocation) {
            InfoSection(title = "Location") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!company.address.isNullOrBlank()) {
                        Text(
                            text = company.address,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    val cityStateZip = buildString {
                        if (!company.city.isNullOrBlank()) append(company.city)
                        if (!company.state.isNullOrBlank()) {
                            if (isNotEmpty()) append(", ")
                            append(company.state)
                        }
                        if (!company.postal_code.isNullOrBlank()) {
                            if (isNotEmpty()) append(" ")
                            append(company.postal_code)
                        }
                    }
                    if (cityStateZip.isNotBlank()) {
                        Text(
                            text = cityStateZip,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (!company.country.isNullOrBlank()) {
                        Text(
                            text = company.country,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (onClick != null) TextDecoration.Underline else TextDecoration.None
            )
        }
    }
}

@Composable
private fun CompanyEmployeesTab(
    employees: List<CompanyEmployee>,
    isLoading: Boolean
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
        employees.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.People,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No team members yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(employees) { employee ->
                    EmployeeCard(employee = employee)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeCard(employee: CompanyEmployee) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            if (!employee.photo_url.isNullOrBlank()) {
                AsyncImage(
                    model = employee.photo_url,
                    contentDescription = "Profile photo",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Initials avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = employee.initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = employee.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Role badges
                    if (employee.isOwner) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("Owner", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (employee.isAdmin) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                            Text("Admin", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Text(
                    text = "@${employee.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!employee.title.isNullOrBlank()) {
                    Text(
                        text = employee.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanyPositionsTab() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Open Positions",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Open positions will be displayed here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompanyProjectsTab() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Projects",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Projects will be displayed here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
