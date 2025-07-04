package ar.edu.um.tif.aiAssistant.component.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.auth.AuthManager
import ar.edu.um.tif.aiAssistant.core.navigation.Assistant
import ar.edu.um.tif.aiAssistant.core.navigation.Welcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    private val _navigateTo = MutableStateFlow<Any?>(null)
    val navigateTo: StateFlow<Any?> = _navigateTo

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            // Delay to show splash screen
            kotlinx.coroutines.delay(1500)

            // Use the centralized AuthManager to verify authentication
            val isAuthenticated = authManager.verifyAuthentication()

            if (isAuthenticated) {
                // Authentication is valid, navigate directly to assistant screen
                _navigateTo.value = Assistant
            } else {
                // Authentication failed, navigate to welcome screen
                _navigateTo.value = Welcome
            }
        }
    }
}
