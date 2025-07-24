package ar.edu.um.tif.aiAssistant.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ar.edu.um.tif.aiAssistant.MainActivity
import ar.edu.um.tif.aiAssistant.R
import ar.edu.um.tif.aiAssistant.core.state.AppStateManager
import com.justai.aimybox.Aimybox
import com.justai.aimybox.voicetrigger.VoiceTrigger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class KaldiWakeWordService : Service() {

    companion object {
        private const val TAG = "KaldiWakeWordService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "kaldi_wake_word_service_channel"
        private const val CHANNEL_NAME = "AI Assistant Wake Word Detection"
        private const val RESTART_DELAY = 2000L // 2 seconds

        fun startService(context: Context) {
            val intent = Intent(context, KaldiWakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, KaldiWakeWordService::class.java)
            context.stopService(intent)
        }
    }

    @Inject
    lateinit var appStateManager: AppStateManager

    @Inject
    lateinit var aimybox: Aimybox

    private var serviceScope: CoroutineScope? = null
    private var voiceTriggerJob: Job? = null
    private var isDetectionActive = false
    private var lastTriggerTime = 0L
    private var restartAttempts = 0
    private val maxRestartAttempts = 5
    private var isServiceInForeground = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KaldiWakeWordService created")

        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "KaldiWakeWordService started")

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        isServiceInForeground = true

        // Check if this is a popup dismissal notification
        if (intent?.action == "POPUP_DISMISSED") {
            Log.d(TAG, "Received popup dismissal notification")
            onPopupDismissed()
        } else {
            startWakeWordDetection()
        }

        return START_STICKY // Restart if killed
    }

    override fun onDestroy() {
        Log.d(TAG, "KaldiWakeWordService destroyed")
        isServiceInForeground = false
        stopWakeWordDetection()
        voiceTriggerJob?.cancel()
        serviceScope?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the AI Assistant listening for wake words in the background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Assistant")
            .setContentText("Listening for 'Hola Iris'...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun startWakeWordDetection() {
        if (isDetectionActive) {
            Log.d(TAG, "Wake word detection already active")
            return
        }

        // Check if we have permission
        if (!hasRecordAudioPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted, cannot start detection")
            return
        }

        voiceTriggerJob = serviceScope?.launch {
            try {
                isDetectionActive = true
                Log.d(TAG, "Starting Aimybox wake word detection")
                Log.i(TAG, "🎤 Background wake word detection is now LISTENING for 'hola iris'")

                // Check if assistant screen is active before enabling voice trigger
                val isAssistantActive = appStateManager.isAssistantActive.value

                if (!isAssistantActive) {
                    // Only enable voice trigger if assistant screen is not active
                    aimybox.isVoiceTriggerActivated = true
                    Log.d(TAG, "Voice trigger activated for background service")
                } else {
                    Log.d(TAG, "Assistant screen active - monitoring wake word events without enabling trigger")
                }

                // Subscribe to voice trigger events
                val voiceTriggerChannel = aimybox.voiceTriggerEvents.openSubscription()

                try {
                    for (event in voiceTriggerChannel) {
                        when (event) {
                            is VoiceTrigger.Event.Triggered -> {
                                Log.i(TAG, "🔥 WAKE WORD DETECTED: '${event.phrase}' - Background service working!")
                                onWakeWordDetected(event.phrase)
                            }
                            is VoiceTrigger.Event.Started -> {
                                Log.i(TAG, "✅ Voice trigger started - Background wake word detection is ACTIVE")
                            }
                            is VoiceTrigger.Event.Stopped -> {
                                Log.w(TAG, "⚠️ Voice trigger stopped")
                                // Only restart if we're still supposed to be active and assistant screen is not active
                                if (isDetectionActive && !appStateManager.isAssistantActive.value) {
                                    Log.w(TAG, "Voice trigger stopped unexpectedly, restarting...")
                                    delay(RESTART_DELAY)
                                    if (isDetectionActive && hasRecordAudioPermission() && !appStateManager.isAssistantActive.value) {
                                        aimybox.isVoiceTriggerActivated = true
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    voiceTriggerChannel.cancel()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake word detection", e)
                isDetectionActive = false
                // Restart detection after delay if conditions are met
                if (restartAttempts < maxRestartAttempts && hasRecordAudioPermission() && !appStateManager.isAssistantActive.value) {
                    restartAttempts++
                    serviceScope?.launch {
                        delay(RESTART_DELAY)
                        if (!isDetectionActive && !appStateManager.isAssistantActive.value) {
                            startWakeWordDetection()
                        }
                    }
                } else {
                    Log.e(TAG, "Max restart attempts reached, permission lost, or assistant screen active")
                }
            }
        }
    }

    private fun stopWakeWordDetection() {
        if (!isDetectionActive) return

        serviceScope?.launch {
            try {
                Log.d(TAG, "Stopping Aimybox wake word detection")
                isDetectionActive = false
                voiceTriggerJob?.cancel()

                // Only disable voice trigger if assistant screen is not active
                val isAssistantActive = appStateManager.isAssistantActive.value
                if (!isAssistantActive) {
                    aimybox.isVoiceTriggerActivated = false
                    Log.d(TAG, "Voice trigger deactivated")
                } else {
                    Log.d(TAG, "Assistant screen active - keeping voice trigger enabled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping wake word detection", e)
            }
        }
    }

    private fun onWakeWordDetected(phrase: String?) {
        // Add debouncing to prevent rapid successive triggers
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTime < 3000) { // 3 second debounce
            Log.d(TAG, "Wake word trigger ignored due to debouncing")
            return
        }
        lastTriggerTime = currentTime

        Log.d(TAG, "Wake word triggered - checking app state. Phrase: $phrase")

        serviceScope?.launch {
            try {
                // Check if assistant screen is already active
                val isAssistantActive = appStateManager.isAssistantActive.value

                Log.d(TAG, "App state - Assistant active: $isAssistantActive, Screen: ${appStateManager.currentScreen.value}")

                if (isAssistantActive) {
                    Log.d(TAG, "Assistant screen is active - triggering recognition directly")
                    if (hasRecordAudioPermission()) {
                        triggerRecognitionInAssistantScreen()
                    } else {
                        Log.w(TAG, "RECORD_AUDIO permission not granted, falling back to popup")
                        launchPopupActivity(phrase)
                    }
                } else {
                    Log.d(TAG, "Assistant screen not active - launching popup")
                    launchPopupActivity(phrase)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error handling wake word detection", e)
            }
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun triggerRecognitionInAssistantScreen() {
        try {
            // Don't launch popup, just trigger recognition in the existing assistant screen
            if (hasRecordAudioPermission()) {
                aimybox.toggleRecognition()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering recognition in assistant screen", e)
        }
    }

    private fun launchPopupActivity(phrase: String?) {
        // Temporarily disable voice trigger to prevent conflicts during popup
        aimybox.isVoiceTriggerActivated = false

        // Launch the popup overlay activity
        val intent = Intent(this@KaldiWakeWordService, AssistantPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                   Intent.FLAG_ACTIVITY_CLEAR_TOP or
                   Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("wake_word_phrase", phrase)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch assistant popup", e)
            // Re-enable voice trigger if launch failed
            serviceScope?.launch {
                delay(1000)
                if (isDetectionActive && !appStateManager.isAssistantActive.value) {
                    aimybox.isVoiceTriggerActivated = true
                }
            }
        }
    }

    // Add method to handle when popup is dismissed
    fun onPopupDismissed() {
        serviceScope?.launch {
            // Re-enable voice trigger after popup is dismissed, but only if assistant screen is not active
            if (isDetectionActive && !appStateManager.isAssistantActive.value && isServiceInForeground) {
                delay(1000) // Brief delay to ensure popup is fully dismissed
                aimybox.isVoiceTriggerActivated = true
                Log.d(TAG, "Voice trigger re-enabled after popup dismissal")
            }
        }
    }
}
