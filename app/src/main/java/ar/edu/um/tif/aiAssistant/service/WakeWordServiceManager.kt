package ar.edu.um.tif.aiAssistant.service

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class WakeWordServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WakeWordServiceManager"
        private const val PREFS_NAME = "wake_word_service_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) {
            sharedPrefs.edit { putBoolean(KEY_SERVICE_ENABLED, value) }
            if (value) {
                startService()
            } else {
                stopService()
            }
        }

    fun startService() {
        try {
            Log.d(TAG, "Starting Kaldi wake word service")
            KaldiWakeWordService.startService(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake word service", e)
        }
    }

    fun stopService() {
        try {
            Log.d(TAG, "Stopping wake word service")
            KaldiWakeWordService.stopService(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word service", e)
        }
    }

    fun restartService() {
        stopService()
        Thread.sleep(1000) // Give service time to stop
        startService()
    }

    fun isServiceRunning(): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            manager.getRunningServices(Integer.MAX_VALUE).any { service ->
                KaldiWakeWordService::class.java.name == service.service.className
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if service is running", e)
            false
        }
    }
}
