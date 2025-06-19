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
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(emailOrUsername: String, password: String) {
        if (emailOrUsername.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(
                errorMessage = "Email/username and password cannot be empty",
                isLoading = false
            )}
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                val result = authRepository.loginWithEmail(emailOrUsername, password)

                result.fold(
                    onSuccess = { authResponse ->
                        _uiState.update { it.copy(
                            isAuthenticated = true,
                            isLoading = false,
                            errorMessage = null
                        )}
                    },
                    onFailure = { exception ->
                        // Handle the exception and determine the appropriate error message
                        val errorMessage = when {
                            exception.message?.contains("401") == true &&
                            exception.message?.contains("verify your email") == true -> "Account not verified"
                            exception.message?.contains("401") == true -> "Invalid credentials"
                            exception.message?.contains("403") == true -> "Account not verified"
                            else -> "Login failed: ${exception.message}"
                        }

                        // Handle specific case for email verification needed
                        if ((exception.message?.contains("403") == true) ||
                            (exception.message?.contains("401") == true &&
                             exception.message?.contains("verify your email") == true)) {
                            _uiState.update { it.copy(
                                isLoading = false,
                                needsEmailVerification = true,
                                userEmail = emailOrUsername,
                                errorMessage = null
                            )}
                        } else {
                            _uiState.update { it.copy(
                                isLoading = false,
                                errorMessage = errorMessage
                            )}
                        }
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
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val needsEmailVerification: Boolean = false,
    val userEmail: String? = null,
    val errorMessage: String? = null
)
