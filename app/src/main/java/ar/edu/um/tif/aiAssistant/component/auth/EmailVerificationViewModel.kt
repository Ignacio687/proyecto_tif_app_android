package ar.edu.um.tif.aiAssistant.component.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class EmailVerificationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailVerificationUiState())
    val uiState: StateFlow<EmailVerificationUiState> = _uiState.asStateFlow()

    fun verifyEmail(code: String) {
        if (code.isBlank()) {
            _uiState.update { it.copy(
                errorMessage = "Verification code cannot be empty",
                isLoading = false
            )}
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

                val result = authRepository.verifyEmail(code)

                result.fold(
                    onSuccess = { response ->
                        if (response.verified) {
                            _uiState.update { it.copy(
                                isVerified = true,
                                isLoading = false,
                                errorMessage = null,
                                successMessage = response.message ?: "Email successfully verified!"
                            )}
                        } else {
                            _uiState.update { it.copy(
                                isLoading = false,
                                errorMessage = "Verification failed. Please try again."
                            )}
                        }
                    },
                    onFailure = { exception ->
                        // Log the full technical error to the console
                        android.util.Log.e("EmailVerificationViewModel", "Verification error: ${exception.message}", exception)

                        // Provide a simplified error message to the user
                        val errorMessage = when {
                            exception.message?.contains("400") == true -> "Invalid verification code"
                            exception.message?.contains("404") == true -> "Email not found or code expired"
                            else -> "Verification failed. Please try again."
                        }
                        _uiState.update { it.copy(
                            isLoading = false,
                            errorMessage = errorMessage
                        )}
                    }
                )
            } catch (e: Exception) {
                // Log the full technical error to the console
                android.util.Log.e("EmailVerificationViewModel", "Verification error: ${e.message}", e)

                // Provide a simplified error message to the user
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = "Verification failed. Please try again."
                )}
            }
        }
    }

    fun resendVerificationCode() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

                val email = authRepository.email.first() ?: return@launch

                val result = authRepository.resendVerificationCode(email)

                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(
                            isLoading = false,
                            errorMessage = null,
                            successMessage = "Verification code resent to your email"
                        )}
                    },
                    onFailure = { exception ->
                        val errorMessage = when {
                            exception.message?.contains("404") == true -> "Email not found"
                            exception.message?.contains("429") == true -> "Too many requests, please try again later"
                            else -> "Failed to resend code: ${exception.message}"
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

data class EmailVerificationUiState(
    val isLoading: Boolean = false,
    val isVerified: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
