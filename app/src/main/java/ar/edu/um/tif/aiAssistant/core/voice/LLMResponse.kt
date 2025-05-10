package ar.edu.um.tif.aiAssistant.core.voice

import com.justai.aimybox.model.Response

class LLMResponse(
    override val query: String?,
    override val replies: List<com.justai.aimybox.model.reply.Reply>
) : Response {
    override val action: String? = null
    override val intent: String? = null
    override val question: Boolean? = false
}