package ar.edu.um.tif.aiAssistant.core.skills

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.RequiresPermission
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.ServerResponse
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.UserRequest
import ar.edu.um.tif.aiAssistant.core.service.ContactService
import ar.edu.um.tif.aiAssistant.core.service.PatchResponseCoordinator
import com.justai.aimybox.Aimybox
import com.justai.aimybox.core.CustomSkill
import com.justai.aimybox.model.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * CustomSkill that handles call invocation and response for contact calling.
 * Uses [ContactService] for contact retrieval and matching.
 * Phase 1: Exact match locally; Phase 2: Send at most 5 similar contacts to server for disambiguation.
 */
class CallContactSkill(
    private val context: Context,
    private val contactService: ContactService,
    private val patchResponseCoordinator: PatchResponseCoordinator
) : CustomSkill<UserRequest, ServerResponse> {

    companion object {
        private const val TAG = "CallContactSkill"
        private const val ACTION_CALL_CONTACT = "call_contact"
        private const val PARAM_CONTACT_NAME = "contact_name"

        // Special prefix to indicate patch request with contacts
        const val PATCH_REQUEST_PREFIX = "CONTACT_PATCH:"
    }

    @Serializable
    private data class ContactParams(
        val contact_name: String
    )

    override fun canHandle(response: ServerResponse): Boolean {
        val hasCallContactSkill = response.skills?.any { it.action == ACTION_CALL_CONTACT } == true
        val actionMatches = response.action == ACTION_CALL_CONTACT
        return hasCallContactSkill || actionMatches
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun onResponse(
        response: ServerResponse,
        aimybox: Aimybox,
        defaultHandler: suspend (Response) -> Unit
    ) {
        try {
            // Extract contact name from skill parameters
            val contactName = extractContactName(response)
            if (contactName.isNullOrBlank()) {
                Log.w(TAG, "No contact name found in response")
                defaultHandler(response)
                return
            }

            // Phase 1: Try local contact lookup with EXACT matching (via ContactService)
            val phoneNumber = withContext(Dispatchers.IO) {
                contactService.findContactPhoneNumberExact(contactName)
            }

            if (phoneNumber != null) {
                Log.d(TAG, "Phase 1: Contact found locally (exact match) - $contactName: $phoneNumber")

                // Trigger speech synthesis following Aimybox patterns
                val speeches = listOf(com.justai.aimybox.model.TextSpeech(response.serverReply))
                val speakJob = aimybox.speak(
                    speeches,
                    nextAction = com.justai.aimybox.Aimybox.NextAction.NOTHING // Don't auto-transition state
                )

                // Wait for speech to complete, then make the call
                speakJob?.join()

                // Make the call after speech synthesis completes
                makeCall(phoneNumber, contactName)
                Log.d(TAG, "Successfully initiated call to $contactName")

                // Return to standby state
                aimybox.standby()
            } else {
                Log.i(TAG, "Phase 1: Contact '$contactName' not found locally (exact match). Initiating Phase 2...")

                // Phase 2: Use the response.query field which now contains the original user request
                val originalQuery = response.query ?: "llamar a $contactName"
                Log.d(TAG, "Using original query from response.query for Phase 2: $originalQuery")

                // Phase 2: Delegate to Aimybox sendRequest with patch request (at most 5 similar contacts)
                initiateContactPatchingPhase(aimybox, originalQuery, contactName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in CallContactSkill", e)
            // Always call defaultHandler as fallback
            try {
                defaultHandler(response)
            } catch (handlerException: Exception) {
                Log.e(TAG, "Error in defaultHandler fallback", handlerException)
                // Ensure we return to standby state even if defaultHandler fails
                aimybox.standby()
            }
        }
    }

    /**
     * Phase 2: Contact Patching - Send at most 5 similar contacts to server for disambiguation
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun initiateContactPatchingPhase(aimybox: Aimybox, originalQuery: String, contactName: String) {
        try {
            Log.d(TAG, "Phase 2: Collecting at most ${ContactService.MAX_SIMILAR_CONTACTS} similar contacts for server-side matching")

            // Similar contacts (at most 5); sent as contacts_list for API compatibility
            val similarContacts = withContext(Dispatchers.IO) {
                contactService.getSimilarContactNames(contactName, ContactService.MAX_SIMILAR_CONTACTS)
            }

            if (similarContacts.isEmpty()) {
                Log.w(TAG, "Phase 2: No similar contacts found, sending patch with empty list so server can respond")
            }

            // Patch message: similar contacts (or empty); server decides response text. Var name contactsList kept for compatibility.
            val patchRequestMessage = createPatchRequestMessage(originalQuery, similarContacts)

            Log.d(TAG, "Phase 2: Sending patch request via Aimybox.sendRequest()")

            // Signal UI to replace the first response with the patch response when it arrives
            patchResponseCoordinator.replaceLastWithNext = true

            // Use Aimybox's sendRequest - this will go through AssistantApiClient
            // which will detect the patch format and create proper UserRequest
            aimybox.sendRequest(patchRequestMessage)

        } catch (e: Exception) {
            Log.e(TAG, "Phase 2: Error during contact patching", e)
            aimybox.standby()
        }
    }

    /**
     * Create a patch request message with similar contacts (exposed as contacts_list for compatibility).
     * Format: "CONTACT_PATCH:original_query|contact1,contact2,contact3"
     */
    private fun createPatchRequestMessage(originalQuery: String, contactsList: List<String>): String {
        // contactsList = similar contacts; name kept for API compatibility
        val contactsString = contactsList.joinToString(",")
        return "$PATCH_REQUEST_PREFIX$originalQuery|$contactsString"
    }

    /**
     * Extract contact name from the server response skill parameters
     */
    private fun extractContactName(response: ServerResponse): String? {
        val skill = response.skills?.find { it.action == ACTION_CALL_CONTACT }
        return skill?.params?.get(PARAM_CONTACT_NAME)
            ?: skill?.params?.get("data")?.let { parseContactNameFromJson(it) }
    }

    /**
     * Parse contact name from JSON data parameter (fallback for existing format)
     */
    private fun parseContactNameFromJson(jsonData: String): String? {
        return try {
            Json.decodeFromString<ContactParams>(jsonData).contact_name
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse contact name from JSON: $jsonData", e)
            null
        }
    }

    /**
     * Make the actual phone call
     */
    private fun makeCall(phoneNumber: String, contactName: String) {
        try {
            // Check if we have CALL_PHONE permission before attempting to make the call
            if (context.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "CALL_PHONE permission not granted, cannot make call to $contactName")
                return
            }

            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
            Log.d(TAG, "Initiated call to $contactName ($phoneNumber)")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing CALL_PHONE permission for $contactName", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call to $contactName", e)
        }
    }
}
