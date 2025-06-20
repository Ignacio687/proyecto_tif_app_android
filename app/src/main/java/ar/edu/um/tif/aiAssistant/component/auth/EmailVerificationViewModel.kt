package ar.edu.um.tif.aiAssistant.component.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.data.repository.AuthRepository
import ar.edu.um.tif.aiAssistant.core.navigation.EmailVerification
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

    // Get email from EmailVerification navigation object
    private var email = EmailVerification.email

    init {
        // Log the email for debugging purposes
        android.util.Log.d("EmailVerificationViewModel", "Email received: $email")

        // Update UI state with the email
        _uiState.update { it.copy(email = email, needsEmailInput = email.isEmpty()) }
    }

    fun updateEmail(newEmail: String) {
        email = newEmail
        _uiState.update { it.copy(email = newEmail, needsEmailInput = false) }
    }

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

                        // Provide a user-friendly error message
                        val errorMessage = when {
                            exception.message?.contains("Invalid or expired verification code") == true ->
                                "Your verification code is invalid or has expired. Please request a new code."
                            exception.message?.contains("400") == true ->
                                "Invalid verification code. Please check and try again."
                            exception.message?.contains("404") == true ->
                                "Email not found or code expired. Please try registering again."
                            else -> "Verification failed. Please try again later."
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

                // Log the email being used for debugging
                android.util.Log.d("EmailVerificationViewModel", "Resending verification code to: $email")

                // Check if we have an email to send to
                if (email.isBlank()) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = "Email address is missing. Please go back and try again."
                    )}
                    return@launch
                }

                val result = authRepository.resendVerificationCode(email)

                result.fold(
                    onSuccess = { response ->
                        _uiState.update { it.copy(
                            isLoading = false,
                            errorMessage = null,
                            successMessage = response.message
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
    val successMessage: String? = null,
    val email: String? = null, // Add email to the UI state
    val needsEmailInput: Boolean = false // Track if email input is needed
)
