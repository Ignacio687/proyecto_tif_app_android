package ar.edu.um.tif.aiAssistant.component.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.data.repository.AuthRepository
import ar.edu.um.tif.aiAssistant.core.navigation.Home
import ar.edu.um.tif.aiAssistant.core.navigation.Welcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _navigateTo = MutableStateFlow<Any?>(null)
    val navigateTo: StateFlow<Any?> = _navigateTo

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            // Get token using the suspending function instead of the flow
            val token = authRepository.getAuthToken()

            // Delay to show splash screen
            kotlinx.coroutines.delay(1500)

            if (token.isNullOrEmpty()) {
                // No token, navigate to welcome screen
                _navigateTo.value = Welcome
            } else {
                try {
                    // Verify token with the server
                    val response = authRepository.verifyToken()
                    response.fold(
                        onSuccess = { tokenData ->
                            // Check if the token is valid
                            if (tokenData.valid) {
                                // Token is valid, navigate to home screen
                                _navigateTo.value = Home
                            } else {
                                // Token is invalid, clear auth data and navigate to welcome screen
                                viewModelScope.launch {
                                    authRepository.clearAuthData()
                                }
                                _navigateTo.value = Welcome
                            }
                        },
                        onFailure = { error ->
                            // Log the error for debugging
                            android.util.Log.e("SplashViewModel", "Token verification failed", error)

                            // Token verification failed, clear auth data and navigate to welcome screen
                            viewModelScope.launch {
                                authRepository.clearAuthData()
                            }
                            _navigateTo.value = Welcome
                        }
                    )
                } catch (e: Exception) {
                    // Log the error for debugging
                    android.util.Log.e("SplashViewModel", "Exception during token verification", e)

                    // Error verifying token, clear auth data and navigate to welcome screen
                    viewModelScope.launch {
                        authRepository.clearAuthData()
                    }
                    _navigateTo.value = Welcome
                }
            }
        }
    }
}
