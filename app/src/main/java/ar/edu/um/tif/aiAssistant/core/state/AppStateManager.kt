package ar.edu.um.tif.aiAssistant.core.state

import android.util.Log
import ar.edu.um.tif.aiAssistant.service.WakeWordServiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AppScreen {
    SPLASH,
    WELCOME,
    LOGIN,
    REGISTER,
    HOME,
    ASSISTANT,
    SETTINGS,
    OTHER
}

@Singleton
class AppStateManager @Inject constructor(
    private val wakeWordServiceManager: WakeWordServiceManager
) {
    companion object {
        private const val TAG = "AppStateManager"
    }

    private val _currentScreen = MutableStateFlow(AppScreen.SPLASH)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _isAssistantActive = MutableStateFlow(false)
    val isAssistantActive: StateFlow<Boolean> = _isAssistantActive.asStateFlow()

    // Helper property that combines screen state and foreground state
    val isAssistantScreenActiveAndInForeground: Boolean
        get() = _currentScreen.value == AppScreen.ASSISTANT && _isAppInForeground.value

    fun setCurrentScreen(screen: AppScreen) {
        val previousScreen = _currentScreen.value
        _currentScreen.value = screen

        updateAssistantState()

        Log.d(TAG, "Screen changed: $previousScreen -> $screen, Assistant active: ${_isAssistantActive.value}")
    }

    fun setAppInForeground(inForeground: Boolean) {
        val wasInForeground = _isAppInForeground.value
        _isAppInForeground.value = inForeground

        Log.d(TAG, "App foreground state changed: $wasInForeground -> $inForeground")

        updateAssistantState()
    }

    private fun updateAssistantState() {
        val wasAssistantActive = _isAssistantActive.value
        val isAssistantActive = isAssistantScreenActiveAndInForeground

        if (wasAssistantActive != isAssistantActive) {
            _isAssistantActive.value = isAssistantActive

            // Handle assistant screen lifecycle
            if (!wasAssistantActive && isAssistantActive) {
                // Entering assistant screen (and app is in foreground)
                Log.d(TAG, "Assistant screen became active - notifying service manager")
                wakeWordServiceManager.onAssistantScreenEntered()
            } else if (wasAssistantActive && !isAssistantActive) {
                // Exiting assistant screen (navigating away or app going to background)
                Log.d(TAG, "Assistant screen became inactive - notifying service manager")
                wakeWordServiceManager.onAssistantScreenExited()
            }
        }
    }

    fun setAssistantActive(active: Boolean) {
        val wasActive = _isAssistantActive.value
        _isAssistantActive.value = active

        // Handle assistant lifecycle when called directly
        if (!wasActive && active) {
            Log.d(TAG, "Assistant activated - notifying service manager")
            wakeWordServiceManager.onAssistantScreenEntered()
        } else if (wasActive && !active) {
            Log.d(TAG, "Assistant deactivated - notifying service manager")
            wakeWordServiceManager.onAssistantScreenExited()
        }
    }
}
