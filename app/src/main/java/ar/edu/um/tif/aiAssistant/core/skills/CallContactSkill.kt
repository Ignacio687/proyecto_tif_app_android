package ar.edu.um.tif.aiAssistant.core.skills

import android.Manifest
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.annotation.RequiresPermission
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.ServerResponse
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.UserRequest
import com.justai.aimybox.Aimybox
import com.justai.aimybox.core.CustomSkill
import com.justai.aimybox.model.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Refactored CustomSkill that handles contact calling with server-side contact matching.
 * Implements a two-phase mechanism:
 * Phase 1: Try local contact lookup, if not found proceed to Phase 2
 * Phase 2: Send contacts list to server for intelligent matching
 */
class CallContactSkill(
    private val context: Context
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

            // Phase 1: Try local contact lookup with EXACT matching
            val phoneNumber = withContext(Dispatchers.IO) {
                findContactPhoneNumberExact(contactName)
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

                // Phase 2: Delegate to Aimybox sendRequest with patch request
                initiateContactPatchingPhase(aimybox, originalQuery)
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
     * Phase 2: Contact Patching - Send request via Aimybox with contact list
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun initiateContactPatchingPhase(aimybox: Aimybox, originalQuery: String) {
        try {
            Log.d(TAG, "Phase 2: Collecting device contacts for server-side matching")

            // Collect all device contact names
            val deviceContacts = withContext(Dispatchers.IO) {
                getAllDeviceContactNames()
            }

            if (deviceContacts.isEmpty()) {
                Log.w(TAG, "Phase 2: No contacts found on device")

                // Inform user that no contacts were found
                val speeches = listOf(com.justai.aimybox.model.TextSpeech("No hay contactos en tu teléfono"))
                val speakJob = aimybox.speak(
                    speeches,
                    nextAction = com.justai.aimybox.Aimybox.NextAction.STANDBY
                )

                // Wait for speech to complete, then go to standby
                speakJob?.join()
                return
            }

            Log.d(TAG, "Phase 2: Found ${deviceContacts.size} contacts on device")

            // Create patch request message that AssistantApiClient will detect
            val patchRequestMessage = createPatchRequestMessage(originalQuery, deviceContacts)

            Log.d(TAG, "Phase 2: Sending patch request via Aimybox.sendRequest()")

            // Use Aimybox's sendRequest - this will go through AssistantApiClient
            // which will detect the patch format and create proper UserRequest
            aimybox.sendRequest(patchRequestMessage)

        } catch (e: Exception) {
            Log.e(TAG, "Phase 2: Error during contact patching", e)
            aimybox.standby()
        }
    }

    /**
     * Create a patch request message with embedded contact data
     * Format: "CONTACT_PATCH:original_query|contact1,contact2,contact3"
     */
    private fun createPatchRequestMessage(originalQuery: String, contactsList: List<String>): String {
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
     * Search for contact by EXACT name match and return phone number
     */
    private fun findContactPhoneNumberExact(contactName: String): String? {
        var cursor: Cursor? = null
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            // Use exact match instead of LIKE for precise matching
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(contactName)

            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)

            if (cursor?.moveToFirst() == true) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    val foundNumber = cursor.getString(numberIndex)
                    Log.d(TAG, "Exact match found for '$contactName': $foundNumber")
                    return foundNumber
                }
            }

            Log.d(TAG, "No exact match found for contact: '$contactName'")
        } catch (e: Exception) {
            Log.e(TAG, "Error finding contact: $contactName", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * Collect all contact names from device for server-side matching
     */
    private fun getAllDeviceContactNames(): List<String> {
        val contactNames = mutableSetOf<String>()
        var cursor: Cursor? = null

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

            cursor = context.contentResolver.query(uri, projection, null, null, null)

            if (cursor != null) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            contactNames.add(name.trim())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting device contacts", e)
        } finally {
            cursor?.close()
        }

        Log.d(TAG, "Collected ${contactNames.size} unique contact names from device")
        return contactNames.toList()
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
