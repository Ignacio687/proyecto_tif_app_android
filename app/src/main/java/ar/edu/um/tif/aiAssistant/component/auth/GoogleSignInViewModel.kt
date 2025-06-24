package ar.edu.um.tif.aiAssistant.component.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoogleSignInViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoogleSignInUiState())
    val uiState: StateFlow<GoogleSignInUiState> = _uiState.asStateFlow()

    fun authenticateWithGoogle(idToken: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                val result = authRepository.authenticateWithGoogle(idToken)

                result.fold(
                    onSuccess = { authResponse ->
                        _uiState.update { it.copy(
                            isAuthenticated = true,
                            isLoading = false,
                            errorMessage = null
                        )}
                    },
                    onFailure = { exception ->
                        val errorMessage = when {
                            exception.message?.contains("401") == true -> "Authentication failed: Invalid token"
                            else -> "Authentication failed: ${exception.message}"
                        }
                        _uiState.update { it.copy(
                            isLoading = false,
                            errorMessage = errorMessage
                        )}
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.localizedMessage}"
                )}
            }
        }
    }

    fun handleSignInError(errorMessage: String) {
        _uiState.update { it.copy(
            isLoading = false,
            errorMessage = errorMessage
        )}
    }
}

data class GoogleSignInUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null
)
