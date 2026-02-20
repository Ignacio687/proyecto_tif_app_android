package ar.edu.um.tif.aiAssistant.core.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates patch flow so only the final (patch) response is shown in chat.
 * When [CallContactSkill] or [SendMessageSkill] sends a patch request (contact not found),
 * the server response has [replaceLastWithNext] set; the next assistant message added
 * to the chat will replace the previous one instead of appending.
 */
@Singleton
class PatchResponseCoordinator @Inject constructor() {
    /** When true, the next call to add a voice response should replace the last assistant message. */
    var replaceLastWithNext: Boolean = false
}
