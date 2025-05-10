package ar.edu.um.tif.aiAssistant.core.voice.clientDataModel

import com.justai.aimybox.model.Request
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssistantRequest(
    @SerialName("user_req") val userReq: String
) : Request {
    override val query: String
        get() = userReq
}