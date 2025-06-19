package ar.edu.um.tif.aiAssistant.core.data.model

import com.justai.aimybox.model.Request
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contains all assistant-related models used in the application.
 */
object ApiAssistantModels {
    @Serializable
    data class UserRequest(
        @SerialName("user_req")
        val userReq: String
    ) : Request {
        override val query: String
            get() = userReq
    }

    @Serializable
    data class Skill(
        val name: String,
        val action: String,
        val params: Map<String, String>
    )

    @Serializable
    data class ServerResponse(
        @SerialName("server_reply")
        val serverReply: String,
        @SerialName("app_params")
        val appParams: List<Map<String, Boolean>>? = null,
        val skills: List<Skill>? = null
    )
}

