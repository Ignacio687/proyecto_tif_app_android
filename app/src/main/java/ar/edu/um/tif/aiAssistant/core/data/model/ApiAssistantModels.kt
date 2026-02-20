package ar.edu.um.tif.aiAssistant.core.data.model

import com.justai.aimybox.model.Request
import com.justai.aimybox.model.Response
import com.justai.aimybox.model.reply.Reply
import com.justai.aimybox.model.reply.TextReply
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Assistant API models aligned with server OpenAPI schema (openapi.json).
 * Skills are typed per OpenAPI: CallContactSkill, SendMessageSkill, CreateReminderSkill, GoogleSearchSkill.
 * CallContactSkill and SendMessageSkill are implemented in the app; others use default reply.
 */
object ApiAssistantModels {

    /** OpenAPI: UserRequest. Required user_req; optional system_message, timezone, location. */
    @Serializable
    data class UserRequest(
        @SerialName("user_req")
        val userReq: String,
        @SerialName("system_message")
        val systemMessage: SystemMessage? = null,
        @SerialName("timezone")
        val timezone: String? = null,
        @SerialName("location")
        val location: String? = null
    ) : Request {
        override val query: String
            get() = userReq
    }

    /** OpenAPI: SystemMessage. patch_last, contacts_list, skill_failure_message. */
    @Serializable
    data class SystemMessage(
        @SerialName("patch_last")
        val patchLast: Boolean,
        @SerialName("contacts_list")
        val contactsList: List<String>,
        @SerialName("skill_failure_message")
        val skillFailureMessage: String? = null
    )

    // ─── Skill params (OpenAPI) ─────────────────────────────────────────────────────────────

    /** OpenAPI: CallContactParams. Exactly one of contact_name or contact_phone non-empty. */
    @Serializable
    data class CallContactParams(
        @SerialName("contact_name")
        val contactName: String? = null,
        @SerialName("contact_phone")
        val contactPhone: String? = null
    )

    /** OpenAPI: SendMessageParams. Exactly one of recipient or recipient_phone non-empty; message required. */
    @Serializable
    data class SendMessageParams(
        val recipient: String? = null,
        val message: String,
        @SerialName("recipient_phone")
        val recipientPhone: String? = null
    )

    /** OpenAPI: CreateReminderParams. */
    @Serializable
    data class CreateReminderParams(
        val title: String,
        val datetime: String
    )

    /** OpenAPI: GoogleSearchParams. Empty object (executed server-side). */
    @Serializable
    data class GoogleSearchParams(
        val placeholder: Boolean? = null
    )

    // ─── Skills (OpenAPI oneOf, discriminator: name) ───────────────────────────────────────────

    /** OpenAPI: ServerResponse.skills item. Discriminator "name" selects concrete type. */
    @Serializable(with = SkillSerializer::class)
    sealed class Skill {
        abstract val name: String
        abstract val action: String
    }

    /** OpenAPI: CallContactSkill */
    @Serializable
    @SerialName("CallContactSkill")
    data class CallContactSkillResponse(
        override val name: String = "CallContactSkill",
        override val action: String = "call_contact",
        val params: CallContactParams
    ) : Skill()

    /** OpenAPI: SendMessageSkill */
    @Serializable
    @SerialName("SendMessageSkill")
    data class SendMessageSkillResponse(
        override val name: String = "SendMessageSkill",
        override val action: String = "send_message",
        val params: SendMessageParams
    ) : Skill()

    /** OpenAPI: CreateReminderSkill (not implemented in app). */
    @Serializable
    @SerialName("CreateReminderSkill")
    data class CreateReminderSkillResponse(
        override val name: String = "CreateReminderSkill",
        override val action: String = "create_reminder",
        val params: CreateReminderParams
    ) : Skill()

    /** OpenAPI: GoogleSearchSkill (not implemented in app). */
    @Serializable
    @SerialName("GoogleSearchSkill")
    data class GoogleSearchSkillResponse(
        override val name: String = "GoogleSearchSkill",
        override val action: String = "activate",
        val params: GoogleSearchParams = GoogleSearchParams()
    ) : Skill()

    /** Fallback for unknown skill names from the server; avoids SerializationException. */
    @Serializable
    data class UnknownSkillResponse(
        override val name: String = "Unknown",
        override val action: String = "unknown"
    ) : Skill()

    private object SkillSerializer : JsonContentPolymorphicSerializer<Skill>(Skill::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Skill> {
            val name = element.jsonObject["name"]?.jsonPrimitive?.content
            return when (name) {
                "CallContactSkill" -> serializer<CallContactSkillResponse>()
                "SendMessageSkill" -> serializer<SendMessageSkillResponse>()
                "CreateReminderSkill" -> serializer<CreateReminderSkillResponse>()
                "GoogleSearchSkill" -> serializer<GoogleSearchSkillResponse>()
                else -> serializer<UnknownSkillResponse>()
            }
        }
    }

    /** OpenAPI: ServerResponse. required server_reply; optional app_params, skills. */
    @Serializable
    data class ServerResponse(
        @SerialName("server_reply")
        val serverReply: String,
        @SerialName("app_params")
        val appParams: List<Map<String, Boolean>>? = null,
        val skills: List<Skill>? = null,
        @kotlinx.serialization.Transient
        val originalQuery: String? = null
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
            get() = originalQuery
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
