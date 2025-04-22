package ar.edu.um.programacion2.ai_assistant.component.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import ar.edu.um.programacion2.ai_assistant.core.TokenManager
import ar.edu.um.programacion2.ai_assistant.core.customException.UnauthorizedAccessException
import ar.edu.um.programacion2.ai_assistant.component.home.clientDataModel.Device
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _token = MutableLiveData<String?>()
    val token: LiveData<String?> = _token

    private val _devices = MutableLiveData<List<Device>>()
    val devices: LiveData<List<Device>> = _devices

    private val _validLogin = MutableLiveData<Boolean>()
    val validLogin: LiveData<Boolean> = _validLogin

    private val tokenManager = TokenManager(application)

    init {
        _token.value = tokenManager.getToken()
        fetchDevices()
    }

    private fun fetchDevices() {
        viewModelScope.launch {
            try {
                android.util.Log.d("HomeViewModel", "Fetching devices")
                val devicesList = HomeClient.getDevices(_token.value.toString())
                _devices.value = devicesList
            } catch (e: UnauthorizedAccessException) {
                Log.e("HomeViewModel", e.message, e)
                _validLogin.value = false
            } catch (e: Exception) {
                Log.e("HomeViewModel", e.message, e)
            }
        }
    }

    fun logout(navigateToLogin: () -> Unit) {
        viewModelScope.launch {
            _validLogin.value = true
            tokenManager.clearToken()
            navigateToLogin()
        }
    }
}