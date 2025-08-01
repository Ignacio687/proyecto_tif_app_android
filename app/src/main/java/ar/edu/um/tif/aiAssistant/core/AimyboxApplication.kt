package ar.edu.um.tif.aiAssistant.core

import android.app.Application
import android.content.Context
import ar.edu.um.tif.aiAssistant.core.client.AssistantApiClient
import ar.edu.um.tif.aiAssistant.service.WakeWordServiceManager
import com.justai.aimybox.Aimybox
import com.justai.aimybox.components.AimyboxProvider
import com.justai.aimybox.core.Config
import com.justai.aimybox.speechkit.google.platform.GooglePlatformTextToSpeech
import com.justai.aimybox.speechkit.kaldi.KaldiAssets
import com.justai.aimybox.speechkit.kaldi.KaldiSpeechToText
import com.justai.aimybox.speechkit.kaldi.KaldiVoiceTrigger
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class AimyboxApplication : Application(), AimyboxProvider {

    // Use Provider to avoid circular dependency issues during initialization
    @Inject
    lateinit var assistantApiClientProvider: Provider<AssistantApiClient>

    // Inject WakeWordServiceManager to ensure it's initialized at app startup
    @Inject
    lateinit var wakeWordServiceManager: WakeWordServiceManager

    companion object {
        private const val TAG = "AimyboxApplication"

        init {
            System.setProperty("jna.nosys", "true")
        }
    }

    override fun onCreate() {
        super.onCreate()
        System.setProperty("jna.nosys", "true")

        // Initialize wake word service manager and health monitoring
        checkAndRestartServiceIfNeeded()
    }

    private fun checkAndRestartServiceIfNeeded() {
        // Check if the service should be running but isn't
        if (wakeWordServiceManager.isServiceEnabled) {
            val isServiceRunning = wakeWordServiceManager.isServiceRunning()
            if (!isServiceRunning) {
                android.util.Log.d(TAG, "Service is enabled but not running - restarting it")
                wakeWordServiceManager.startService()
            } else {
                android.util.Log.d(TAG, "Service is enabled and already running")
            }
        } else {
            android.util.Log.d(TAG, "Service is disabled in settings")
        }
    }

    override val aimybox by lazy { createAimybox(this) }

    private fun createAimybox(context: Context): Aimybox {
        val locale = Locale("es", "AR")
        val assets = KaldiAssets.Companion.fromApkAssets(this, "vosk-model-small-es-0.42")

        val voiceTrigger = KaldiVoiceTrigger(assets, listOf("hola iris"))
        val textToSpeech = GooglePlatformTextToSpeech(context, locale)
        val speechToText = KaldiSpeechToText(assets)

        // Get the AssistantApiClient from the provider (skills are now injected via Dagger)
        val dialogApi = assistantApiClientProvider.get()

        val aimyboxConfig = Config.Companion.create(speechToText, textToSpeech, dialogApi) {
            this.voiceTrigger = voiceTrigger
//            this.recognitionBehavior = Config.RecognitionBehavior.ALLOW_OVERRIDE
        }

        val aimybox = Aimybox(aimyboxConfig, context)
        return aimybox
    }
}
