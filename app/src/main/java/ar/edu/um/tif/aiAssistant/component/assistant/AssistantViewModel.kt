package ar.edu.um.tif.aiAssistant.component.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.data.repository.AssistantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val content: String,
    val isFromUser: Boolean
)

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val authError: Boolean = false
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistantRepository: AssistantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        // You could load conversation history here
        // loadConversationHistory()
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return

        // Add user message to the chat
        addMessage(ChatMessage(content = message, isFromUser = true))

        // Send to AI assistant
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                val result = assistantRepository.sendUserRequest(message)

                result.fold(
                    onSuccess = { serverResponse ->
                        serverResponse.let {
                            addMessage(ChatMessage(content = it.server_reply, isFromUser = false))

                            // Process any skills/actions returned from the server
                            it.skills?.forEach { skill ->
                                // Handle different skills as needed
                                // e.g., executeSkill(skill)
                            }
                        }

                        _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                    },
                    onFailure = { exception ->
                        if (exception.message?.contains("401") == true) {
                            // Authentication error
                            _uiState.update { it.copy(
                                isLoading = false,
                                errorMessage = "Authentication error. Please log in again.",
                                authError = true
                            )}
                        } else {
                            // Other error
                            _uiState.update { it.copy(
                                isLoading = false,
                                errorMessage = "Error: ${exception.message}"
                            )}
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.localizedMessage}"
                )}
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update {
            it.copy(messages = it.messages + message)
        }
    }

    private fun loadConversationHistory() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                val result = assistantRepository.getConversationHistory()

                result.fold(
                    onSuccess = { historyData ->
                        // Process conversation history from response
                        // The exact implementation would depend on your API response format

                        _uiState.update { it.copy(isLoading = false) }
                    },
                    onFailure = { exception ->
                        if (exception.message?.contains("401") == true) {
                            // Authentication error
                            _uiState.update { it.copy(
                                isLoading = false,
                                authError = true
                            )}
                        } else {
                            // Other error
                            _uiState.update { it.copy(
                                isLoading = false,
                                errorMessage = "Failed to load conversation history"
                            )}
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.localizedMessage}"
                )}
            }
        }
    }
}
