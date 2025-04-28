package ar.edu.um.tif.aiAssistant.component.assistant

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import ar.edu.um.tif.aiAssistant.core.voice.PorcupineService

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    var isListening by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Permissions required
    private val permissions = listOf(
        Manifest.permission.RECORD_AUDIO
    ) + if (android.os.Build.VERSION.SDK_INT >= 33)
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    else emptyList()

    fun hasAllPermissions(context: Context): Boolean =
        permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

    fun requestPermissions(launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(permissions.toTypedArray())
    }

    fun onPermissionsResult(context: Context, grantResults: Map<String, Boolean>) {
        if (grantResults.values.all { it }) {
            startService(context)
        } else {
            onError("Microphone/notification permissions are required for this feature")
        }
    }

    fun startService(context: Context) {
        errorMessage = null
        val intent = Intent(context, PorcupineService::class.java)
        ContextCompat.startForegroundService(context, intent)
        isListening = true
    }

    fun stopService(context: Context) {
        val intent = Intent(context, PorcupineService::class.java)
        context.stopService(intent)
        isListening = false
    }

    fun onError(msg: String) {
        errorMessage = msg
        isListening = false
    }
}