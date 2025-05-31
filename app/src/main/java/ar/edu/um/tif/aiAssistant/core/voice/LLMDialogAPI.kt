package ar.edu.um.tif.aiAssistant.core.voice

import com.justai.aimybox.api.DialogApi
import com.justai.aimybox.core.CustomSkill
import com.justai.aimybox.model.Response
import com.justai.aimybox.model.reply.TextReply
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import ar.edu.um.tif.aiAssistant.core.network.client.KtorClient
import ar.edu.um.tif.aiAssistant.core.voice.clientDataModel.AssistantRequest
import ar.edu.um.tif.aiAssistant.core.voice.clientDataModel.ServerResponse

class LLMDialogAPI(
    private val baseUrl: String = "http://10.0.2.2:8000/api/v1"
) : DialogApi<AssistantRequest, Response>() {

    override val customSkills: LinkedHashSet<CustomSkill<AssistantRequest, Response>> = linkedSetOf()

    override fun createRequest(query: String): AssistantRequest = AssistantRequest(query)

    override suspend fun send(request: AssistantRequest): Response {
        val response = KtorClient.httpClient.post("$baseUrl/assistant") {
            headers {
                append("Content-Type", "application/json")
                append("Authorization", "Bearer ")
            }
            setBody(request)
        }.body<ServerResponse>()

        val question = response.appParams?.firstOrNull()?.question ?: false

        return LLMResponse(
            query = request.query,
            replies = listOf(TextReply(null, response.serverReply)),
            question = question
        )
    }
}