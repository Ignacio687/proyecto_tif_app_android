// File: app/src/main/java/ar/edu/um/tif/aiAssistant/component/home/HomeViewModel.kt
package ar.edu.um.tif.aiAssistant.component.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ar.edu.um.tif.aiAssistant.BuildConfig
import ar.edu.um.tif.aiAssistant.core.TokenManager
import ar.edu.um.tif.aiAssistant.core.voice.PorcupineManager

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _token = MutableLiveData<String?>()
    val token: LiveData<String?> = _token

    private val _validLogin = MutableLiveData<Boolean>()
    val validLogin: LiveData<Boolean> = _validLogin

    private val _hotwordHeard = MutableLiveData<Boolean>(false)
    val hotwordHeard: LiveData<Boolean> = _hotwordHeard

    private val tokenManager = TokenManager(application)
    private val porcupineManager = PorcupineManager(getApplication())

    init {
        _token.value = tokenManager.getToken()
        if (porcupineManager.getApiKey().isNullOrEmpty()) {
            porcupineManager.saveApiKey(BuildConfig.PORCUPINE_API_KEY)
        }
        porcupineManager.startHotwordDetection(
            context = getApplication(),
            onHotwordDetected = { _hotwordHeard.postValue(true) },
            onError = { errorMsg ->
                android.util.Log.e("HomeViewModel", "Hotword detection error: $errorMsg")
                _hotwordHeard.postValue(false)
            }
        )
    }

    fun logout(navigateToLogin: () -> Unit) {
        tokenManager.clearToken()
        _validLogin.postValue(false)
        navigateToLogin()
    }
}