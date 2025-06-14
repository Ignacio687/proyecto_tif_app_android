package ar.edu.um.tif.aiAssistant.component.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserInfo()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            val name = authRepository.name.first()
            val email = authRepository.email.first()

            _uiState.update {
                it.copy(
                    userName = name,
                    userEmail = email
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.clearAuthData()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }
}

data class HomeUiState(
    val userName: String? = null,
    val userEmail: String? = null,
    val isLoggedOut: Boolean = false
)
