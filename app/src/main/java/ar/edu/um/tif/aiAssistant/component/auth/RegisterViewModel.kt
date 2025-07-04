package ar.edu.um.tif.aiAssistant.component.auth

import android.util.Log
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
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun register(email: String, username: String, password: String, name: String? = null) {
        // Validate input
        if (email.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(
                errorMessage = "El correo electrónico, nombre de usuario y contraseña son obligatorios",
                isLoading = false
            )}
            return
        }

        if (password.length < 8) {
            _uiState.update { it.copy(
                errorMessage = "La contraseña debe tener al menos 8 caracteres",
                isLoading = false
            )}
            return
        }

        if (username.length < 3) {
            _uiState.update { it.copy(
                errorMessage = "El nombre de usuario debe tener al menos 3 caracteres",
                isLoading = false
            )}
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                val result = authRepository.registerWithEmail(email, username, password, name)

                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(
                            isRegistered = true,
                            isLoading = false,
                            errorMessage = null
                        )}
                    },
                    onFailure = { exception ->
                        val errorMessage = when {
                            exception.message?.contains("409") == true -> "El correo o nombre de usuario ya existe"
                            exception.message?.contains("400") == true -> "Formato de correo inválido o contraseña demasiado débil"
                            else -> "Error en el registro. Por favor, inténtalo más tarde."
                        }

                        // Log the technical error for debugging purposes
                        Log.e("RegisterViewModel", "Registration error: ${exception.message}", exception)

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

data class RegisterUiState(
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val errorMessage: String? = null
)
