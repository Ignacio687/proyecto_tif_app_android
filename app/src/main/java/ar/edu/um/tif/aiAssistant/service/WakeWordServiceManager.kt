package ar.edu.um.tif.aiAssistant.service

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
    private var wasServiceRunningBeforeAssistant = false

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
            Log.d(TAG, "Stopping wake word services")
            KaldiWakeWordService.stopService(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word service", e)
        }
    }

    fun restartService() {
        stopService()
        Thread.sleep(1000) // Give services time to stop
        startService()
    }

    // New methods for assistant screen lifecycle management
    fun onAssistantScreenEntered() {
        Log.d(TAG, "Assistant screen entered - managing background service")

        // Remember if service was running before assistant screen
        wasServiceRunningBeforeAssistant = isServiceEnabled

        if (isServiceEnabled) {
            Log.d(TAG, "Temporarily stopping background service while assistant screen is active")
            // Temporarily stop the service but don't change the user preference
            KaldiWakeWordService.stopService(context)
        }
    }

    fun onAssistantScreenExited() {
        Log.d(TAG, "Assistant screen exited - restoring background service state")

        // Restore service state if it was running before assistant screen
        if (wasServiceRunningBeforeAssistant && isServiceEnabled) {
            Log.d(TAG, "Restarting background service after assistant screen exit")
            // Small delay to ensure assistant screen cleanup is complete
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                KaldiWakeWordService.startService(context)
            }, 1000)
        }
    }
}
