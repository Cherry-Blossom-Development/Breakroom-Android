package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.cherryblossomdev.breakroom.data.ProfileRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.UserProfile
import com.cherryblossomdev.breakroom.data.models.UserJob
import com.cherryblossomdev.breakroom.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class PublicProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val error: String? = null
)

class PublicProfileViewModel(
    private val profileRepository: ProfileRepository,
    val handle: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(PublicProfileUiState())
    val uiState: StateFlow<PublicProfileUiState> = _uiState

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = PublicProfileUiState(isLoading = true)
            when (val result = profileRepository.getPublicProfile(handle)) {
                is BreakroomResult.Success -> _uiState.value = PublicProfileUiState(isLoading = false, profile = result.data)
                is BreakroomResult.Error -> _uiState.value = PublicProfileUiState(isLoading = false, error = result.message)
                else -> _uiState.value = PublicProfileUiState(isLoading = false, error = "Failed to load profile")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    viewModel: PublicProfileViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.profile?.handle?.let { "@$it" } ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.loadProfile() }) { Text("Retry") }
                    }
                }
            }
            uiState.profile != null -> {
                ProfileContent(
                    profile = uiState.profile!!,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(profile: UserProfile, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: avatar + name + meta
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Avatar
                val photoUrl = profile.photoPath?.let { "${RetrofitClient.BASE_URL}api/uploads/$it" }
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Profile photo",
                        modifier = Modifier.size(80.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.initials,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${profile.handle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (profile.createdAt != null) {
                        Text(
                            text = "Member since ${formatMemberSince(profile.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${profile.friendCount} ${if (profile.friendCount == 1) "friend" else "friends"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bio
        item {
            ProfileSection(title = "About") {
                if (!profile.bio.isNullOrBlank()) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "This user hasn't added a bio yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }

        // Work bio
        if (!profile.workBio.isNullOrBlank()) {
            item {
                ProfileSection(title = "Work Biography") {
                    Text(
                        text = profile.workBio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Skills
        if (profile.skills.isNotEmpty()) {
            item {
                ProfileSection(title = "Skills") {
                    profile.skills.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { skill ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(skill.name, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Jobs
        if (profile.jobs.isNotEmpty()) {
            item {
                ProfileSection(title = "Work Experience") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        profile.jobs.forEach { job ->
                            JobCard(job = job)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Divider()
            content()
        }
    }
}

@Composable
private fun JobCard(job: UserJob) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(job.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(job.company, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        val dates = buildString {
            append(formatJobDate(job.start_date))
            append(" – ")
            append(if (job.isCurrent) "Present" else formatJobDate(job.end_date))
        }
        Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!job.description.isNullOrBlank()) {
            Text(
                text = job.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun formatMemberSince(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString) ?: return ""
        SimpleDateFormat("MMMM yyyy", Locale.US).format(date)
    } catch (e: Exception) { "" }
}

private fun formatJobDate(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = inputFormat.parse(dateString) ?: return ""
        SimpleDateFormat("MMM yyyy", Locale.US).format(date)
    } catch (e: Exception) { dateString }
}
