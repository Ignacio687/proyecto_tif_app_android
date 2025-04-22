package ar.edu.um.programacion2.computech.component.loading

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.programacion2.computech.core.TokenManager
import ar.edu.um.programacion2.computech.core.customException.UnauthorizedAccessException
import kotlinx.coroutines.launch

class LoadingViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)

    fun fetchAccount(navigateToHome: () -> Unit, navigateToLogin: () -> Unit) {
        viewModelScope.launch {
            try {
                val token = tokenManager.getToken() ?: throw UnauthorizedAccessException("Token not found")
                Log.d("LoadingViewModel", "Fetching account with token: $token")
                val account = LoadingClient.getAccount(token)
                navigateToHome()
            } catch (e: UnauthorizedAccessException) {
                Log.e("LoadingViewModel", e.message, e)
                navigateToLogin()
            } catch (e: Exception) {
                Log.e("LoadingViewModel", e.message, e)
                navigateToLogin()
            }
        }
    }
}