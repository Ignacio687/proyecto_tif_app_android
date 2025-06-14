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
class ResetPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun resetPassword(code: String, newPassword: String) {
        // Validate input
        if (code.isBlank() || newPassword.isBlank()) {
            _uiState.update { it.copy(
                errorMessage = "Reset code and new password are required",
                isLoading = false
            )}
            return
        }

        if (newPassword.length < 8) {
            _uiState.update { it.copy(
                errorMessage = "Password must be at least 8 characters",
                isLoading = false
            )}
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                val result = authRepository.confirmPasswordReset(code, newPassword)

                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(
                            isResetSuccessful = true,
                            isLoading = false,
                            errorMessage = null
                        )}
                    },
                    onFailure = { exception ->
                        val errorMessage = when {
                            exception.message?.contains("400") == true -> "Invalid reset code"
                            exception.message?.contains("404") == true -> "Reset code not found or expired"
                            else -> "Password reset failed: ${exception.message}"
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
}

data class ResetPasswordUiState(
    val isLoading: Boolean = false,
    val isResetSuccessful: Boolean = false,
    val errorMessage: String? = null
)
