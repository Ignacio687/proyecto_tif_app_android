package ar.edu.um.tif.aiAssistant.core.voice.clientDataModel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppParam(
    @SerialName("question") val question: Boolean? = null
)

@Serializable
data class ServerResponse(
    @SerialName("server_reply") val serverReply: String,
    @SerialName("app_params") val appParams: List<AppParam>? = null
    // skills omitted for now
)