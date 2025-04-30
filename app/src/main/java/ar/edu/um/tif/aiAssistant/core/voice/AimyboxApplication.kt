package ar.edu.um.tif.aiAssistant.core.voice

import android.app.Application
import android.content.Context
import com.justai.aimybox.Aimybox
import com.justai.aimybox.assistant.api.DummyDialogApi
import com.justai.aimybox.components.AimyboxProvider
import com.justai.aimybox.core.Config
import com.justai.aimybox.speechkit.google.platform.GooglePlatformSpeechToText
import com.justai.aimybox.speechkit.google.platform.GooglePlatformTextToSpeech
import java.util.UUID

class AimyboxApplication : Application(), AimyboxProvider {

    override val aimybox by lazy { createAimybox(this) }

    private fun createAimybox(context: Context): Aimybox {
        val unitId = UUID.randomUUID().toString()

        val textToSpeech = GooglePlatformTextToSpeech(context)
        val speechToText = GooglePlatformSpeechToText(context)

        val dialogApi = DummyDialogApi()

        val aimyboxConfig = Config.create(speechToText, textToSpeech, dialogApi)
        return Aimybox(aimyboxConfig, context)
    }
}