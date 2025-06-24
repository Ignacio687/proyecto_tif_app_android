package ar.edu.um.tif.aiAssistant.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contains all conversation-related models used in the application.
 */
object ApiConversationModels {
    @Serializable
    data class Conversation(
        @SerialName("user_input")
        val userInput: String,
        @SerialName("server_reply")
        val serverReply: String,
        val timestamp: String
    )

    @Serializable
    data class ConversationHistoryResponse(
        val conversations: List<Conversation>,
        @SerialName("total_count")
        val totalCount: Int,
        val page: Int,
        @SerialName("page_size")
        val pageSize: Int,
        @SerialName("total_pages")
        val totalPages: Int
    )
}
