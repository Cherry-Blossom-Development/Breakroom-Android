package com.cherryblossomdev.breakroom.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.FriendsRepository
import com.cherryblossomdev.breakroom.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FriendsUiState(
    val friends: List<Friend> = emptyList(),
    val requests: List<FriendRequest> = emptyList(),
    val sent: List<FriendRequest> = emptyList(),
    val blocked: List<BlockedUser> = emptyList(),
    val allUsers: List<SearchUser> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val actionInProgress: Int? = null,
    val successMessage: String? = null
)

class FriendsViewModel(
    private val friendsRepository: FriendsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load all data in parallel
            val friendsResult = friendsRepository.getFriends()
            val requestsResult = friendsRepository.getFriendRequests()
            val sentResult = friendsRepository.getSentRequests()
            val blockedResult = friendsRepository.getBlockedUsers()
            val usersResult = friendsRepository.getAllUsers()

            _uiState.value = _uiState.value.copy(
                friends = ((friendsResult as? BreakroomResult.Success)?.data ?: emptyList()).distinctBy { it.id },
                requests = ((requestsResult as? BreakroomResult.Success)?.data ?: emptyList()).distinctBy { it.id },
                sent = ((sentResult as? BreakroomResult.Success)?.data ?: emptyList()).distinctBy { it.id },
                blocked = ((blockedResult as? BreakroomResult.Success)?.data ?: emptyList()).distinctBy { it.id },
                allUsers = ((usersResult as? BreakroomResult.Success)?.data ?: emptyList()).distinctBy { it.id },
                isLoading = false
            )
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            when (val result = friendsRepository.getFriends()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(friends = result.data.distinctBy { it.id })
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired - please log in again")
                }
                else -> { }
            }
        }
    }

    fun loadRequests() {
        viewModelScope.launch {
            when (val result = friendsRepository.getFriendRequests()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(requests = result.data)
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired - please log in again")
                }
                else -> { }
            }
        }
    }

    fun loadSent() {
        viewModelScope.launch {
            when (val result = friendsRepository.getSentRequests()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(sent = result.data)
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired - please log in again")
                }
                else -> { }
            }
        }
    }

    fun loadBlocked() {
        viewModelScope.launch {
            when (val result = friendsRepository.getBlockedUsers()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(blocked = result.data)
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired - please log in again")
                }
                else -> { }
            }
        }
    }

    fun sendFriendRequest(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = userId)
            when (val result = friendsRepository.sendFriendRequest(userId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        successMessage = result.data
                    )
                    loadSent()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = "Session expired - please log in again"
                    )
                }
                else -> { }
            }
        }
    }

    fun acceptFriendRequest(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = userId)
            when (val result = friendsRepository.acceptFriendRequest(userId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        successMessage = result.data
                    )
                    loadFriends()
                    loadRequests()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = "Session expired - please log in again"
                    )
                }
                else -> { }
            }
        }
    }

    fun declineFriendRequest(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = userId)
            when (val result = friendsRepository.declineFriendRequest(userId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        successMessage = result.data
                    )
                    loadRequests()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = "Session expired - please log in again"
                    )
                }
                else -> { }
            }
        }
    }

    fun cancelFriendRequest(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = userId)
            when (val result = friendsRepository.cancelFriendRequest(userId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        successMessage = result.data
                    )
                    loadSent()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = "Session expired - please log in again"
                    )
                }
                else -> { }
            }
        }
    }

    fun removeFriend(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = userId)
            when (val result = friendsRepository.removeFriend(userId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        successMessage = result.data
                    )
                    loadFriends()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = "Session expired - please log in again"
                    )
                }
                else -> { }
            }
        }
    }

    fun blockUser(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = userId)
            when (val result = friendsRepository.blockUser(userId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        successMessage = result.data
                    )
                    loadFriends()
                    loadBlocked()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = "Session expired - please log in again"
                    )
                }
                else -> { }
            }
        }
    }

    fun unblockUser(userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = userId)
            when (val result = friendsRepository.unblockUser(userId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        successMessage = result.data
                    )
                    loadBlocked()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        error = "Session expired - please log in again"
                    )
                }
                else -> { }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
