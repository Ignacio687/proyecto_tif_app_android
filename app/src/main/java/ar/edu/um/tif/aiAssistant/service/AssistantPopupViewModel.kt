package ar.edu.um.tif.aiAssistant.service

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.um.tif.aiAssistant.core.auth.AuthManager
import ar.edu.um.tif.aiAssistant.core.data.model.ApiConversationModels.Conversation
import ar.edu.um.tif.aiAssistant.core.data.repository.AssistantRepository
import ar.edu.um.tif.aiAssistant.core.service.CallPermissionRequestCoordinator
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
    val userHasInteracted: Boolean = false,
    val hasMorePages: Boolean = true,
    val isLoadingMore: Boolean = false,
    val prependedCount: Int = 0,
    /** First visible item index when load-more was triggered; used to restore scroll after prepend. */
    val scrollRestoreFirstVisibleIndex: Int? = null
)

@HiltViewModel
class AssistantPopupViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val aimybox: Aimybox,
    private val assistantRepository: AssistantRepository,
    private val patchResponseCoordinator: PatchResponseCoordinator,
    private val smsPermissionRequestCoordinator: SmsPermissionRequestCoordinator,
    private val calendarPermissionRequestCoordinator: CalendarPermissionRequestCoordinator,
    private val callPermissionRequestCoordinator: CallPermissionRequestCoordinator
) : ViewModel() {

    val requestSmsPermissionLiveData: LiveData<Boolean> = smsPermissionRequestCoordinator.requestSmsPermissionLiveData

    fun consumeSmsPermissionRequest() {
        smsPermissionRequestCoordinator.consumeRequest()
    }

    val requestCalendarPermissionLiveData: LiveData<Boolean> = calendarPermissionRequestCoordinator.requestCalendarPermissionLiveData

    fun consumeCalendarPermissionRequest() {
        calendarPermissionRequestCoordinator.consumeRequest()
    }

    val requestCallPermissionLiveData: LiveData<Boolean> = callPermissionRequestCoordinator.requestCallPermissionLiveData

    fun consumeCallPermissionRequest() {
        callPermissionRequestCoordinator.consumeRequest()
    }

    private val _uiState = MutableStateFlow(PopupUiState())
    val uiState: StateFlow<PopupUiState> = _uiState.asStateFlow()

    private var nextPageToLoad = 1
    private var historyLoadId = 0

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
     * Add a voice response message from Aimybox to the chat.
     * When [PatchResponseCoordinator.replaceLastWithNext] is true (contact or message patch flow),
     * replaces the last assistant message so only the final reply is shown.
     */
    fun addVoiceResponseMessage(content: String) {
        if (patchResponseCoordinator.replaceLastWithNext) {
            patchResponseCoordinator.replaceLastWithNext = false
            val messages = _uiState.value.messages
            val lastAssistantIndex = messages.indexOfLast { !it.isFromUser }
            if (lastAssistantIndex >= 0) {
                val newMessage = PopupChatMessage(content = content, isFromUser = false)
                _uiState.update { state ->
                    val list = state.messages.toMutableList()
                    list[lastAssistantIndex] = newMessage
                    state.copy(messages = list, isLoading = false)
                }
                return
            }
        }

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

    fun loadConversationHistory() {
        nextPageToLoad = 1
        loadConversationHistoryPage(replace = true)
    }

    /**
     * Load the next page (older messages) and prepend. Call when user scrolls to the top.
     * Pass [firstVisibleItemIndex] so scroll can be restored after prepend.
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
                        val messages = conversationsToPopupMessages(historyResponse.conversations)
                        val ordered = messages.reversed()
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
                        android.util.Log.e("AssistantPopupViewModel", "History loading error", error)
                        _uiState.update { currentState -> currentState.copy(
                            isLoading = if (page == 1) false else currentState.isLoading,
                            isLoadingMore = if (page != 1) false else currentState.isLoadingMore,
                            errorMessage = if (page == 1) "Unable to load conversation history. Please try again later." else currentState.errorMessage
                        )}
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("AssistantPopupViewModel", "Exception loading history", e)
                _uiState.update { currentState -> currentState.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = if (page == 1) "Unable to load conversation history. Please try again later." else currentState.errorMessage
                )}
            }
        }
    }

    private fun conversationsToPopupMessages(conversations: List<Conversation>): List<PopupChatMessage> =
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
                    android.util.Log.e("AssistantPopupViewModel", "Failed to parse timestamp: ${conversation.timestamp}", e)
                    System.currentTimeMillis()
                }
                val userMessage = PopupChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    content = conversation.userInput,
                    isFromUser = true,
                    timestamp = timestamp
                )
                val assistantMessage = PopupChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    content = conversation.serverReply,
                    isFromUser = false,
                    timestamp = timestamp
                )
                listOf(assistantMessage, userMessage)
            } catch (e: Exception) {
                android.util.Log.e("AssistantPopupViewModel", "Error processing conversation", e)
                emptyList()
            }
        }
}
