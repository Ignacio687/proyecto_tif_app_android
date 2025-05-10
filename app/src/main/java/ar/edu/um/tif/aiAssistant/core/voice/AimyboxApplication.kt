package ar.edu.um.tif.aiAssistant.core.voice

import android.app.Application
import android.content.Context
import com.justai.aimybox.Aimybox
import com.justai.aimybox.assistant.api.DummyDialogApi
import com.justai.aimybox.components.AimyboxProvider
import com.justai.aimybox.core.Config
import com.justai.aimybox.core.Config.RecognitionBehavior
import com.justai.aimybox.speechkit.google.platform.GooglePlatformSpeechToText
import com.justai.aimybox.speechkit.google.platform.GooglePlatformTextToSpeech
import com.justai.aimybox.speechkit.kaldi.KaldiAssets
import com.justai.aimybox.speechkit.kaldi.KaldiVoiceTrigger
import java.util.Locale

class AimyboxApplication : Application(), AimyboxProvider {

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
        val assets = KaldiAssets.fromApkAssets(this, "vosk-model-small-es-0.42")
        val voiceTrigger = KaldiVoiceTrigger(assets, listOf("alexa", "jarvis"))

        val textToSpeech = GooglePlatformTextToSpeech(context, locale)
        val speechToText = GooglePlatformSpeechToText(context, locale)

        val dialogApi = LLMDialogAPI()

        val aimyboxConfig = Config.create(speechToText, textToSpeech, dialogApi) {
            this.voiceTrigger = voiceTrigger
            this.recognitionBehavior = RecognitionBehavior.ALLOW_OVERRIDE
        }
        return Aimybox(aimyboxConfig, context)
    }
}