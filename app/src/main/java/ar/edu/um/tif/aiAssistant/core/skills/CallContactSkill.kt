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
import com.justai.aimybox.model.TextSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * CustomSkill that handles contact calling functionality.
 * This skill will be triggered when the assistant response has a "call_contact" action.
 */
class CallContactSkill(
    private val context: Context
) : CustomSkill<UserRequest, ServerResponse> {

    companion object {
        private const val TAG = "CallContactSkill"
        private const val ACTION_CALL_CONTACT = "call_contact"
        private const val PARAM_DATA = "data"
        private const val CONTACT_NAME_KEY = "contact_name"
    }

    // Data class to parse the nested JSON in the "data" field
    @Serializable
    private data class ContactData(
        val contact_name: String
    )

    override fun canHandleRequest(request: UserRequest): Boolean {
        // Check if the request contains a calling-related keyword
        // This is optional and helps optimize performance by filtering requests early
        val callPatterns = listOf("llamar", "llama", "llamame", "comunicar", "comunicame", "contactar")
        return callPatterns.any { request.query.contains(it, ignoreCase = true) }
    }

    override suspend fun onRequest(request: UserRequest, aimybox: Aimybox): UserRequest {
        // Just pass the request through without modification
        // This method is required by the CustomSkill interface
        Log.d(TAG, "Processing request: ${request.query}")
        return request
    }

    override fun canHandle(response: ServerResponse): Boolean {
        // Check if the response has the call_contact action
        return response.skills?.any { it.action == ACTION_CALL_CONTACT } == true
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun onResponse(
        response: ServerResponse,
        aimybox: Aimybox,
        defaultHandler: suspend (Response) -> Unit
    ) {
        Log.d(TAG, "Processing call contact response: ${response.skills}")

        // Get the contact data JSON string from the response
        val contactDataJson = response.skills?.find { it.action == ACTION_CALL_CONTACT }
            ?.params?.get(PARAM_DATA)

        Log.d(TAG, "Raw contact data: $contactDataJson")

        // Parse the contact name from the data field using multiple approaches
        val contactName = parseContactName(contactDataJson)

        if (contactName.isNullOrBlank()) {
            // If no contact name provided, inform the user and standby
            val speech = TextSpeech("No se pudo identificar el contacto a llamar.")
            aimybox.speak(speech, Aimybox.NextAction.STANDBY)
            return
        }

        // Find the contact phone number
        val phoneNumber = withContext(Dispatchers.IO) {
            findContactPhoneNumber(contactName)
        }

        if (phoneNumber.isNullOrBlank()) {
            // If no phone number found, inform the user and standby
            val speech = TextSpeech("No se encontró el contacto $contactName o no tiene un número de teléfono.")
            aimybox.speak(speech, Aimybox.NextAction.STANDBY)
            return
        }

        // Call the contact
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)

            // Inform the user that the call is being placed
            val speech = TextSpeech("Llamando a $contactName.")
            aimybox.speak(speech, Aimybox.NextAction.STANDBY)
        } catch (e: Exception) {
            Log.e(TAG, "Error making call: ${e.message}", e)
            // Handle any exceptions that might occur during the call intent
            val speech = TextSpeech("No se pudo realizar la llamada. Verifique los permisos de la aplicación.")
            aimybox.speak(speech, Aimybox.NextAction.STANDBY)
        }
    }

    /**
     * Attempts to parse the contact name from the data string using multiple approaches
     * to handle different possible formats from the server.
     */
    private fun parseContactName(contactDataJson: String?): String? {
        if (contactDataJson.isNullOrBlank()) return null

        return try {
            // Try several parsing approaches to handle different formats

            // Approach 1: Try to parse as a properly escaped JSON string
            try {
                Json.decodeFromString<ContactData>(contactDataJson).contact_name
            } catch (e: Exception) {
                Log.d(TAG, "Failed to parse with kotlinx.serialization: ${e.message}")
                null
            }
            // Approach 2: Try to parse as a raw JSON object using JSONObject
                ?: try {
                    JSONObject(contactDataJson).optString(CONTACT_NAME_KEY)
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to parse with JSONObject: ${e.message}")
                    null
                }
                // Approach 3: Try to handle malformed JSON by extracting the name directly
                ?: try {
                    // Extract anything between quotes after "contact_name":
                    val regex = "\"$CONTACT_NAME_KEY\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                    val matchResult = regex.find(contactDataJson)
                    matchResult?.groupValues?.getOrNull(1)
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to parse with regex: ${e.message}")
                    null
                }
                // If all approaches fail, return null
                ?: null

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing contact name: ${e.message}", e)
            null
        }
    }

    /**
     * Searches for a contact by name and returns their phone number.
     * @param contactName The name of the contact to search for.
     * @return The phone number of the contact, or null if not found.
     */
    private fun findContactPhoneNumber(contactName: String): String? {
        var phoneNumber: String? = null
        var cursor: Cursor? = null

        try {
            Log.d(TAG, "Searching for contact: $contactName")

            // Query the contacts database
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")

            cursor = context.contentResolver.query(
                uri, projection, selection, selectionArgs, null
            )

            // Find the first matching contact
            if (cursor?.moveToFirst() == true) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                if (nameIndex != -1 && numberIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    phoneNumber = cursor.getString(numberIndex)
                    Log.d(TAG, "Found contact: $name with number: $phoneNumber")
                }
            } else {
                Log.d(TAG, "No contacts found matching: $contactName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding contact: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        return phoneNumber
    }
}