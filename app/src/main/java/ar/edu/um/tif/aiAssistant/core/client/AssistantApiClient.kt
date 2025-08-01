package ar.edu.um.tif.aiAssistant.core.client

import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.ServerResponse
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.UserRequest
import ar.edu.um.tif.aiAssistant.core.data.model.ApiConversationModels.ConversationHistoryResponse
import ar.edu.um.tif.aiAssistant.core.data.repository.AuthRepository
import com.justai.aimybox.Aimybox
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
import android.util.Log

/**
 * Client for handling all assistant-related API calls.
 * Integrates both standard HTTP client functionality and Aimybox DialogApi interface
 * for voice assistant capabilities.
 */
@Singleton
class AssistantApiClient @Inject constructor(
    private val client: HttpClient,
    private val authRepository: AuthRepository,
    customSkills: LinkedHashSet<CustomSkill<*, *>>
) : DialogApi<UserRequest, ServerResponse>() {

    // Implementation of the abstract property from DialogApi with the correct type parameters
    // We use the skills passed in the constructor, with proper casting
    @Suppress("UNCHECKED_CAST")
    override val customSkills = customSkills as LinkedHashSet<CustomSkill<UserRequest, ServerResponse>>

    // API endpoints
    private val apiPath = "/api/v1"
    private val assistantEndpoint = "$apiPath/assistant"
    private val conversationsEndpoint = "$apiPath/conversations"

    companion object {
        private const val TAG = "AssistantApiClient"
    }

    // LLM Response type for internal use
    data class LLMResponse(
        val text: String,
        override val action: String? = null,
        override val question: Boolean? = false,
        override val intent: String? = null,
        override val replies: List<Reply> = listOf(TextReply(null, text)),
        override val query: String? = null
    ) : Response

    override fun createRequest(query: String): UserRequest = UserRequest(userReq = query)

    /**
     * Create a request with system message for contact patching
     */
    fun createRequestWithSystemMessage(query: String, contactsList: List<String>): UserRequest {
        return UserRequest(
            userReq = query,
            systemMessage = ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.SystemMessage(
                patchLast = true,
                contactsList = contactsList
            )
        )
    }

    /**
     * Implementation of the DialogApi interface method.
     * Gets the token directly from AuthRepository.
     */
    override suspend fun send(request: UserRequest): ServerResponse {
        // Get token from AuthRepository
        val token = authRepository.getAccessToken()
            ?: return ServerResponse(
                serverReply = "Not authenticated. Please log in first.",
                appParams = listOf(mapOf("question" to false)),
                skills = emptyList()
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
        }.getOrElse { exception ->
            Log.e(TAG, "Failed to send request", exception)
            return ServerResponse(
                serverReply = "Failed to connect to assistant",
                appParams = listOf(mapOf("question" to false)),
                skills = emptyList()
            )
        }

        return response
    }

    /**
     * Send a request with system message (used for contact patching)
     */
    suspend fun sendRequestWithSystemMessage(query: String, contactsList: List<String>): ServerResponse {
        val request = createRequestWithSystemMessage(query, contactsList)
        return send(request)
    }

    /**
     * Add a custom skill to the dialog API
     */
    fun addCustomSkill(skill: CustomSkill<UserRequest, ServerResponse>) {
        customSkills.add(skill)
    }

    /**
     * Get the number of registered custom skills
     */
    fun getCustomSkillsCount(): Int {
        return customSkills.size
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
}
