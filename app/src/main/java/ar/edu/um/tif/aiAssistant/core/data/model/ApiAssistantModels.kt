package ar.edu.um.tif.aiAssistant.core.data.model

import com.justai.aimybox.model.Request
import com.justai.aimybox.model.Response
import com.justai.aimybox.model.reply.Reply
import com.justai.aimybox.model.reply.TextReply
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contains all assistant-related models used in the application.
 */
object ApiAssistantModels {
    @Serializable
    data class UserRequest(
        @SerialName("user_req")
        val userReq: String,
        @SerialName("system_message")
        val systemMessage: SystemMessage? = null
    ) : Request {
        override val query: String
            get() = userReq
    }

    @Serializable
    data class SystemMessage(
        @SerialName("patch_last")
        val patchLast: Boolean,
        @SerialName("contacts_list")
        val contactsList: List<String>
    )

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
    ) : Response {

        override val action: String?
            get() = skills?.firstOrNull()?.action

        override val question: Boolean?
            get() = appParams?.firstOrNull()?.get("question") ?: false

        override val intent: String?
            get() = skills?.firstOrNull()?.name

        override val replies: List<Reply>
            get() = listOf(TextReply(null, serverReply))

        override val query: String?
            get() = null
    }

    /**
     * Data class to represent a contact with name and phone number
     */
    @Serializable
    data class Contact(
        val name: String,
        val phoneNumber: String
    )
}
