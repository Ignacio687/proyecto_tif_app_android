package ar.edu.um.tif.aiAssistant.core.data.repository

import ar.edu.um.tif.aiAssistant.core.client.AssistantApiClient
import ar.edu.um.tif.aiAssistant.core.data.model.ApiConversationModels.ConversationHistoryResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantRepository @Inject constructor(
    private val assistantClient: AssistantApiClient,
    private val authRepository: AuthRepository
) {
    // Get conversation history
    suspend fun getConversationHistory(page: Int = 1, pageSize: Int = 10): Result<ConversationHistoryResponse> {
        val token = authRepository.getAccessToken() ?: return Result.failure(
            IllegalStateException("Authentication token not found")
        )

        return assistantClient.getConversationHistory(token, page, pageSize)
    }
}
