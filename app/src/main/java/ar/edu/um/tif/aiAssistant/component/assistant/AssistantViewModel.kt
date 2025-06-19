package ar.edu.um.tif.aiAssistant.component.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.UserRequest
import ar.edu.um.tif.aiAssistant.core.data.repository.AssistantRepository
import ar.edu.um.tif.aiAssistant.core.client.AssistantApiClient
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
    private val assistantRepository: AssistantRepository,
    private val assistantApiClient: AssistantApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        // Load conversation history when ViewModel is created
        loadConversationHistory()
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return

        // Add user message to the chat
        addMessage(ChatMessage(content = message, isFromUser = true))

        // Send to AI assistant
        viewModelScope.launch {
            try {
                _uiState.update { currentState -> currentState.copy(isLoading = true, errorMessage = null) }

                // Create request and use the DialogApi interface directly
                val request = UserRequest(userReq = message)
                val response = assistantApiClient.send(request)

                // Get the text from the first reply
                // TextReply in AimyBox has a 'text' property we need to cast
                val replyText = when (val firstReply = response.replies.firstOrNull()) {
                    is com.justai.aimybox.model.reply.TextReply -> firstReply.text
                    else -> "No response from assistant"
                }

                // Add assistant's response to the chat
                addMessage(ChatMessage(content = replyText, isFromUser = false))

                _uiState.update { currentState -> currentState.copy(isLoading = false, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { currentState -> currentState.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.localizedMessage}"
                )}

                // Add error message to chat for better UX
                addMessage(ChatMessage(
                    content = "Sorry, I encountered an error: ${e.message}",
                    isFromUser = false
                ))
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update { currentState ->
            currentState.copy(messages = currentState.messages + message)
        }
    }

    fun loadConversationHistory() {
        viewModelScope.launch {
            try {
                _uiState.update { currentState -> currentState.copy(isLoading = true) }

                val result = assistantRepository.getConversationHistory()

                result.fold(
                    onSuccess = { historyData ->
                        // Process history data based on your API response structure
                        // This is a placeholder implementation
                        val messages = (historyData["messages"] as? List<Map<String, Any>>)?.map { messageData ->
                            ChatMessage(
                                id = (messageData["id"] as? String) ?: System.currentTimeMillis().toString(),
                                content = (messageData["content"] as? String) ?: "",
                                isFromUser = (messageData["is_user"] as? Boolean) ?: false
                            )
                        } ?: emptyList()

                        _uiState.update { currentState -> currentState.copy(
                            messages = messages,
                            isLoading = false
                        )}
                    },
                    onFailure = { error ->
                        _uiState.update { currentState -> currentState.copy(
                            isLoading = false,
                            errorMessage = "Failed to load conversation history: ${error.message}"
                        )}
                    }
                )
            } catch (e: Exception) {
                _uiState.update { currentState -> currentState.copy(
                    isLoading = false,
                    errorMessage = "Error loading history: ${e.localizedMessage}"
                )}
            }
        }
    }
}
