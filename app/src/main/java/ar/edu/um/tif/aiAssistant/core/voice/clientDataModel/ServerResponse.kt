package ar.edu.um.tif.aiAssistant.core.voice.clientDataModel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerResponse(
    @SerialName("server_reply") val serverReply: String
    // app_params and skills omitted for now
)