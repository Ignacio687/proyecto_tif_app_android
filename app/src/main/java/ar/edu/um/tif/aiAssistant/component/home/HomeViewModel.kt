package ar.edu.um.tif.aiAssistant.component.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.auth.AuthManager
import ar.edu.um.tif.aiAssistant.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserInfo()
        observeAuthEvents()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            try {
                val name = authRepository.name.first()
                val email = authRepository.email.first()

                _uiState.update {
                    it.copy(
                        userName = name,
                        userEmail = email
                    )
                }
            } catch (e: Exception) {
                // Only handle loading error, auth errors are handled by AuthManager
                _uiState.update {
                    it.copy(errorMessage = "Failed to load user information")
                }
            }
        }
    }

    private fun observeAuthEvents() {
        viewModelScope.launch {
            authManager.authEvents.collect { event ->
                when (event) {
                    AuthManager.AuthEvent.AUTH_ERROR -> {
                        _uiState.update { it.copy(authError = true) }
                    }
                    AuthManager.AuthEvent.LOGGED_OUT -> {
                        _uiState.update { it.copy(isLoggedOut = true) }
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.logout()
        }
    }
}

data class HomeUiState(
    val userName: String? = null,
    val userEmail: String? = null,
    val isLoggedOut: Boolean = false,
    val authError: Boolean = false,
    val errorMessage: String? = null
)
