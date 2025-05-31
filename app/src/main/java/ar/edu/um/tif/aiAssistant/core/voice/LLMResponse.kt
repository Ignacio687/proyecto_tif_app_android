package ar.edu.um.tif.aiAssistant.core.voice

import com.justai.aimybox.model.Response
import com.justai.aimybox.model.reply.Reply

class LLMResponse(
    override val query: String?,
    override val replies: List<Reply>,
    override val question: Boolean? = false
) : Response {
    override val action: String? = null
    override val intent: String? = null
}