package ar.edu.um.tif.aiAssistant.component.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.TokenManager
import ar.edu.um.tif.aiAssistant.core.customException.UnauthorizedAccessException
import kotlinx.coroutines.launch

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
        viewModelScope.launch {
            _validLogin.value = true
            tokenManager.clearToken()
            navigateToLogin()
        }
    }
}