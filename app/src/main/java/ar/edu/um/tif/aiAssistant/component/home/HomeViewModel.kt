// File: app/src/main/java/ar/edu/um/tif/aiAssistant/component/home/HomeViewModel.kt
package ar.edu.um.tif.aiAssistant.component.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ar.edu.um.tif.aiAssistant.BuildConfig
import ar.edu.um.tif.aiAssistant.core.TokenManager
import ar.edu.um.tif.aiAssistant.core.voice.PorcupineAPIKeyManager

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _token = MutableLiveData<String?>()
    val token: LiveData<String?> = _token

    private val _validLogin = MutableLiveData<Boolean>()
    val validLogin: LiveData<Boolean> = _validLogin

    private val tokenManager = TokenManager(application)

    init {
        _token.value = tokenManager.getToken()
    }

    fun logout(navigateToLogin: () -> Unit) {
        tokenManager.clearToken()
        _validLogin.postValue(false)
        navigateToLogin()
    }
}