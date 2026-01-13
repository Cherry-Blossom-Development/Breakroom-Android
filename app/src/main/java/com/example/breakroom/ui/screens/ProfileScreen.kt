package com.example.breakroom.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.breakroom.data.models.Skill
import com.example.breakroom.data.models.UserJob
import com.example.breakroom.data.models.UserProfile
import com.example.breakroom.network.RetrofitClient
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddJobDialog by remember { mutableStateOf(false) }
    var showDeletePhotoDialog by remember { mutableStateOf(false) }
    var showDeleteJobDialog by remember { mutableStateOf<UserJob?>(null) }
    var skillSearchQuery by remember { mutableStateOf("") }

    // Edit mode form state
    var editFirstName by remember { mutableStateOf("") }
    var editLastName by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    var editWorkBio by remember { mutableStateOf("") }

    // Update edit form when profile loads or edit mode changes
    LaunchedEffect(uiState.profile, uiState.isEditMode) {
        if (uiState.isEditMode) {
            uiState.profile?.let { profile ->
                editFirstName = profile.firstName ?: ""
                editLastName = profile.lastName ?: ""
                editBio = profile.bio ?: ""
                editWorkBio = profile.workBio ?: ""
            }
        }
    }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadPhoto(it) }
    }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!uiState.isEditMode) {
                            OutlinedButton(onClick = { viewModel.setEditMode(true) }) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }
                        IconButton(onClick = { viewModel.loadProfile() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            }

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.profile == null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Failed to load profile")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadProfile() }) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> {
                        val profile = uiState.profile!!
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Profile Header with Photo
                            item {
                                ProfileHeaderSection(
                                    profile = profile,
                                    isEditMode = uiState.isEditMode,
                                    isSaving = uiState.isSaving,
                                    onPhotoClick = { photoPickerLauncher.launch("image/*") },
                                    onDeletePhoto = { showDeletePhotoDialog = true }
                                )
                            }

                            // Edit Mode Form
                            if (uiState.isEditMode) {
                                item {
                                    EditProfileForm(
                                        firstName = editFirstName,
                                        lastName = editLastName,
                                        bio = editBio,
                                        workBio = editWorkBio,
                                        onFirstNameChange = { editFirstName = it },
                                        onLastNameChange = { editLastName = it },
                                        onBioChange = { editBio = it },
                                        onWorkBioChange = { editWorkBio = it },
                                        isSaving = uiState.isSaving,
                                        onSave = {
                                            viewModel.updateProfile(
                                                editFirstName, editLastName, editBio, editWorkBio
                                            )
                                        },
                                        onCancel = { viewModel.setEditMode(false) }
                                    )
                                }
                            } else {
                                // About Section
                                item {
                                    ProfileSection(title = "About") {
                                        Text(
                                            text = profile.bio?.ifBlank { "No bio yet" } ?: "No bio yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (profile.bio.isNullOrBlank())
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // Work Bio Section
                                if (!profile.workBio.isNullOrBlank()) {
                                    item {
                                        ProfileSection(title = "Work Biography") {
                                            Text(
                                                text = profile.workBio!!,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }

                            // Skills Section
                            item {
                                SkillsSection(
                                    skills = profile.skills,
                                    searchQuery = skillSearchQuery,
                                    searchResults = uiState.skillSearchResults,
                                    isSearching = uiState.isSearchingSkills,
                                    onSearchQueryChange = {
                                        skillSearchQuery = it
                                        viewModel.searchSkills(it)
                                    },
                                    onAddSkill = { name ->
                                        viewModel.addSkill(name)
                                        skillSearchQuery = ""
                                    },
                                    onRemoveSkill = { viewModel.removeSkill(it) }
                                )
                            }

                            // Work Experience Section
                            item {
                                WorkExperienceSection(
                                    jobs = profile.jobs,
                                    onAddJob = { showAddJobDialog = true },
                                    onDeleteJob = { showDeleteJobDialog = it }
                                )
                            }

                            // Account Details
                            item {
                                ProfileSection(title = "Account Details") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DetailRow("Email", profile.email ?: "-")
                                        DetailRow("Handle", "@${profile.handle}")
                                        DetailRow("Member since", formatMemberSince(profile.createdAt))
                                        DetailRow("Friends", profile.friendCount.toString())
                                        if (!profile.city.isNullOrBlank()) {
                                            DetailRow("Location", profile.city!!)
                                        }
                                    }
                                }
                            }

                            // Logout Button
                            item {
                                Button(
                                    onClick = { viewModel.logout(onLoggedOut) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Logout")
                                }
                            }

                            item { Spacer(modifier = Modifier.height(32.dp)) }
                        }
                    }
                }
            }
        }
    }

    // Delete Photo Dialog
    if (showDeletePhotoDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePhotoDialog = false },
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to delete your profile photo?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePhoto()
                        showDeletePhotoDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePhotoDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete Job Dialog
    showDeleteJobDialog?.let { job ->
        AlertDialog(
            onDismissRequest = { showDeleteJobDialog = null },
            title = { Text("Delete Job") },
            text = { Text("Are you sure you want to delete \"${job.title}\" at ${job.company}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteJob(job.id)
                        showDeleteJobDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteJobDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Add Job Dialog
    if (showAddJobDialog) {
        AddJobDialog(
            onDismiss = { showAddJobDialog = false },
            onAddJob = { title, company, location, startDate, endDate, isCurrent, description ->
                viewModel.addJob(title, company, location, startDate, endDate, isCurrent, description)
                showAddJobDialog = false
            }
        )
    }
}

@Composable
private fun ProfileHeaderSection(
    profile: UserProfile,
    isEditMode: Boolean,
    isSaving: Boolean,
    onPhotoClick: () -> Unit,
    onDeletePhoto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Photo
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(enabled = !isSaving) { onPhotoClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (!profile.photoPath.isNullOrBlank()) {
                        AsyncImage(
                            model = "${RetrofitClient.BASE_URL}api/uploads/${profile.photoPath}",
                            contentDescription = "Profile photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = profile.initials,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Camera icon overlay
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Change photo",
                        modifier = Modifier.padding(6.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Delete photo button
            if (!profile.photoPath.isNullOrBlank()) {
                TextButton(
                    onClick = onDeletePhoto,
                    enabled = !isSaving
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove photo", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name and handle
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "@${profile.handle}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditProfileForm(
    firstName: String,
    lastName: String,
    bio: String,
    workBio: String,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onWorkBioChange: (String) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = onFirstNameChange,
                    label = { Text("First Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = onLastNameChange,
                    label = { Text("Last Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
            }

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 500) onBioChange(it) },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                supportingText = { Text("${bio.length}/500") }
            )

            OutlinedTextField(
                value = workBio,
                onValueChange = { if (it.length <= 1000) onWorkBioChange(it) },
                label = { Text("Work Biography") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                supportingText = { Text("${workBio.length}/1000") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel, enabled = !isSaving) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave, enabled = !isSaving) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillsSection(
    skills: List<Skill>,
    searchQuery: String,
    searchResults: List<Skill>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onAddSkill: (String) -> Unit,
    onRemoveSkill: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Skills",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Current skills
            if (skills.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(skills, key = { it.id }) { skill ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text(skill.name) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRemoveSkill(skill.id) }
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search or add a skill...") },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        Row {
                            IconButton(onClick = { onAddSkill(searchQuery) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add skill")
                            }
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                }
            )

            // Search results dropdown
            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        searchResults.take(5).forEach { skill ->
                            Text(
                                text = skill.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddSkill(skill.name) }
                                    .padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkExperienceSection(
    jobs: List<UserJob>,
    onAddJob: () -> Unit,
    onDeleteJob: (UserJob) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Work Experience",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAddJob) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Job")
                }
            }

            if (jobs.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No work experience added yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                // Sort: current jobs first, then by start date
                val sortedJobs = jobs.sortedWith(
                    compareByDescending<UserJob> { it.is_current }
                        .thenByDescending { it.start_date }
                )
                sortedJobs.forEach { job ->
                    JobCard(job = job, onDelete = { onDeleteJob(job) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun JobCard(
    job: UserJob,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = job.company,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatJobDateRange(job.start_date, job.end_date, job.is_current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!job.location.isNullOrBlank()) {
                Text(
                    text = job.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!job.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = job.description,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddJobDialog(
    onDismiss: () -> Unit,
    onAddJob: (String, String, String?, String, String?, Boolean, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var isCurrent by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Work Experience") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Job Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Start Date * (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("2023-01-15") }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isCurrent,
                        onCheckedChange = { isCurrent = it }
                    )
                    Text("I currently work here")
                }
                if (!isCurrent) {
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("End Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("2024-06-30") }
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddJob(
                        title,
                        company,
                        location.ifBlank { null },
                        startDate,
                        if (isCurrent) null else endDate.ifBlank { null },
                        isCurrent,
                        description.ifBlank { null }
                    )
                },
                enabled = title.isNotBlank() && company.isNotBlank() && startDate.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatMemberSince(dateStr: String?): String {
    if (dateStr == null) return "-"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateStr) ?: return dateStr
        SimpleDateFormat("MMMM yyyy", Locale.US).format(date)
    } catch (e: Exception) {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val date = inputFormat.parse(dateStr) ?: return dateStr
            SimpleDateFormat("MMMM yyyy", Locale.US).format(date)
        } catch (e: Exception) {
            dateStr
        }
    }
}

private fun formatJobDateRange(startDate: String, endDate: String?, isCurrent: Boolean): String {
    val formatInput = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val formatOutput = SimpleDateFormat("MMM yyyy", Locale.US)

    val start = try {
        val date = formatInput.parse(startDate)
        if (date != null) formatOutput.format(date) else startDate
    } catch (e: Exception) {
        startDate
    }

    val end = if (isCurrent) {
        "Present"
    } else if (!endDate.isNullOrBlank()) {
        try {
            val date = formatInput.parse(endDate)
            if (date != null) formatOutput.format(date) else endDate
        } catch (e: Exception) {
            endDate
        }
    } else {
        "Present"
    }

    return "$start - $end"
}
