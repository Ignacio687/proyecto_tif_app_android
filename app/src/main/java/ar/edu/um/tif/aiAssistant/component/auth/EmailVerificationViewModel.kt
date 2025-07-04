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
                errorMessage = "El código de verificación no puede estar vacío",
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
                                successMessage = response.message ?: "¡Correo electrónico verificado con éxito!"
                            )}
                        } else {
                            _uiState.update { it.copy(
                                isLoading = false,
                                errorMessage = "La verificación falló. Por favor, inténtalo de nuevo."
                            )}
                        }
                    },
                    onFailure = { exception ->
                        // Log the full technical error to the console
                        android.util.Log.e("EmailVerificationViewModel", "Verification error: ${exception.message}", exception)

                        // Provide a user-friendly error message
                        val errorMessage = when {
                            exception.message?.contains("Invalid or expired verification code") == true ->
                                "Tu código de verificación es inválido o ha expirado. Por favor, solicita un nuevo código."
                            exception.message?.contains("400") == true ->
                                "Código de verificación inválido. Por favor, revisa e inténtalo de nuevo."
                            exception.message?.contains("404") == true ->
                                "Correo no encontrado o código expirado. Por favor, intenta registrarte de nuevo."
                            else -> "La verificación falló. Por favor, inténtalo más tarde."
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
                    errorMessage = "La verificación falló. Por favor, inténtalo de nuevo."
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
                        errorMessage = "Falta la dirección de correo electrónico. Por favor, regresa e inténtalo de nuevo."
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
                            exception.message?.contains("404") == true -> "Correo electrónico no encontrado"
                            exception.message?.contains("429") == true -> "Demasiadas solicitudes, por favor intenta más tarde"
                            else -> "Error al reenviar el código: ${exception.message}"
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
    val email: String? = null,
    val needsEmailInput: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
