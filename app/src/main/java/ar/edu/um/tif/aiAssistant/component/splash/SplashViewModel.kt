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
                    // Verify token with the server (now using the token-optional method)
                    val response = authRepository.verifyToken()
                    response.fold(
                        onSuccess = {
                            // Token is valid, navigate to home screen
                            _navigateTo.value = Home
                        },
                        onFailure = {
                            // Token is invalid, clear auth data and navigate to welcome screen
                            viewModelScope.launch {
                                authRepository.clearAuthData()
                            }
                            _navigateTo.value = Welcome
                        }
                    )
                } catch (e: Exception) {
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
