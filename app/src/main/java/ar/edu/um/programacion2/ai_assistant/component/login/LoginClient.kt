package ar.edu.um.programacion2.ai_assistant.component.login

import ar.edu.um.programacion2.ai_assistant.core.customException.UnauthorizedAccessException
import ar.edu.um.programacion2.ai_assistant.core.network.client.KtorClient
import ar.edu.um.programacion2.ai_assistant.component.login.clientDataModel.Authenticate
import ar.edu.um.programacion2.ai_assistant.component.login.clientDataModel.BadCredentials
import ar.edu.um.programacion2.ai_assistant.component.login.clientDataModel.Token
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

object LoginClient {
    private const val RELATIVE_URL = "authenticate"

    suspend fun authenticate(authenticate: Authenticate): Token {
        val response = KtorClient.httpClient.post("${KtorClient.BASE_URL}$RELATIVE_URL") {
            contentType(ContentType.Application.Json)
            setBody(authenticate)
        }
        try {
            return response.body()
        } catch (e: Exception) {
            val responseBody = try {
                response.body<BadCredentials>()
            } catch (e: Exception) {
                throw Exception("Authentication failed: ${response.bodyAsText()}", e)
            }
            throw UnauthorizedAccessException("Unauthorized access: ${responseBody.detail}")
        }
    }
}