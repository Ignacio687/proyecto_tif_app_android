package ar.edu.um.programacion2.ai_assistant.component.home

import ar.edu.um.programacion2.ai_assistant.core.customException.UnauthorizedAccessException
import ar.edu.um.programacion2.ai_assistant.core.network.client.KtorClient
import ar.edu.um.programacion2.ai_assistant.component.home.clientDataModel.Device
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText

object HomeClient {
    private const val RELATIVE_URL = "devices"

    suspend fun getDevices(token: String): List<Device> {
        val response = KtorClient.httpClient
            .get("${KtorClient.BASE_URL}$RELATIVE_URL?active=true") {
            headers {
                append("Authorization", "Bearer $token")
            }
        }
        try {
            return response.body()
        } catch (e: Exception) {
            if (response.status.value == 401 && response.status.description == "Unauthorized") {
                throw UnauthorizedAccessException("Unauthorized access")
            }
            throw Exception(
                "Failed to fetch devices: ${response.status.value} ${response.bodyAsText()}", e)
        }
    }
}