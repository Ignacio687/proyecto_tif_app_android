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
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun requestPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.update { it.copy(
                errorMessage = "Email cannot be empty",
                isLoading = false
            )}
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                val result = authRepository.requestPasswordReset(email)

                result.fold(
                    onSuccess = { response ->
                        _uiState.update { it.copy(
                            isCodeSent = true,
                            isLoading = false,
                            errorMessage = null,
                            successMessage = response.message
                        )}
                    },
                    onFailure = { exception ->
                        // Log the technical error for debugging
                        android.util.Log.e("ForgotPasswordViewModel", "Password reset error: ${exception.message}", exception)

                        // Provide a user-friendly error message
                        val errorMessage = "We couldn't process your request. Please try again later."
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

data class ForgotPasswordUiState(
    val isLoading: Boolean = false,
    val isCodeSent: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
