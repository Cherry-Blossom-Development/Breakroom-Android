package com.example.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.breakroom.data.AuthRepository
import com.example.breakroom.data.TokenManager
import com.example.breakroom.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val username: String = "",
    val isLoading: Boolean = false
)

class ProfileViewModel(
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        _uiState.value = _uiState.value.copy(
            username = tokenManager.getUsername() ?: "Unknown"
        )
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }
}

@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tokenManager = TokenManager(context)
    val authRepository = AuthRepository(RetrofitClient.apiService, tokenManager)
    val viewModel: ProfileViewModel = viewModel {
        ProfileViewModel(tokenManager, authRepository)
    }
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Logged in as: ${uiState.username}",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { viewModel.logout(onLoggedOut) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Logout")
        }
    }
}
