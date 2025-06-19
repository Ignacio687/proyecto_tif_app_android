package ar.edu.um.tif.aiAssistant.core

import android.app.Application
import android.content.Context
import ar.edu.um.tif.aiAssistant.core.client.AssistantApiClient
import com.justai.aimybox.Aimybox
import com.justai.aimybox.components.AimyboxProvider
import com.justai.aimybox.core.Config
import com.justai.aimybox.speechkit.google.platform.GooglePlatformSpeechToText
import com.justai.aimybox.speechkit.google.platform.GooglePlatformTextToSpeech
import com.justai.aimybox.speechkit.kaldi.KaldiAssets
import com.justai.aimybox.speechkit.kaldi.KaldiVoiceTrigger
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class AimyboxApplication : Application(), AimyboxProvider {

    @Inject
    lateinit var assistantApiClient: AssistantApiClient

    companion object {
        init {
            System.setProperty("jna.nosys", "true")
        }
    }

    override fun onCreate() {
        super.onCreate()
        System.setProperty("jna.nosys", "true")
    }

    override val aimybox by lazy { createAimybox(this) }

    private fun createAimybox(context: Context): Aimybox {
        val locale = Locale("es", "ES")
        val assets = KaldiAssets.Companion.fromApkAssets(this, "vosk-model-small-es-0.42")
        val voiceTrigger = KaldiVoiceTrigger(assets, listOf("cortana"))

        val textToSpeech = GooglePlatformTextToSpeech(context, locale)
        val speechToText = GooglePlatformSpeechToText(context, locale)

        // Use the injected assistantApiClient as the dialog API
        val dialogApi = assistantApiClient

        val aimyboxConfig = Config.Companion.create(speechToText, textToSpeech, dialogApi) {
            this.voiceTrigger = voiceTrigger
            this.recognitionBehavior = Config.RecognitionBehavior.ALLOW_OVERRIDE
        }
        return Aimybox(aimyboxConfig, context)
    }
}