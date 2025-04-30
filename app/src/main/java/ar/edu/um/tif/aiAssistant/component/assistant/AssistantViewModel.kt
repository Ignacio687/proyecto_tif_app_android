package ar.edu.um.tif.aiAssistant.component.assistant

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import ar.edu.um.tif.aiAssistant.core.voice.AimyboxService
import com.justai.aimybox.Aimybox
import ar.edu.um.tif.aiAssistant.core.voice.AimyboxApplication

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    val aimybox: Aimybox = (application as AimyboxApplication).aimybox

    var isListening by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
        }
    }

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
            onError("Microphone/notification permissions are required")
        }
    }

    fun startService(context: Context) {
        errorMessage = null
        ContextCompat.startForegroundService(
            context, Intent(context, AimyboxService::class.java)
        )
        isListening = true
    }

    fun stopService(context: Context) {
        context.stopService(Intent(context, AimyboxService::class.java))
        isListening = false
    }

    private fun onError(msg: String) {
        errorMessage = msg
        isListening = false
    }
}