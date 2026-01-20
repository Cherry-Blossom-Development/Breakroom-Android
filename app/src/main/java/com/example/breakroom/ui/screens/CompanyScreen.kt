package com.example.breakroom.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.breakroom.data.models.Company
import com.example.breakroom.data.models.CompanyEmployee
import com.example.breakroom.data.models.Position
import com.example.breakroom.data.models.Project

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
                text = { Text("Recruit") },
                icon = { Icon(Icons.Outlined.PersonSearch, contentDescription = null) }
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
                        isLoading = uiState.isLoadingEmployees,
                        editingEmployee = uiState.editingEmployee,
                        isSaving = uiState.isSavingEmployee,
                        onEditClick = { viewModel.startEditEmployee(it) },
                        onDismissEdit = { viewModel.cancelEditEmployee() },
                        onSaveEdit = { employeeId, title, department, hireDate, isAdmin ->
                            viewModel.updateEmployee(employeeId, title, department, hireDate, isAdmin)
                        }
                    )
                    CompanyTab.POSITIONS -> CompanyPositionsTab(
                        positions = uiState.positions,
                        isLoading = uiState.isLoadingPositions,
                        selectedPosition = uiState.selectedPosition,
                        editingPosition = uiState.editingPosition,
                        showCreateDialog = uiState.showCreatePositionDialog,
                        isCreating = uiState.isCreatingPosition,
                        isDeleting = uiState.isDeletingPosition,
                        isUpdating = uiState.isUpdatingPosition,
                        onPositionClick = { viewModel.selectPosition(it) },
                        onDismissDetail = { viewModel.selectPosition(null) },
                        onCreateClick = { viewModel.showCreatePositionDialog() },
                        onDismissCreate = { viewModel.hideCreatePositionDialog() },
                        onCreatePosition = { title, description, requirements, benefits, department, employmentType, locationType, city, state, payType, payRateMin, payRateMax ->
                            viewModel.createPosition(title, description, requirements, benefits, department, employmentType, locationType, city, state, payType, payRateMin, payRateMax)
                        },
                        onDeletePosition = { viewModel.deletePosition(it) },
                        onEditClick = { viewModel.startEditPosition(it) },
                        onDismissEdit = { viewModel.cancelEditPosition() },
                        onUpdatePosition = { positionId, title, description, requirements, benefits, department, employmentType, locationType, city, state, country, payType, payRateMin, payRateMax ->
                            viewModel.updatePosition(positionId, title, description, requirements, benefits, department, employmentType, locationType, city, state, country, payType, payRateMin, payRateMax)
                        }
                    )
                    CompanyTab.PROJECTS -> CompanyProjectsTab(
                        projects = uiState.projects,
                        isLoading = uiState.isLoadingProjects,
                        showCreateDialog = uiState.showCreateProjectDialog,
                        isCreating = uiState.isCreatingProject,
                        editingProject = uiState.editingProject,
                        isUpdating = uiState.isUpdatingProject,
                        onCreateClick = { viewModel.showCreateProjectDialog() },
                        onDismissCreate = { viewModel.hideCreateProjectDialog() },
                        onCreateProject = { title, description, isPublic ->
                            viewModel.createProject(title, description, isPublic)
                        },
                        onEditClick = { viewModel.startEditProject(it) },
                        onDismissEdit = { viewModel.cancelEditProject() },
                        onUpdateProject = { projectId, title, description, isPublic, isActive ->
                            viewModel.updateProject(projectId, title, description, isPublic, isActive)
                        }
                    )
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
    isLoading: Boolean,
    editingEmployee: CompanyEmployee?,
    isSaving: Boolean,
    onEditClick: (CompanyEmployee) -> Unit,
    onDismissEdit: () -> Unit,
    onSaveEdit: (employeeId: Int, title: String?, department: String?, hireDate: String?, isAdmin: Boolean) -> Unit
) {
    // Show edit dialog if editing an employee
    if (editingEmployee != null) {
        EditEmployeeDialog(
            employee = editingEmployee,
            isSaving = isSaving,
            onDismiss = onDismissEdit,
            onSave = { title, department, hireDate, isAdmin ->
                onSaveEdit(editingEmployee.id, title, department, hireDate, isAdmin)
            }
        )
    }

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
                    EmployeeCard(
                        employee = employee,
                        onEditClick = { onEditClick(employee) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeCard(
    employee: CompanyEmployee,
    onEditClick: () -> Unit
) {
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

            // Edit button
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit employee",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EditEmployeeDialog(
    employee: CompanyEmployee,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String?, department: String?, hireDate: String?, isAdmin: Boolean) -> Unit
) {
    var title by remember(employee.id) { mutableStateOf(employee.title ?: "") }
    var department by remember(employee.id) { mutableStateOf(employee.department ?: "") }
    var hireDate by remember(employee.id) { mutableStateOf(employee.hire_date ?: "") }
    var isAdmin by remember(employee.id) { mutableStateOf(employee.isAdmin) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text("Edit Team Member")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Employee info (read-only)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!employee.photo_url.isNullOrBlank()) {
                        AsyncImage(
                            model = employee.photo_url,
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = employee.initials,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = employee.fullName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "@${employee.handle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Job Title") },
                    placeholder = { Text("e.g., Software Engineer") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving
                )

                // Department field
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Department") },
                    placeholder = { Text("e.g., Engineering") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving
                )

                // Hire Date field
                OutlinedTextField(
                    value = hireDate,
                    onValueChange = { hireDate = it },
                    label = { Text("Hire Date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving
                )

                // Admin toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Administrator",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Can manage employees and settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAdmin,
                        onCheckedChange = { isAdmin = it },
                        enabled = !isSaving && !employee.isOwner
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title.ifBlank { null },
                        department.ifBlank { null },
                        hireDate.ifBlank { null },
                        isAdmin
                    )
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CompanyPositionsTab(
    positions: List<Position>,
    isLoading: Boolean,
    selectedPosition: Position?,
    editingPosition: Position?,
    showCreateDialog: Boolean,
    isCreating: Boolean,
    isDeleting: Boolean,
    isUpdating: Boolean,
    onPositionClick: (Position) -> Unit,
    onDismissDetail: () -> Unit,
    onCreateClick: () -> Unit,
    onDismissCreate: () -> Unit,
    onCreatePosition: (
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
    ) -> Unit,
    onDeletePosition: (Int) -> Unit,
    onEditClick: (Position) -> Unit,
    onDismissEdit: () -> Unit,
    onUpdatePosition: (
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
    ) -> Unit
) {
    // Position Detail Dialog
    if (selectedPosition != null) {
        PositionDetailDialog(
            position = selectedPosition,
            isDeleting = isDeleting,
            onDismiss = onDismissDetail,
            onDelete = { onDeletePosition(selectedPosition.id) },
            onEdit = { onEditClick(selectedPosition) }
        )
    }

    // Edit Position Dialog
    if (editingPosition != null) {
        EditPositionDialog(
            position = editingPosition,
            isUpdating = isUpdating,
            onDismiss = onDismissEdit,
            onUpdate = onUpdatePosition
        )
    }

    // Create Position Dialog
    if (showCreateDialog) {
        CreatePositionDialog(
            isCreating = isCreating,
            onDismiss = onDismissCreate,
            onCreate = onCreatePosition
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            positions.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.PersonSearch,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No open positions",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create positions to start recruiting",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(positions) { position ->
                        PositionCard(
                            position = position,
                            onClick = { onPositionClick(position) }
                        )
                    }
                }
            }
        }

        // FAB for creating new position
        FloatingActionButton(
            onClick = onCreateClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Create Position"
            )
        }
    }
}

@Composable
private fun PositionCard(
    position: Position,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: Title and Pay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = position.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = position.formattedPay,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Department if available
            if (!position.department.isNullOrBlank()) {
                Text(
                    text = position.department,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Meta tags row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (position.formattedEmploymentType.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(position.formattedEmploymentType, style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                if (position.formattedLocationType.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(position.formattedLocationType, style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
                Text(
                    text = position.locationString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Description preview
            if (position.descriptionPreview.isNotBlank()) {
                Text(
                    text = position.descriptionPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PositionDetailDialog(
    position: Position,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isDeleting) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Text(
                    text = position.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (!position.department.isNullOrBlank()) {
                    Text(
                        text = position.department,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Meta info card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            PositionMetaRow("Employment Type", position.formattedEmploymentType)
                            PositionMetaRow("Location Type", position.formattedLocationType)
                            PositionMetaRow("Location", position.locationString)
                            PositionMetaRow("Compensation", position.formattedPay, isPay = true)
                        }
                    }

                    // Description
                    if (!position.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = position.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Requirements
                    if (!position.requirements.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Requirements",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = position.requirements,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Benefits
                    if (!position.benefits.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Benefits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = position.benefits,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // Footer
                Divider(modifier = Modifier.padding(vertical = 12.dp))

                if (showDeleteConfirm) {
                    // Delete confirmation
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Delete this position?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "This action cannot be undone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                TextButton(
                                    onClick = { showDeleteConfirm = false },
                                    enabled = !isDeleting
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = onDelete,
                                    enabled = !isDeleting,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    if (isDeleting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onError
                                        )
                                    } else {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onEdit) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }
                            Button(onClick = onDismiss) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionMetaRow(
    label: String,
    value: String,
    isPay: Boolean = false
) {
    if (value.isBlank()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isPay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CompanyProjectsTab(
    projects: List<Project>,
    isLoading: Boolean,
    showCreateDialog: Boolean,
    isCreating: Boolean,
    editingProject: Project?,
    isUpdating: Boolean,
    onCreateClick: () -> Unit,
    onDismissCreate: () -> Unit,
    onCreateProject: (title: String, description: String?, isPublic: Boolean) -> Unit,
    onEditClick: (Project) -> Unit,
    onDismissEdit: () -> Unit,
    onUpdateProject: (projectId: Int, title: String, description: String?, isPublic: Boolean, isActive: Boolean) -> Unit
) {
    // Create Project Dialog
    if (showCreateDialog) {
        CreateProjectDialog(
            isCreating = isCreating,
            onDismiss = onDismissCreate,
            onCreate = onCreateProject
        )
    }

    // Edit Project Dialog
    if (editingProject != null) {
        EditProjectDialog(
            project = editingProject,
            isUpdating = isUpdating,
            onDismiss = onDismissEdit,
            onUpdate = onUpdateProject
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            projects.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No projects yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create projects to organize tickets",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(projects) { project ->
                        ProjectCard(
                            project = project,
                            onEditClick = { onEditClick(project) }
                        )
                    }
                }
            }
        }

        // FAB for creating new project
        FloatingActionButton(
            onClick = onCreateClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Create Project"
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: Title and Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Default badge
                if (project.isDefault) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text("Default", style = MaterialTheme.typography.labelSmall)
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Public/Private badge
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            if (project.isPublic) "Public" else "Private",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (project.isPublic)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                // Active/Inactive badge
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            if (project.isActive) "Active" else "Inactive",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (project.isActive)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                )
            }

            // Description
            if (!project.description.isNullOrBlank()) {
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ticket count
            Text(
                text = project.ticketCountText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // View Tickets button
                Button(
                    onClick = { /* TODO: Navigate to project tickets */ },
                    modifier = Modifier.weight(1f),
                    enabled = project.isActive
                ) {
                    Icon(
                        imageVector = Icons.Outlined.List,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View Tickets")
                }

                // Edit button (only for non-default projects)
                if (!project.isDefault) {
                    OutlinedButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePositionDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
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
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var requirements by remember { mutableStateOf("") }
    var benefits by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var employmentType by remember { mutableStateOf("") }
    var locationType by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var payType by remember { mutableStateOf("") }
    var payRateMin by remember { mutableStateOf("") }
    var payRateMax by remember { mutableStateOf("") }

    var employmentTypeExpanded by remember { mutableStateOf(false) }
    var locationTypeExpanded by remember { mutableStateOf(false) }
    var payTypeExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isCreating) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Text(
                    text = "Create Position",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title (required)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Job Title *") },
                        placeholder = { Text("e.g., Software Engineer") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isCreating
                    )

                    // Department
                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("Department") },
                        placeholder = { Text("e.g., Engineering") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isCreating
                    )

                    // Employment Type dropdown
                    ExposedDropdownMenuBox(
                        expanded = employmentTypeExpanded,
                        onExpandedChange = { if (!isCreating) employmentTypeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (employmentType.isBlank()) "" else employmentType.split("-").joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Employment Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = employmentTypeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = !isCreating
                        )
                        ExposedDropdownMenu(
                            expanded = employmentTypeExpanded,
                            onDismissRequest = { employmentTypeExpanded = false }
                        ) {
                            listOf("full-time", "part-time", "contract", "internship", "temporary").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.split("-").joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }) },
                                    onClick = {
                                        employmentType = type
                                        employmentTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Location Type dropdown
                    ExposedDropdownMenuBox(
                        expanded = locationTypeExpanded,
                        onExpandedChange = { if (!isCreating) locationTypeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (locationType.isBlank()) "" else locationType.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Location Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationTypeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = !isCreating
                        )
                        ExposedDropdownMenu(
                            expanded = locationTypeExpanded,
                            onDismissRequest = { locationTypeExpanded = false }
                        ) {
                            listOf("remote", "onsite", "hybrid").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        locationType = type
                                        locationTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // City and State row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("City") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isCreating
                        )
                        OutlinedTextField(
                            value = state,
                            onValueChange = { state = it },
                            label = { Text("State") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isCreating
                        )
                    }

                    // Pay Type dropdown
                    ExposedDropdownMenuBox(
                        expanded = payTypeExpanded,
                        onExpandedChange = { if (!isCreating) payTypeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (payType.isBlank()) "" else payType.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pay Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payTypeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = !isCreating
                        )
                        ExposedDropdownMenu(
                            expanded = payTypeExpanded,
                            onDismissRequest = { payTypeExpanded = false }
                        ) {
                            listOf("salary", "hourly").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        payType = type
                                        payTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Pay Rate row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = payRateMin,
                            onValueChange = { payRateMin = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Min Pay") },
                            placeholder = { Text("e.g., 50000") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isCreating
                        )
                        OutlinedTextField(
                            value = payRateMax,
                            onValueChange = { payRateMax = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Max Pay") },
                            placeholder = { Text("e.g., 80000") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isCreating
                        )
                    }

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Describe the role and responsibilities...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        enabled = !isCreating
                    )

                    // Requirements
                    OutlinedTextField(
                        value = requirements,
                        onValueChange = { requirements = it },
                        label = { Text("Requirements") },
                        placeholder = { Text("List required qualifications...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        enabled = !isCreating
                    )

                    // Benefits
                    OutlinedTextField(
                        value = benefits,
                        onValueChange = { benefits = it },
                        label = { Text("Benefits") },
                        placeholder = { Text("List benefits offered...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        enabled = !isCreating
                    )
                }

                // Footer
                Divider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isCreating
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onCreate(
                                title,
                                description.ifBlank { null },
                                requirements.ifBlank { null },
                                benefits.ifBlank { null },
                                department.ifBlank { null },
                                employmentType.ifBlank { null },
                                locationType.ifBlank { null },
                                city.ifBlank { null },
                                state.ifBlank { null },
                                payType.ifBlank { null },
                                payRateMin.toDoubleOrNull(),
                                payRateMax.toDoubleOrNull()
                            )
                        },
                        enabled = !isCreating && title.isNotBlank()
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPositionDialog(
    position: Position,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (
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
    ) -> Unit
) {
    var title by remember { mutableStateOf(position.title) }
    var description by remember { mutableStateOf(position.description ?: "") }
    var requirements by remember { mutableStateOf(position.requirements ?: "") }
    var benefits by remember { mutableStateOf(position.benefits ?: "") }
    var department by remember { mutableStateOf(position.department ?: "") }
    var employmentType by remember { mutableStateOf(position.employment_type ?: "") }
    var locationType by remember { mutableStateOf(position.location_type ?: "") }
    var city by remember { mutableStateOf(position.city ?: "") }
    var state by remember { mutableStateOf(position.state ?: "") }
    var country by remember { mutableStateOf(position.country ?: "") }
    var payType by remember { mutableStateOf(position.pay_type ?: "") }
    var payRateMin by remember { mutableStateOf(position.pay_rate_min?.toString() ?: "") }
    var payRateMax by remember { mutableStateOf(position.pay_rate_max?.toString() ?: "") }

    var employmentTypeExpanded by remember { mutableStateOf(false) }
    var locationTypeExpanded by remember { mutableStateOf(false) }
    var payTypeExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isUpdating) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Text(
                    text = "Edit Position",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title (required)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Job Title *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isUpdating
                    )

                    // Department
                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("Department") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isUpdating
                    )

                    // Employment Type dropdown
                    ExposedDropdownMenuBox(
                        expanded = employmentTypeExpanded,
                        onExpandedChange = { if (!isUpdating) employmentTypeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (employmentType.isBlank()) "" else employmentType.split("-").joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Employment Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = employmentTypeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = !isUpdating
                        )
                        ExposedDropdownMenu(
                            expanded = employmentTypeExpanded,
                            onDismissRequest = { employmentTypeExpanded = false }
                        ) {
                            listOf("full-time", "part-time", "contract", "internship", "temporary").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.split("-").joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }) },
                                    onClick = {
                                        employmentType = type
                                        employmentTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Location Type dropdown
                    ExposedDropdownMenuBox(
                        expanded = locationTypeExpanded,
                        onExpandedChange = { if (!isUpdating) locationTypeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (locationType.isBlank()) "" else locationType.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Location Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationTypeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = !isUpdating
                        )
                        ExposedDropdownMenu(
                            expanded = locationTypeExpanded,
                            onDismissRequest = { locationTypeExpanded = false }
                        ) {
                            listOf("remote", "onsite", "hybrid").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        locationType = type
                                        locationTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // City, State, Country row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("City") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isUpdating
                        )
                        OutlinedTextField(
                            value = state,
                            onValueChange = { state = it },
                            label = { Text("State") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isUpdating
                        )
                    }

                    // Country
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isUpdating
                    )

                    // Pay Type dropdown
                    ExposedDropdownMenuBox(
                        expanded = payTypeExpanded,
                        onExpandedChange = { if (!isUpdating) payTypeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (payType.isBlank()) "" else payType.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pay Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payTypeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = !isUpdating
                        )
                        ExposedDropdownMenu(
                            expanded = payTypeExpanded,
                            onDismissRequest = { payTypeExpanded = false }
                        ) {
                            listOf("salary", "hourly").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        payType = type
                                        payTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Pay Rate row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = payRateMin,
                            onValueChange = { payRateMin = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Min Pay") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isUpdating
                        )
                        OutlinedTextField(
                            value = payRateMax,
                            onValueChange = { payRateMax = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Max Pay") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isUpdating
                        )
                    }

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        enabled = !isUpdating
                    )

                    // Requirements
                    OutlinedTextField(
                        value = requirements,
                        onValueChange = { requirements = it },
                        label = { Text("Requirements") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        enabled = !isUpdating
                    )

                    // Benefits
                    OutlinedTextField(
                        value = benefits,
                        onValueChange = { benefits = it },
                        label = { Text("Benefits") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        enabled = !isUpdating
                    )
                }

                // Footer
                Divider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isUpdating
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onUpdate(
                                position.id,
                                title,
                                description.ifBlank { null },
                                requirements.ifBlank { null },
                                benefits.ifBlank { null },
                                department.ifBlank { null },
                                employmentType.ifBlank { null },
                                locationType.ifBlank { null },
                                city.ifBlank { null },
                                state.ifBlank { null },
                                country.ifBlank { null },
                                payType.ifBlank { null },
                                payRateMin.toDoubleOrNull(),
                                payRateMax.toDoubleOrNull()
                            )
                        },
                        enabled = !isUpdating && title.isNotBlank()
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String?, isPublic: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isCreating) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Text(
                    text = "Add New Project",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Form fields
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title (required)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Project Title *") },
                        placeholder = { Text("e.g., Mobile App Development") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isCreating
                    )

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Project description...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 4,
                        enabled = !isCreating
                    )

                    // Public toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Public Project",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Public projects are visible to all employees. Private projects are only visible to assigned members.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPublic,
                            onCheckedChange = { isPublic = it },
                            enabled = !isCreating
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Footer buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isCreating
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onCreate(
                                title,
                                description.ifBlank { null },
                                isPublic
                            )
                        },
                        enabled = !isCreating && title.isNotBlank()
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Create Project")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditProjectDialog(
    project: Project,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (projectId: Int, title: String, description: String?, isPublic: Boolean, isActive: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(project.title) }
    var description by remember { mutableStateOf(project.description ?: "") }
    var isPublic by remember { mutableStateOf(project.isPublic) }
    var isActive by remember { mutableStateOf(project.isActive) }

    Dialog(onDismissRequest = { if (!isUpdating) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Text(
                    text = "Edit Project",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Form fields
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title (required)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Project Title *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isUpdating
                    )

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Project description...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 4,
                        enabled = !isUpdating
                    )

                    // Public toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Public Project",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Visible to all employees",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPublic,
                            onCheckedChange = { isPublic = it },
                            enabled = !isUpdating
                        )
                    }

                    // Active toggle (cannot deactivate default project)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (project.isDefault) "Default project cannot be deactivated" else "Inactive projects are hidden",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                            enabled = !isUpdating && !project.isDefault
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Footer buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isUpdating
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onUpdate(
                                project.id,
                                title,
                                description.ifBlank { null },
                                isPublic,
                                isActive
                            )
                        },
                        enabled = !isUpdating && title.isNotBlank()
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
