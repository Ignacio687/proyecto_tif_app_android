package ar.edu.um.tif.aiAssistant.core.skills

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import ar.edu.um.tif.aiAssistant.core.client.AssistantApiClient
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.Contact
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.ServerResponse
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.UserRequest
import com.justai.aimybox.Aimybox
import com.justai.aimybox.core.CustomSkill
import com.justai.aimybox.model.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import javax.inject.Provider

/**
 * CustomSkill that handles contact calling functionality.
 * This skill will be triggered when the assistant response has a "call_contact" action.
 */
class CallContactSkill(
    private val context: Context,
    private val assistantApiClientProvider: Provider<AssistantApiClient>
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
            Log.e(TAG, "No contact name could be identified from the data")
            // Send system message to server instead of speaking directly
            sendInvalidContactDataMessage(defaultHandler)
            return
        }

        // Find the contact and similar contacts
        val searchResult = withContext(Dispatchers.IO) {
            findContactsWithSimilarNames(contactName)
        }

        // Check if we found an exact match
        val exactMatch = searchResult.find { contact ->
            contact.name.equals(contactName, ignoreCase = true)
        }

        if (exactMatch != null) {
            // Exact match found, proceed with the call
            makeCall(exactMatch)
            // Send success message to server
            sendCallSuccessMessage(exactMatch.name, defaultHandler)
        } else if (searchResult.isNotEmpty()) {
            // No exact match but found similar contacts, send system message to assistant
            sendContactNotFoundMessage(contactName, searchResult, defaultHandler)
        } else {
            // No contacts found at all, send system message to assistant
            sendNoContactsFoundMessage(contactName, defaultHandler)
        }
    }

    /**
     * Makes a phone call to the specified contact
     */
    private fun makeCall(contact: Contact) {
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contact.phoneNumber}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
            Log.d(TAG, "Call initiated to ${contact.name} at ${contact.phoneNumber}")
        } catch (e: Exception) {
            Log.e(TAG, "Error making call to ${contact.name}: ${e.message}", e)
        }
    }

    /**
     * Sends a system message to the assistant when contact data is invalid
     */
    private suspend fun sendInvalidContactDataMessage(
        defaultHandler: suspend (Response) -> Unit
    ) {
        val systemMessage = "[SYSTEM MESSAGE] No se pudo identificar el contacto a llamar. Los datos del contacto están incompletos o son inválidos."

        Log.d(TAG, "Sending invalid contact data message to server: $systemMessage")

        try {
            val systemRequest = UserRequest(userReq = systemMessage)
            val assistantApiClient = assistantApiClientProvider.get()
            val serverResponse = assistantApiClient.send(systemRequest)
            defaultHandler(serverResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending invalid contact data message to assistant: ${e.message}", e)
        }
    }

    /**
     * Sends a system message to the assistant when a contact is not found but similar contacts exist
     */
    private suspend fun sendContactNotFoundMessage(
        requestedName: String,
        similarContacts: List<Contact>,
        defaultHandler: suspend (Response) -> Unit
    ) {
        // Create a system message explaining the situation and providing available contacts
        val contactList = similarContacts.joinToString(", ") { it.name }

        val systemMessage = "[SYSTEM MESSAGE] El contacto '$requestedName' no fue encontrado exactamente. " +
                "Contactos similares disponibles: $contactList."

        Log.d(TAG, "Sending system message to server: $systemMessage")

        try {
            val systemRequest = UserRequest(userReq = systemMessage)
            val assistantApiClient = assistantApiClientProvider.get()
            val serverResponse = assistantApiClient.send(systemRequest)
            defaultHandler(serverResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending system message to assistant: ${e.message}", e)
        }
    }

    /**
     * Sends a system message to the assistant when no contacts are found at all
     */
    private suspend fun sendNoContactsFoundMessage(
        requestedName: String,
        defaultHandler: suspend (Response) -> Unit
    ) {
        // Get all contacts to send to the server
        val allContacts = withContext(Dispatchers.IO) {
            getAllContacts()
        }

        val contactNamesList = allContacts.joinToString(", ") { it.name }

        val systemMessage = "[SYSTEM MESSAGE] El contacto '$requestedName' no fue encontrado en la lista de contactos. " +
                "No hay contactos similares disponibles. Lista completa de contactos: $contactNamesList."

        Log.d(TAG, "Sending no contacts found message to server: $systemMessage")

        try {
            val systemRequest = UserRequest(userReq = systemMessage)
            val assistantApiClient = assistantApiClientProvider.get()
            val serverResponse = assistantApiClient.send(systemRequest)
            defaultHandler(serverResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending no contacts found message to assistant: ${e.message}", e)
        }
    }

    /**
     * Sends a success message to the assistant when a call is initiated
     */
    private suspend fun sendCallSuccessMessage(
        contactName: String,
        defaultHandler: suspend (Response) -> Unit
    ) {
        val systemMessage = "[SYSTEM MESSAGE] Llamada iniciada exitosamente a $contactName."

        Log.d(TAG, "Sending call success message to server: $systemMessage")

        try {
            val systemRequest = UserRequest(userReq = systemMessage)
            val assistantApiClient = assistantApiClientProvider.get()
            val serverResponse = assistantApiClient.send(systemRequest)
            defaultHandler(serverResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending call success message to assistant: ${e.message}", e)
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

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing contact name: ${e.message}", e)
            null
        }
    }

    /**
     * Searches for contacts with names similar to the provided name.
     * Returns a list of contacts that contain the search term or are similar.
     * @param contactName The name of the contact to search for.
     * @return A list of contacts with similar names.
     */
    private fun findContactsWithSimilarNames(contactName: String): List<Contact> {
        val contacts = mutableListOf<Contact>()
        var cursor: Cursor? = null

        try {
            Log.d(TAG, "Searching for contacts similar to: $contactName")

            // Query the contacts database
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            // Search for contacts that contain the search term (case insensitive)
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")

            cursor = context.contentResolver.query(
                uri, projection, selection, selectionArgs, null
            )

            // Collect all matching contacts
            if (cursor?.moveToFirst() == true) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                if (nameIndex != -1 && numberIndex != -1) {
                    do {
                        val name = cursor.getString(nameIndex)
                        val phoneNumber = cursor.getString(numberIndex)

                        if (!name.isNullOrBlank() && !phoneNumber.isNullOrBlank()) {
                            contacts.add(Contact(name, phoneNumber))
                            Log.d(TAG, "Found similar contact: $name")
                        }
                    } while (cursor.moveToNext())
                }
            }

            // If no partial matches found, try a broader search (first word match)
            if (contacts.isEmpty() && contactName.contains(" ")) {
                val firstWord = contactName.split(" ").first()
                cursor?.close()

                val broadSelection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
                val broadSelectionArgs = arrayOf("%$firstWord%")

                cursor = context.contentResolver.query(
                    uri, projection, broadSelection, broadSelectionArgs, null
                )

                if (cursor?.moveToFirst() == true) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    if (nameIndex != -1 && numberIndex != -1) {
                        do {
                            val name = cursor.getString(nameIndex)
                            val phoneNumber = cursor.getString(numberIndex)

                            if (!name.isNullOrBlank() && !phoneNumber.isNullOrBlank()) {
                                contacts.add(Contact(name, phoneNumber))
                                Log.d(TAG, "Found broad match contact: $name")
                            }
                        } while (cursor.moveToNext() && contacts.size < 5) // Limit to 5 contacts
                    }
                }
            }

            Log.d(TAG, "Found ${contacts.size} similar contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error finding similar contacts: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        // Remove duplicates and limit results
        return contacts.distinctBy { it.name }.take(5)
    }

    /**
     * Gets all contacts from the device
     * @return A list of all contacts with names only
     */
    private fun getAllContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        var cursor: Cursor? = null

        try {
            Log.d(TAG, "Retrieving all contacts")

            // Query the contacts database
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            cursor = context.contentResolver.query(
                uri, projection, null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            // Collect all contacts
            if (cursor?.moveToFirst() == true) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                if (nameIndex != -1 && numberIndex != -1) {
                    do {
                        val name = cursor.getString(nameIndex)
                        val phoneNumber = cursor.getString(numberIndex)

                        if (!name.isNullOrBlank() && !phoneNumber.isNullOrBlank()) {
                            contacts.add(Contact(name, phoneNumber))
                        }
                    } while (cursor.moveToNext())
                }
            }

            Log.d(TAG, "Retrieved ${contacts.size} total contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving all contacts: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        // Remove duplicates by name and return
        return contacts.distinctBy { it.name }
    }
}
