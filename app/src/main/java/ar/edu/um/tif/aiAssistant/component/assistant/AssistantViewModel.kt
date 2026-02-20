package ar.edu.um.tif.aiAssistant.component.assistant

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.auth.AuthManager
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.UserRequest
import ar.edu.um.tif.aiAssistant.core.data.model.ApiConversationModels.Conversation
import ar.edu.um.tif.aiAssistant.core.data.repository.AssistantRepository
import ar.edu.um.tif.aiAssistant.core.client.AssistantApiClient
import ar.edu.um.tif.aiAssistant.core.service.CalendarPermissionRequestCoordinator
import ar.edu.um.tif.aiAssistant.core.service.PatchResponseCoordinator
import ar.edu.um.tif.aiAssistant.core.service.SmsPermissionRequestCoordinator
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
    val authError: Boolean = false,
    val hasMorePages: Boolean = true,
    val isLoadingMore: Boolean = false,
    val prependedCount: Int = 0,
    /** First visible item index when load-more was triggered; used to restore scroll after prepend. */
    val scrollRestoreFirstVisibleIndex: Int? = null
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistantRepository: AssistantRepository,
    private val assistantApiClient: AssistantApiClient,
    private val authManager: AuthManager,
    private val aimybox: Aimybox,
    private val patchResponseCoordinator: PatchResponseCoordinator,
    private val smsPermissionRequestCoordinator: SmsPermissionRequestCoordinator,
    private val calendarPermissionRequestCoordinator: CalendarPermissionRequestCoordinator
) : ViewModel() {

    val requestSmsPermissionLiveData: LiveData<Boolean> = smsPermissionRequestCoordinator.requestSmsPermissionLiveData

    fun consumeSmsPermissionRequest() {
        smsPermissionRequestCoordinator.consumeRequest()
    }

    val requestCalendarPermissionLiveData: LiveData<Boolean> = calendarPermissionRequestCoordinator.requestCalendarPermissionLiveData

    fun consumeCalendarPermissionRequest() {
        calendarPermissionRequestCoordinator.consumeRequest()
    }

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var nextPageToLoad = 1
    private var historyLoadId = 0

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
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
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
     * Add a response from the assistant to the chat (for voice interactions).
     * When [PatchResponseCoordinator.replaceLastWithNext] is true (contact patch flow),
     * replaces the last assistant message with this response so only the final reply is shown.
     */
    fun addVoiceResponseMessage(text: String) {
        if (text.isBlank()) return

        if (patchResponseCoordinator.replaceLastWithNext) {
            patchResponseCoordinator.replaceLastWithNext = false
            val messages = _uiState.value.messages
            val lastAssistantIndex = messages.indexOfLast { !it.isFromUser }
            if (lastAssistantIndex >= 0) {
                val newMessage = ChatMessage(content = text, isFromUser = false)
                _uiState.update { state ->
                    val list = state.messages.toMutableList()
                    list[lastAssistantIndex] = newMessage
                    state.copy(messages = list)
                }
                return
            }
        }

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
        nextPageToLoad = 1
        loadConversationHistoryPage(replace = true)
    }

    /**
     * Load the next page of conversation history (older messages) and prepend.
     * Call when user scrolls to the top. Pass [firstVisibleItemIndex] so scroll can be restored.
     */
    fun loadMoreConversationHistory(firstVisibleItemIndex: Int) {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMorePages) return
        _uiState.update {
            it.copy(
                isLoadingMore = true,
                scrollRestoreFirstVisibleIndex = firstVisibleItemIndex
            )
        }
        loadConversationHistoryPage(replace = false)
    }

    fun clearPrependedCount() {
        _uiState.update { it.copy(prependedCount = 0, scrollRestoreFirstVisibleIndex = null) }
    }

    private fun loadConversationHistoryPage(replace: Boolean) {
        viewModelScope.launch {
            val page = nextPageToLoad
            val loadId = ++historyLoadId
            if (page == 1) {
                _uiState.update { it.copy(isLoading = true) }
            }
            try {
                val result = assistantRepository.getConversationHistory(page = page)

                result.fold(
                    onSuccess = { historyResponse ->
                        if (loadId != historyLoadId) return@fold
                        val messages = conversationsToMessages(historyResponse.conversations)
                        val ordered = messages.reversed() // oldest first, newest last

                        nextPageToLoad = page + 1
                        val hasMore = page < historyResponse.totalPages

                        if (replace) {
                            _uiState.update { currentState -> currentState.copy(
                                messages = ordered,
                                isLoading = false,
                                errorMessage = null,
                                hasMorePages = hasMore,
                                prependedCount = 0
                            )}
                        } else {
                            _uiState.update { currentState -> currentState.copy(
                                messages = ordered + currentState.messages,
                                isLoadingMore = false,
                                hasMorePages = hasMore,
                                prependedCount = ordered.size
                            )}
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("AssistantViewModel", "History loading error", error)
                        _uiState.update { currentState -> currentState.copy(
                            isLoading = if (page == 1) false else currentState.isLoading,
                            isLoadingMore = if (page != 1) false else currentState.isLoadingMore,
                            errorMessage = if (page == 1) "Unable to load conversation history. Please try again later." else currentState.errorMessage
                        )}
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("AssistantViewModel", "Exception loading history", e)
                _uiState.update { currentState -> currentState.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = if (page == 1) "Unable to load conversation history. Please try again later." else currentState.errorMessage
                )}
            }
        }
    }

    private fun conversationsToMessages(conversations: List<Conversation>): List<ChatMessage> =
        conversations.flatMap { conversation ->
            try {
                val timestamp = try {
                    val regex = """(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})""".toRegex()
                    val matchResult = regex.find(conversation.timestamp)
                    if (matchResult != null) {
                        val (date, hours, minutes) = matchResult.destructured
                        val year = date.substring(0, 4).toInt()
                        val month = date.substring(5, 7).toInt() - 1
                        val day = date.substring(8, 10).toInt()
                        val hour = hours.toInt()
                        val minute = minutes.toInt()
                        val calendar = java.util.Calendar.getInstance()
                        calendar.set(year, month, day, hour, minute, 0)
                        calendar.set(java.util.Calendar.MILLISECOND, 0)
                        calendar.timeInMillis
                    } else System.currentTimeMillis()
                } catch (e: Exception) {
                    android.util.Log.e("AssistantViewModel", "Failed to parse timestamp: ${conversation.timestamp}", e)
                    System.currentTimeMillis()
                }
                val userMessage = ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    content = conversation.userInput,
                    isFromUser = true,
                    timestamp = timestamp
                )
                val assistantMessage = ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    content = conversation.serverReply,
                    isFromUser = false,
                    timestamp = timestamp
                )
                listOf(assistantMessage, userMessage)
            } catch (e: Exception) {
                android.util.Log.e("AssistantViewModel", "Error processing conversation", e)
                emptyList()
            }
        }
}
