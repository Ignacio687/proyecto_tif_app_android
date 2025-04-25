// File: app/src/main/java/ar/edu/um/tif/aiAssistant/core/voice/PorcupineManager.kt
package ar.edu.um.tif.aiAssistant.core.voice

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineActivationException
import ai.picovoice.porcupine.PorcupineActivationLimitException
import ai.picovoice.porcupine.PorcupineActivationRefusedException
import ai.picovoice.porcupine.PorcupineActivationThrottledException
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineInvalidArgumentException
import ai.picovoice.porcupine.PorcupineManager as PvPorcupineManager

class PorcupineManager(context: Context) {
    companion object {
        private const val PREFS_NAME = "porcupine_prefs"
        private const val KEY_API = "porcupine_api_key"
    }

    private var porcupineManager: PvPorcupineManager? = null

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API, apiKey).apply()
    }

    fun getApiKey(): String? = prefs.getString(KEY_API, null)

    fun startHotwordDetection(
        context: Context,
        onHotwordDetected: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val apiKey = getApiKey() ?: run {
            onError("API key not found")
            return
        }
        try {
            porcupineManager = PvPorcupineManager.Builder()
                .setAccessKey(apiKey)
                .setKeyword(Porcupine.BuiltInKeyword.PORCUPINE)
                .setSensitivity(0.7f)
                .build(context, onHotwordDetected)
            porcupineManager?.start()
        } catch (e: PorcupineInvalidArgumentException) {
            onError("Invalid argument: ${e.message}")
        } catch (e: PorcupineActivationException) {
            onError("AccessKey activation error: ${e.message}")
        } catch (e: PorcupineActivationLimitException) {
            onError("AccessKey reached its device limit: ${e.message}")
        } catch (e: PorcupineActivationRefusedException) {
            onError("AccessKey refused: ${e.message}")
        } catch (e: PorcupineActivationThrottledException) {
            onError("AccessKey has been throttled: ${e.message}")
        } catch (e: PorcupineException) {
            onError("Failed to initialize Porcupine: ${e.message}")
        }
    }

    fun stopHotwordDetection() {
        porcupineManager?.stop()
        porcupineManager?.delete()
        porcupineManager = null
    }
}