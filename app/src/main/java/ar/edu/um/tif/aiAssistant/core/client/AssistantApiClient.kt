package ar.edu.um.tif.aiAssistant.core.client

import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.ServerResponse
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.UserRequest
import ar.edu.um.tif.aiAssistant.core.data.model.ApiConversationModels.ConversationHistoryResponse
import ar.edu.um.tif.aiAssistant.core.data.repository.AuthRepository
import com.justai.aimybox.api.DialogApi
import com.justai.aimybox.core.CustomSkill
import com.justai.aimybox.model.Response
import com.justai.aimybox.model.reply.Reply
import com.justai.aimybox.model.reply.TextReply
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for handling all assistant-related API calls.
 * Integrates both standard HTTP client functionality and Aimybox DialogApi interface
 * for voice assistant capabilities.
 */
@Singleton
class AssistantApiClient @Inject constructor(
    private val client: HttpClient,
    private val authRepository: AuthRepository
) : DialogApi<UserRequest, Response>() {

    // API endpoints
    private val apiPath = "/api/v1"
    private val assistantEndpoint = "$apiPath/assistant"
    private val conversationsEndpoint = "$apiPath/conversations"

    override val customSkills: LinkedHashSet<CustomSkill<UserRequest, Response>> = linkedSetOf()

    override fun createRequest(query: String): UserRequest = UserRequest(userReq = query)

    /**
     * Implementation of the DialogApi interface method.
     * Gets the token directly from AuthRepository.
     */
    override suspend fun send(request: UserRequest): Response {
        // Get token from AuthRepository
        val token = runBlocking {
            authRepository.authToken.first()
        } ?: return LLMResponse(
            query = request.userReq,
            replies = listOf(TextReply(null, "Not authenticated. Please log in first.")),
            question = false
        )

        val response = runCatching {
            client.post {
                url(assistantEndpoint)
                headers {
                    append("Content-Type", "application/json")
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(request)
            }.body<ServerResponse>()
        }.getOrNull() ?: return LLMResponse(
            query = request.userReq,
            replies = listOf(TextReply(null, "Failed to connect to assistant")),
            question = false
        )

        val question = response.appParams?.firstOrNull()?.get("question") ?: false

        return LLMResponse(
            query = request.userReq,
            replies = listOf(TextReply(null, response.serverReply)),
            question = question
        )
    }

    /**
     * Get the conversation history from the assistant API.
     */
    suspend fun getConversationHistory(
        token: String,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<ConversationHistoryResponse> {
        return runCatching {
            val response = client.get {
                url(conversationsEndpoint)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                parameter("page", page)
                parameter("page_size", pageSize)
            }
            response.body<ConversationHistoryResponse>()
        }
    }

    /**
     * Response model for the assistant API integrating with Aimybox.
     */
    class LLMResponse(
        override val query: String?,
        override val replies: List<Reply>,
        override val question: Boolean? = false
    ) : Response {
        override val action: String? = null
        override val intent: String? = null
    }
}
