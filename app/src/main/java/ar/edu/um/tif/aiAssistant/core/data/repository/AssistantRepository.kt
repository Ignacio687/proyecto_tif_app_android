package ar.edu.um.tif.aiAssistant.core.data.repository

import ar.edu.um.tif.aiAssistant.core.data.model.ServerResponse
import ar.edu.um.tif.aiAssistant.core.data.model.UserRequest
import ar.edu.um.tif.aiAssistant.core.data.remote.KtorApiClient
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantRepository @Inject constructor(
    private val apiClient: KtorApiClient,
    private val authRepository: AuthRepository
) {
    // Send a request to the AI assistant
    suspend fun sendUserRequest(userInput: String): Result<ServerResponse> {
        val token = authRepository.authToken.first() ?: return Result.failure(
            IllegalStateException("Authentication token not found")
        )

        return apiClient.sendUserRequest(token, UserRequest(user_req = userInput))
    }

    // Get conversation history
    suspend fun getConversationHistory(page: Int = 1, pageSize: Int = 10): Result<Map<String, Any>> {
        val token = authRepository.authToken.first() ?: return Result.failure(
            IllegalStateException("Authentication token not found")
        )

        return apiClient.getConversationHistory(token, page, pageSize)
    }
}
