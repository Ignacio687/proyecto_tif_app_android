package ar.edu.um.tif.aiAssistant.component.assistant

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.auth.AuthManager
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.UserRequest
import ar.edu.um.tif.aiAssistant.core.data.repository.AssistantRepository
import ar.edu.um.tif.aiAssistant.core.client.AssistantApiClient
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

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
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
    private val assistantApiClient: AssistantApiClient,
    private val authManager: AuthManager,
    private val aimybox: Aimybox
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    // AimyBox related properties - now using injected instance
    private val _aimyboxDelegate: AimyboxAssistantViewModel by lazy {
        AimyboxAssistantViewModel(aimybox)
    }

    private val _widgets: LiveData<List<AssistantWidget>> by lazy {
        _aimyboxDelegate.widgets
    }

    private val _aimyboxState: LiveData<Aimybox.State> by lazy {
        _aimyboxDelegate.aimyboxState
    }

    // Public accessors
    val widgets: LiveData<List<AssistantWidget>>
        get() = _widgets

    val aimyboxState: LiveData<Aimybox.State>
        get() = _aimyboxState

    init {
        // Load conversation history when ViewModel is created
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
                        // We don't need to do anything with LOGGED_OUT in AssistantScreen
                    }
                }
            }
        }
    }

    /**
     * Handle AimyBox button click
     * Requires RECORD_AUDIO permission
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onAssistantButtonClick() {
        _aimyboxDelegate.onAssistantButtonClick()
    }

    /**
     * Handle AimyBox button click
     * Requires RECORD_AUDIO permission
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onButtonClick(button: Button) {
        _aimyboxDelegate.onButtonClick(button)
    }

    /**
     * Mute AimyBox
     */
    fun muteAimybox() {
        _aimyboxDelegate.muteAimybox()
    }

    /**
     * Unmute AimyBox
     */
    fun unmuteAimybox() {
        _aimyboxDelegate.unmuteAimybox()
    }

    /**
     * Set initial phrase
     */
    fun setInitialPhrase(text: String) {
        _aimyboxDelegate.setInitialPhrase(text)
    }

    /**
     * Send a text message to the assistant
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        // Add user message to the chat
        addMessage(ChatMessage(content = message, isFromUser = true))

        // Use Aimybox's sendRequest method to trigger the full DialogApi flow including skills
        viewModelScope.launch {
            try {
                _uiState.update { currentState -> currentState.copy(isLoading = true, errorMessage = null) }

                // Use Aimybox's sendRequest which will trigger the full DialogApi flow including skills
                aimybox.sendRequest(message)

                _uiState.update { currentState -> currentState.copy(isLoading = false, errorMessage = null) }
            } catch (e: Exception) {
                // Check specifically for authentication errors
                if (e is ar.edu.um.tif.aiAssistant.core.customException.UnauthorizedAccessException) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        authError = true
                    )}
                } else {
                    _uiState.update { currentState -> currentState.copy(
                        isLoading = false,
                        errorMessage = "Unable to process your request. Please try again later."
                    )}

                    // Add user-friendly error message to chat
                    addMessage(ChatMessage(
                        content = "Sorry, I'm having trouble processing your request right now.",
                        isFromUser = false
                    ))
                }
            }
        }
    }

    /**
     * Add a message from the user to the chat (for voice interactions)
     */
    fun addVoiceRequestMessage(text: String) {
        if (text.isBlank()) return

        // Check if this message is already in the chat to avoid duplicates
        val existingMessages = _uiState.value.messages
        val isAlreadyAdded = existingMessages.any {
            it.isFromUser && it.content == text
        }

        if (!isAlreadyAdded) {
            addMessage(ChatMessage(
                content = text,
                isFromUser = true
            ))
        }
    }

    /**
     * Add a response from the assistant to the chat (for voice interactions)
     */
    fun addVoiceResponseMessage(text: String) {
        if (text.isBlank()) return

        // Check if this message is already in the chat to avoid duplicates
        val existingMessages = _uiState.value.messages
        val isAlreadyAdded = existingMessages.any {
            !it.isFromUser && it.content == text
        }

        if (!isAlreadyAdded) {
            addMessage(ChatMessage(
                content = text,
                isFromUser = false
            ))
        }
    }

    /**
     * Add a message to the chat
     */
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
                                    android.util.Log.e("AssistantViewModel", "Failed to parse timestamp: ${conversation.timestamp}", e)
                                    System.currentTimeMillis()
                                }

                                // For each conversation create a pair of messages in the right order
                                val userMessage = ChatMessage(
                                    id = "${timestamp}_user",
                                    content = conversation.userInput,
                                    isFromUser = true,
                                    timestamp = timestamp
                                )
                                val assistantMessage = ChatMessage(
                                    id = "${timestamp}_assistant",
                                    content = conversation.serverReply,
                                    isFromUser = false,
                                    timestamp = timestamp
                                )

                                // Return the pair with user message first, then assistant message
                                listOf(assistantMessage, userMessage)
                            } catch (e: Exception) {
                                android.util.Log.e("AssistantViewModel", "Error processing conversation", e)
                                emptyList()
                            }
                        }

                        _uiState.update { currentState -> currentState.copy(
                            messages = messages.reversed(),  // Reverse the order so oldest appear first, newest last
                            isLoading = false,
                            errorMessage = null
                        )}

                        // Log messages only when they're loaded from server
                        android.util.Log.d("ConversationHistory", "=== MESSAGES LOADED FROM SERVER ===")
                        messages.reversed().forEachIndexed { index, message ->
                            android.util.Log.d("ConversationHistory", "Message $index: isFromUser=${message.isFromUser}, content=${message.content}, timestamp=${message.timestamp}")
                        }
                        android.util.Log.d("ConversationHistory", "=== END SERVER MESSAGES (${messages.size} total) ===")
                    },
                    onFailure = { error ->
                        // Log the detailed error for debugging
                        android.util.Log.e("AssistantViewModel", "History loading error", error)

                        // Set a user-friendly error message
                        _uiState.update { currentState -> currentState.copy(
                            isLoading = false,
                            errorMessage = "Unable to load conversation history. Please try again later."
                        )}
                    }
                )
            } catch (e: Exception) {
                // Log the detailed exception
                android.util.Log.e("AssistantViewModel", "Exception loading history", e)

                _uiState.update { currentState -> currentState.copy(
                    isLoading = false,
                    errorMessage = "Unable to load conversation history. Please try again later."
                )}
            }
        }
    }
}
