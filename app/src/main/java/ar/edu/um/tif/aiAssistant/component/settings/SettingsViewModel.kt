package ar.edu.um.tif.aiAssistant.component.settings

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.service.WakeWordServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isServiceEnabled: Boolean = false,
    val hasMicrophonePermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val showPermissionInfo: Boolean = false,
    val permissionError: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wakeWordServiceManager: WakeWordServiceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
        updateServiceState()
    }

    private fun checkPermissions() {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasOverlayPermission = Settings.canDrawOverlays(context)

        _uiState.update {
            it.copy(
                hasMicrophonePermission = hasMicPermission,
                hasOverlayPermission = hasOverlayPermission,
                showPermissionInfo = !hasMicPermission || !hasOverlayPermission
            )
        }
    }

    private fun updateServiceState() {
        _uiState.update {
            it.copy(isServiceEnabled = wakeWordServiceManager.isServiceEnabled)
        }
    }

    fun requestPermissions(
        onRequestMicPermission: () -> Unit,
        onRequestOverlayPermission: () -> Unit
    ) {
        checkPermissions()
        val currentState = _uiState.value

        when {
            !currentState.hasMicrophonePermission -> {
                onRequestMicPermission()
            }
            !currentState.hasOverlayPermission -> {
                onRequestOverlayPermission()
            }
            else -> {
                enableWakeWordService()
            }
        }
    }

    fun checkOverlayPermission() {
        checkPermissions()
        val currentState = _uiState.value

        if (!currentState.hasOverlayPermission) {
            val intent = android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            try {
                context.startActivity(intent.apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(permissionError = "Unable to open overlay permission settings")
                }
            }
        } else {
            enableWakeWordService()
        }
    }

    fun onOverlayPermissionResult() {
        checkPermissions()
        val currentState = _uiState.value

        if (currentState.hasMicrophonePermission && currentState.hasOverlayPermission) {
            enableWakeWordService()
        } else if (!currentState.hasOverlayPermission) {
            _uiState.update {
                it.copy(
                    permissionError = "Overlay permission is required to show assistant popup",
                    showPermissionInfo = true
                )
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                permissionError = "Microphone permission is required for wake word detection",
                showPermissionInfo = true
            )
        }
    }

    private fun enableWakeWordService() {
        viewModelScope.launch {
            try {
                wakeWordServiceManager.isServiceEnabled = true
                _uiState.update {
                    it.copy(
                        isServiceEnabled = true,
                        showPermissionInfo = false,
                        permissionError = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        permissionError = "Failed to start wake word service: ${e.message}",
                        showPermissionInfo = true
                    )
                }
            }
        }
    }

    fun disableWakeWordService() {
        viewModelScope.launch {
            try {
                wakeWordServiceManager.isServiceEnabled = false
                _uiState.update {
                    it.copy(
                        isServiceEnabled = false,
                        showPermissionInfo = false,
                        permissionError = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        permissionError = "Failed to stop wake word service: ${e.message}",
                        showPermissionInfo = true
                    )
                }
            }
        }
    }
}
