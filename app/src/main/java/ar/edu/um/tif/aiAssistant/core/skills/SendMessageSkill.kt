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
 */
class SendMessageSkill(
    private val context: Context,
    private val contactService: ContactService,
    private val smsPermissionRequestCoordinator: SmsPermissionRequestCoordinator
) : CustomSkill<UserRequest, ServerResponse> {

    companion object {
        private const val TAG = "SendMessageSkill"
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
        val phoneNumber = params.recipientPhone?.takeIf { it.isNotBlank() }
            ?: run {
                val recipient = params.recipient?.takeIf { it.isNotBlank() }
                if (recipient.isNullOrBlank()) {
                    Log.w(TAG, "SendMessageSkill: missing recipient and recipient_phone")
                    defaultHandler(response)
                    return
                }
                withContext(Dispatchers.IO) {
                    contactService.findContactPhoneNumberExact(recipient)
                }
            }

        if (phoneNumber.isNullOrBlank()) {
            Log.w(TAG, "SendMessageSkill: no phone number for recipient '${params.recipient ?: params.recipientPhone}'")
            defaultHandler(response)
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
        try {
            sendSmsDirect(phoneNumber, message)
            Log.d(TAG, "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
            defaultHandler(response)
            return
        }

        val speeches = listOf(com.justai.aimybox.model.TextSpeech(response.serverReply))
        aimybox.speak(speeches, nextAction = com.justai.aimybox.Aimybox.NextAction.NOTHING)
        aimybox.standby()
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
