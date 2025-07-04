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
                errorMessage = "El código de restablecimiento y la nueva contraseña son obligatorios",
                isLoading = false
            )}
            return
        }

        if (newPassword.length < 8) {
            _uiState.update { it.copy(
                errorMessage = "La contraseña debe tener al menos 8 caracteres",
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
                            exception.message?.contains("400") == true -> "Código de restablecimiento inválido"
                            exception.message?.contains("404") == true -> "Código no encontrado o expirado"
                            else -> "Error al restablecer la contraseña: ${exception.message}"
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
