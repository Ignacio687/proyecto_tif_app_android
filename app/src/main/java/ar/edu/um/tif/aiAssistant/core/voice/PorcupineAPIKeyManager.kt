// File: app/src/main/java/ar/edu/um/tif/aiAssistant/core/voice/PorcupineManager.kt
package ar.edu.um.tif.aiAssistant.core.voice

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ai.picovoice.porcupine.PorcupineManager as PvPorcupineManager

class PorcupineAPIKeyManager(context: Context) {
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
}