package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.AdminRepository
import com.cherryblossomdev.breakroom.data.models.SearchUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImpersonateViewModel(private val adminRepository: AdminRepository) : ViewModel() {
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var users by mutableStateOf<List<SearchUser>>(emptyList())
        private set
    var searchQuery by mutableStateOf("")
    var impersonatingId by mutableStateOf<Int?>(null)
        private set
    var impersonationError by mutableStateOf<String?>(null)
        private set

    val filteredUsers: List<SearchUser>
        get() = if (searchQuery.isBlank()) users
                else users.filter {
                    it.handle.contains(searchQuery, ignoreCase = true) ||
                    it.displayName.contains(searchQuery, ignoreCase = true)
                }

    init {
        viewModelScope.launch {
            isLoading = true
            users = withContext(Dispatchers.IO) { adminRepository.getAllUsers() }
            isLoading = false
        }
    }

    fun startImpersonation(userId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            impersonatingId = userId
            impersonationError = null
            val result = withContext(Dispatchers.IO) { adminRepository.startImpersonation(userId) }
            impersonatingId = null
            result.onSuccess { onSuccess() }
            result.onFailure { impersonationError = it.message }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpersonateScreen(
    viewModel: ImpersonateViewModel,
    onImpersonated: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impersonate User") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                placeholder = { Text("Search users...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            if (viewModel.impersonationError != null) {
                Text(
                    text = viewModel.impersonationError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(viewModel.filteredUsers) { user ->
                        ListItem(
                            headlineContent = { Text(user.displayName) },
                            supportingContent = { Text("@${user.handle}") },
                            trailingContent = {
                                if (viewModel.impersonatingId == user.id) {
                                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                                } else {
                                    Button(
                                        onClick = { viewModel.startImpersonation(user.id, onImpersonated) },
                                        enabled = viewModel.impersonatingId == null
                                    ) {
                                        Text("Impersonate")
                                    }
                                }
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}
