package ar.edu.um.tif.aiAssistant.service

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.auth.AuthManager
import ar.edu.um.tif.aiAssistant.core.data.repository.AssistantRepository
import com.justai.aimybox.Aimybox
import com.justai.aimybox.components.AimyboxAssistantViewModel
import com.justai.aimybox.components.widget.AssistantWidget
import com.justai.aimybox.components.widget.Button
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PopupChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class PopupUiState(
    val messages: List<PopupChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val authError: Boolean = false,
    val userHasInteracted: Boolean = false
)

@HiltViewModel
class AssistantPopupViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val aimybox: Aimybox,
    private val assistantRepository: AssistantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PopupUiState())
    val uiState: StateFlow<PopupUiState> = _uiState.asStateFlow()

    // AimyBox related properties - using injected instance
    private val _aimyboxDelegate: AimyboxAssistantViewModel by lazy {
        AimyboxAssistantViewModel(aimybox)
    }

    private val _widgets: LiveData<List<AssistantWidget>> by lazy {
        _aimyboxDelegate.widgets
    }

    private val _aimyboxState: LiveData<Aimybox.State> by lazy {
        _aimyboxDelegate.aimyboxState
    }

    // Public accessors - made non-nullable to match AssistantViewModel
    val widgets: LiveData<List<AssistantWidget>>
        get() = _widgets

    val aimyboxState: LiveData<Aimybox.State>
        get() = _aimyboxState

    init {
        // Load conversation history when ViewModel is created (like main AssistantViewModel)
        loadConversationHistory()
        // Observe authentication events
        observeAuthEvents()
    }

    private fun observeAuthEvents() {
        viewModelScope.launch {
            authManager.authEvents.collect { event ->
                when (event) {
                    AuthManager.AuthEvent.AUTH_ERROR -> {
                        _uiState.update { it.copy(authError = true) }
                    }
                    AuthManager.AuthEvent.LOGGED_OUT -> {
                        // Handle logout if needed
                    }
                }
            }
        }
    }

    /**
     * Verify if user is authenticated
     */
    suspend fun verifyAuthentication(): Boolean {
        return try {
            authManager.verifyAuthentication()
        } catch (_: Exception) {
            android.util.Log.e("AssistantPopupViewModel", "Error verifying authentication")
            false
        }
    }

    /**
     * Handle microphone button click
     * Requires RECORD_AUDIO permission
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onMicButtonClick() {
        markUserInteraction()
        _aimyboxDelegate.onAssistantButtonClick()
    }

    /**
     * Handle AimyBox button click (for buttons in widgets)
     * Requires RECORD_AUDIO permission
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onButtonClick(button: Button) {
        markUserInteraction()
        _aimyboxDelegate.onButtonClick(button)
    }

    /**
     * Send a text message
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        markUserInteraction()

        // Add user message to chat
        addUserMessage(message)

        // Set loading state
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                // Send message via Aimybox - this handles permissions internally
                aimybox.sendRequest(message)
            } catch (e: SecurityException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Permisos requeridos para el funcionamiento del asistente"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error al enviar mensaje: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Add a user message to the chat
     */
    private fun addUserMessage(content: String) {
        val message = PopupChatMessage(
            content = content,
            isFromUser = true
        )
        _uiState.update {
            it.copy(messages = it.messages + message)
        }
    }

    /**
     * Add a voice response message from Aimybox to the chat
     */
    fun addVoiceResponseMessage(content: String) {
        // Check if this message is already in the chat to avoid duplicates
        val existingMessages = _uiState.value.messages
        val isAlreadyAdded = existingMessages.any {
            !it.isFromUser && it.content == content
        }

        if (!isAlreadyAdded) {
            val message = PopupChatMessage(
                content = content,
                isFromUser = false
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + message,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Add a voice request message from user to the chat
     */
    fun addVoiceRequestMessage(content: String) {
        // Check if this message is already in the chat to avoid duplicates
        val existingMessages = _uiState.value.messages
        val isAlreadyAdded = existingMessages.any {
            it.isFromUser && it.content == content
        }

        if (!isAlreadyAdded) {
            val message = PopupChatMessage(
                content = content,
                isFromUser = true
            )
            _uiState.update {
                it.copy(messages = it.messages + message)
            }
        }
    }

    /**
     * Filter responses that shouldn't be shown in chat
     * (based on AssistantViewModel pattern)
     */
    fun shouldFilterResponse(text: String): Boolean {
        val lowerText = text.lowercase()

        // Filter out common system responses that aren't useful in chat
        return lowerText.contains("listening") ||
               lowerText.contains("processing") ||
               lowerText.contains("¿en qué puedo ayudarte?") ||
               lowerText.contains("hello, how can i help you") ||
               lowerText.isEmpty() ||
               lowerText.isBlank()
    }

    /**
     * Mark that user has interacted with the popup
     */
    private fun markUserInteraction() {
        _uiState.update { it.copy(userHasInteracted = true) }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Load conversation history from repository (adapted from main AssistantViewModel)
     */
    fun loadConversationHistory() {
        viewModelScope.launch {
            try {
                _uiState.update { currentState -> currentState.copy(isLoading = true) }

                val result = assistantRepository.getConversationHistory()

                result.fold(
                    onSuccess = { historyResponse ->
                        // Process conversation history data - server already sends in order (0 = most recent)
                        val messages = historyResponse.conversations.flatMap { conversation ->
                            try {
                                // Parse the timestamp string to get date and time
                                val timestamp = try {
                                    val regex = """(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})""".toRegex()
                                    val matchResult = regex.find(conversation.timestamp)

                                    if (matchResult != null) {
                                        // Extract date components to create a timestamp
                                        val (date, hours, minutes) = matchResult.destructured
                                        val year = date.substring(0, 4).toInt()
                                        val month = date.substring(5, 7).toInt() - 1 // Month is 0-based in Calendar
                                        val day = date.substring(8, 10).toInt()
                                        val hour = hours.toInt()
                                        val minute = minutes.toInt()

                                        // Create a calendar with the extracted date and time
                                        val calendar = java.util.Calendar.getInstance()
                                        calendar.set(year, month, day, hour, minute, 0)
                                        calendar.set(java.util.Calendar.MILLISECOND, 0)
                                        calendar.timeInMillis
                                    } else {
                                        System.currentTimeMillis()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("AssistantPopupViewModel", "Failed to parse timestamp: ${conversation.timestamp}", e)
                                    System.currentTimeMillis()
                                }

                                // For each conversation create a pair of messages in the right order
                                // Convert ChatMessage to PopupChatMessage
                                val userMessage = PopupChatMessage(
                                    id = "${timestamp}_user",
                                    content = conversation.userInput,
                                    isFromUser = true,
                                    timestamp = timestamp
                                )
                                val assistantMessage = PopupChatMessage(
                                    id = "${timestamp}_assistant",
                                    content = conversation.serverReply,
                                    isFromUser = false,
                                    timestamp = timestamp
                                )

                                // Return the pair with assistant message first, then user message (same order as main)
                                listOf(assistantMessage, userMessage)
                            } catch (e: Exception) {
                                android.util.Log.e("AssistantPopupViewModel", "Error processing conversation", e)
                                emptyList()
                            }
                        }

                        _uiState.update { currentState -> currentState.copy(
                            messages = messages.reversed(),  // Reverse the order so oldest appear first, newest last
                            isLoading = false,
                            errorMessage = null
                        )}

                        // Log messages only when they're loaded from server
                        android.util.Log.d("PopupConversationHistory", "=== POPUP MESSAGES LOADED FROM SERVER ===")
                        messages.reversed().forEachIndexed { index, message ->
                            android.util.Log.d("PopupConversationHistory", "Message $index: isFromUser=${message.isFromUser}, content=${message.content}, timestamp=${message.timestamp}")
                        }
                        android.util.Log.d("PopupConversationHistory", "=== END POPUP SERVER MESSAGES (${messages.size} total) ===")
                    },
                    onFailure = { error ->
                        // Log the detailed error for debugging
                        android.util.Log.e("AssistantPopupViewModel", "History loading error", error)

                        // Set a user-friendly error message
                        _uiState.update { currentState -> currentState.copy(
                            isLoading = false,
                            errorMessage = "Unable to load conversation history. Please try again later."
                        )}
                    }
                )
            } catch (e: Exception) {
                // Log the detailed exception
                android.util.Log.e("AssistantPopupViewModel", "Exception loading history", e)

                _uiState.update { currentState -> currentState.copy(
                    isLoading = false,
                    errorMessage = "Unable to load conversation history. Please try again later."
                )}
            }
        }
    }
}
