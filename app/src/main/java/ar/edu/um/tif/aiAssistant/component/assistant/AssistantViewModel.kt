package ar.edu.um.tif.aiAssistant.component.assistant

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.time.Instant
import java.time.format.DateTimeParseException
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
    private val assistantApiClient: AssistantApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    // AimyBox related properties - using safer initialization approach
    private var _aimyboxDelegate: AimyboxAssistantViewModel? = null
    private var _widgets: LiveData<List<AssistantWidget>>? = null
    private var _aimyboxState: LiveData<Aimybox.State>? = null

    // Safe accessors that won't throw exceptions if not initialized
    val widgets: LiveData<List<AssistantWidget>>?
        get() = _widgets

    val aimyboxState: LiveData<Aimybox.State>?
        get() = _aimyboxState

    init {
        // Load conversation history when ViewModel is created
        loadConversationHistory()
    }

    /**
     * Initialize the AimyBox delegate
     * This must be called before using any AimyBox features
     */
    fun initializeAimybox(aimybox: Aimybox) {
        // Create the delegate with the provided Aimybox instance
        _aimyboxDelegate = AimyboxAssistantViewModel(aimybox)

        // Get references to the delegate's properties
        _widgets = _aimyboxDelegate?.widgets
        _aimyboxState = _aimyboxDelegate?.aimyboxState
    }

    /**
     * Handle AimyBox button click
     * Requires RECORD_AUDIO permission
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onAssistantButtonClick() {
        _aimyboxDelegate?.onAssistantButtonClick()
    }

    /**
     * Handle AimyBox button click
     * Requires RECORD_AUDIO permission
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onButtonClick(button: Button) {
        _aimyboxDelegate?.onButtonClick(button)
    }

    /**
     * Mute AimyBox
     */
    fun muteAimybox() {
        _aimyboxDelegate?.muteAimybox()
    }

    /**
     * Unmute AimyBox
     */
    fun unmuteAimybox() {
        _aimyboxDelegate?.unmuteAimybox()
    }

    /**
     * Set initial phrase
     */
    fun setInitialPhrase(text: String) {
        _aimyboxDelegate?.setInitialPhrase(text)
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
                    errorMessage = "Unable to process your request. Please try again later."
                )}

                // Log the detailed error
                android.util.Log.e("AssistantViewModel", "Error sending message", e)

                // Add user-friendly error message to chat
                addMessage(ChatMessage(
                    content = "Sorry, I'm having trouble processing your request right now.",
                    isFromUser = false
                ))
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
