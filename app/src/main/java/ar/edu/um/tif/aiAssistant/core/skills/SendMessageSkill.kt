package ar.edu.um.tif.aiAssistant.core.skills

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.SendMessageSkillResponse
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.ServerResponse
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.UserRequest
import ar.edu.um.tif.aiAssistant.core.service.ContactService
import ar.edu.um.tif.aiAssistant.core.service.SmsPermissionRequestCoordinator
import com.justai.aimybox.Aimybox
import com.justai.aimybox.core.CustomSkill
import com.justai.aimybox.model.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CustomSkill that sends an SMS to a contact directly in the background.
 * Uses [ContactService] to resolve recipient name to phone when needed.
 * When the server sends [SendMessageParams.recipientPhone] (like CallContactSkill.contact_phone),
 * that number is used directly without contact lookup.
 * When the contact is not found locally, sends similar contacts to the server for disambiguation
 * (same patch logic as [CallContactSkill]).
 */
class SendMessageSkill(
    private val context: Context,
    private val contactService: ContactService,
    private val smsPermissionRequestCoordinator: SmsPermissionRequestCoordinator
) : CustomSkill<UserRequest, ServerResponse> {

    companion object {
        private const val TAG = "SendMessageSkill"
        /** Same prefix as CallContactSkill so the server receives the same patch format. */
        private const val PATCH_REQUEST_PREFIX = "CONTACT_PATCH:"
    }

    override fun canHandle(response: ServerResponse): Boolean {
        return response.skills?.any { it is SendMessageSkillResponse } == true
    }

    private fun ServerResponse.sendMessageSkill(): SendMessageSkillResponse? =
        skills?.filterIsInstance<SendMessageSkillResponse>()?.firstOrNull()

    override suspend fun onResponse(
        response: ServerResponse,
        aimybox: Aimybox,
        defaultHandler: suspend (Response) -> Unit
    ) {
        val skill = response.sendMessageSkill() ?: run {
            defaultHandler(response)
            return
        }
        val params = skill.params

        // When server sends recipient_phone, use it directly (same pattern as CallContactSkill.contact_phone).
        val recipient = params.recipient?.takeIf { it.isNotBlank() }
        val phoneNumber = params.recipientPhone?.takeIf { it.isNotBlank() }
            ?: if (!recipient.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    contactService.findContactPhoneNumberExact(recipient)
                }
            } else {
                Log.w(TAG, "SendMessageSkill: missing recipient and recipient_phone")
                defaultHandler(response)
                return
            }

        if (phoneNumber.isNullOrBlank()) {
            // Phase 2: Contact not found locally — show reply then send similar contacts to server (same as CallContactSkill)
            if (!recipient.isNullOrBlank()) {
                Log.i(TAG, "Recipient '$recipient' not found locally. Initiating patch with similar contacts...")
                defaultHandler(response)
                val originalQuery = response.query ?: "enviar mensaje a $recipient"
                initiateMessagePatchingPhase(aimybox, originalQuery, recipient)
            } else {
                Log.w(TAG, "SendMessageSkill: no phone number for recipient_phone")
                defaultHandler(response)
            }
            return
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SEND_SMS permission not granted, requesting permission")
            smsPermissionRequestCoordinator.requestSmsPermission()
            defaultHandler(response)
            return
        }

        val message = params.message
        if (message.isBlank()) {
            Log.w(TAG, "SendMessageSkill: message is empty")
            defaultHandler(response)
            return
        }
        defaultHandler(response)
        try {
            sendSmsDirect(phoneNumber, message)
            Log.d(TAG, "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
        }
        aimybox.standby()
    }

    /**
     * Phase 2: When recipient not found locally, send at most [ContactService.MAX_SIMILAR_CONTACTS]
     * similar contacts to the server for disambiguation (same format as CallContactSkill).
     */
    private suspend fun initiateMessagePatchingPhase(aimybox: Aimybox, originalQuery: String, recipientName: String) {
        try {
            val similarContacts = withContext(Dispatchers.IO) {
                contactService.getSimilarContactNames(recipientName, ContactService.MAX_SIMILAR_CONTACTS)
            }
            if (similarContacts.isEmpty()) {
                Log.w(TAG, "Phase 2: No similar contacts found, sending patch with empty list")
            }
            val patchRequestMessage = "$PATCH_REQUEST_PREFIX$originalQuery|${similarContacts.joinToString(",")}"
            Log.d(TAG, "Phase 2: Sending patch request via Aimybox.sendRequest()")
            aimybox.sendRequest(patchRequestMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Phase 2: Error during message contact patching", e)
            aimybox.standby()
        }
    }

    /**
     * Sends SMS in the background via SmsManager. Long messages are split into parts.
     */
    private fun sendSmsDirect(destinationAddress: String, text: String) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: throw IllegalStateException("SmsManager not available")
        val parts = smsManager.divideMessage(text)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(destinationAddress, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(destinationAddress, null, text, null, null)
        }
    }
}
