package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.AuthRepository
import com.cherryblossomdev.breakroom.data.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class LegalUiState(
    val isLoading: Boolean = true,
    val eulaAccepted: Boolean = false,
    val eulaAcceptedAt: String? = null,
    val error: String? = null
)

class LegalViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LegalUiState())
    val uiState: StateFlow<LegalUiState> = _uiState

    init {
        loadStatus()
    }

    fun loadStatus() {
        viewModelScope.launch {
            _uiState.value = LegalUiState(isLoading = true)
            when (val result = authRepository.getEulaStatus()) {
                is AuthResult.Success -> {
                    _uiState.value = LegalUiState(
                        isLoading = false,
                        eulaAccepted = result.data.accepted,
                        eulaAcceptedAt = result.data.acceptedAt
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = LegalUiState(isLoading = false, error = "Failed to load status")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    viewModel: LegalViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEula: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Legal Documents") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Documents associated with your account on Prosaurus.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // EULA card
            LegalDocumentCard(
                title = "End User License Agreement (EULA)",
                description = "Governs your use of the Prosaurus platform.",
                status = if (uiState.eulaAccepted) "accepted" else "pending",
                acceptedAt = uiState.eulaAcceptedAt,
                onClick = onNavigateToEula
            )

            // Privacy Policy card
            LegalDocumentCard(
                title = "Privacy Policy",
                description = "Describes how we collect, use, and protect your data.",
                status = "acknowledged",
                acceptedAt = null,
                onClick = onNavigateToPrivacyPolicy
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalDocumentCard(
    title: String,
    description: String,
    status: String,
    acceptedAt: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (status == "accepted" && acceptedAt != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Accepted on ${formatLegalDate(acceptedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (status == "acknowledged") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Acknowledged by creating an account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            when (status) {
                "accepted", "acknowledged" -> {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (status == "accepted") "Accepted" else "Acknowledged") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                else -> {
                    AssistChip(
                        onClick = {},
                        label = { Text("Pending") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }
        }
    }
}

private fun formatLegalDate(dateStr: String): String {
    return try {
        // Try ISO 8601 format from backend
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(dateStr) ?: return dateStr
        val out = SimpleDateFormat("MMMM d, yyyy", Locale.US)
        out.format(date)
    } catch (e: Exception) {
        dateStr
    }
}
