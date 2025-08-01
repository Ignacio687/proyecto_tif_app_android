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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Simple CustomSkill that handles contact calling functionality.
 * When a contact is not found, it delegates back to the server with the contacts list.
 */
class CallContactSkill(
    private val context: Context
) : CustomSkill<UserRequest, ServerResponse> {

    companion object {
        private const val TAG = "CallContactSkill"
        private const val ACTION_CALL_CONTACT = "call_contact"
        private const val PARAM_CONTACT_NAME = "contact_name"
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

            // Do contact lookup
            val phoneNumber = withContext(Dispatchers.IO) {
                findContactPhoneNumber(contactName)
            }

            if (phoneNumber != null) {
                Log.d(TAG, "Phone number found: $phoneNumber for $contactName")

                // Trigger speech synthesis manually
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
                Log.w(TAG, "Contact '$contactName' not found in device contacts")
                // Call defaultHandler for unknown contacts
                defaultHandler(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in CallContactSkill", e)
            // Always call defaultHandler as fallback
            try {
                defaultHandler(response)
            } catch (handlerException: Exception) {
                Log.e(TAG, "Error in defaultHandler fallback", handlerException)
            }
        }
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
     * Search for contact by name and return phone number
     */
    private fun findContactPhoneNumber(contactName: String): String? {
        var cursor: Cursor? = null
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")

            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)

            if (cursor?.moveToFirst() == true) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    return cursor.getString(numberIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding contact: $contactName", e)
        } finally {
            cursor?.close()
        }
        return null
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