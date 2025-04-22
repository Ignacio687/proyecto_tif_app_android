package ar.edu.um.programacion2.computech.component.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import ar.edu.um.programacion2.computech.core.customException.UnauthorizedAccessException
import ar.edu.um.programacion2.computech.core.TokenManager
import ar.edu.um.programacion2.computech.component.login.clientDataModel.Authenticate
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> = _userName

    private val _password = MutableLiveData<String>()
    val password: LiveData<String> = _password

    private val _isLoginEnabled = MutableLiveData<Boolean>()
    val isLoginEnabled: LiveData<Boolean> = _isLoginEnabled

    private val _isUserNameValid = MutableLiveData<Boolean>()
    val isUserNameValid: LiveData<Boolean> = _isUserNameValid

    private val _isPasswordValid = MutableLiveData<Boolean>()
    val isPasswordValid: LiveData<Boolean> = _isPasswordValid

    private val _isInvalidCredentials = MutableLiveData<Boolean>()
    val isInvalidCredentials: LiveData<Boolean> = _isInvalidCredentials

    private val _isLoginError = MutableLiveData<Boolean>()
    val isLoginError: LiveData<Boolean> = _isLoginError

    private val tokenManager = TokenManager(application)

    init {
        _userName.value = ""
        _password.value = ""
        _isLoginEnabled.value = false
        _isUserNameValid.value = false
        _isPasswordValid.value = false
        _isInvalidCredentials.value = true
        _isLoginError.value = false
    }

    fun onUserNameChanged(newUserName: String) {
        _userName.value = newUserName
        _isUserNameValid.value = newUserName.length in 1..50
        validateLogin()
    }

    fun onPasswordChanged(newPassword: String) {
        _password.value = newPassword
        _isPasswordValid.value = newPassword.length in 4..100
        validateLogin()
    }

    private fun validateLogin() {
        _isLoginEnabled.value = _isUserNameValid.value == true && _isPasswordValid.value == true
    }

    fun authenticate(navigateToHome: () -> Unit) {
        val username = _userName.value ?: return
        val password = _password.value ?: return
        val authenticate = Authenticate(username, password, rememberMe = true)

        viewModelScope.launch {
            try {
                val token = LoginClient.authenticate(authenticate)
                tokenManager.saveToken(token.token)
                _isInvalidCredentials.value = true
                _isLoginError.value = false
                navigateToHome()
            } catch (e: UnauthorizedAccessException) {
                Log.e("Login", e.message, e)
                _isInvalidCredentials.value = false
                _isLoginEnabled.value = false
            } catch (e: Exception) {
                Log.e("LoginViewModel", e.message, e)
                _isLoginError.value = true
                _isLoginEnabled.value = false
            }
        }
    }
}