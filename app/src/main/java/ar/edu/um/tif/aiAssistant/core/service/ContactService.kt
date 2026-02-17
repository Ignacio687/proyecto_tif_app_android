package ar.edu.um.tif.aiAssistant.core.service

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for contact retrieval and matching from the device.
 * Used by CallContactSkill (and future MessageSkill) for contact lookup.
 */
@Singleton
class ContactService @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ContactService"
        /** Maximum number of similar contacts to return when no exact match is found. */
        const val MAX_SIMILAR_CONTACTS = 5
    }

    /**
     * Finds a contact by exact display name (case-insensitive) and returns their first phone number.
     * e.g. query "Mateo" matches contact "mateo".
     * @return phone number or null if not found
     */
    fun findContactPhoneNumberExact(contactName: String): String? {
        var cursor: Cursor? = null
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val col = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            val selection = "LOWER($col) = LOWER(?)"
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
     * Returns at most [limit] contact names that are similar to [query] (case-insensitive).
     * Similarity: name contains the query or query words appear in the name.
     * Prefer names that start with the query, then contains.
     * @param query search string (e.g. name or partial name)
     * @param limit max results (default [MAX_SIMILAR_CONTACTS])
     */
    fun getSimilarContactNames(query: String, limit: Int = MAX_SIMILAR_CONTACTS): List<String> {
        if (query.isBlank()) return emptyList()

        val normalizedQuery = query.trim().lowercase()
        val queryWords = normalizedQuery.split(" ").filter { it.isNotEmpty() }

        val allNames = getAllDeviceContactNames()
        if (allNames.isEmpty()) return emptyList()

        val similar = allNames
            .filter { name ->
                val lower = name.lowercase()
                lower.contains(normalizedQuery) ||
                    queryWords.any { word -> word.length >= 2 && lower.contains(word) }
            }
            .sortedBy { name ->
                val lower = name.lowercase()
                when {
                    lower.startsWith(normalizedQuery) -> 0
                    lower.contains(normalizedQuery) -> 1
                    else -> 2
                }
            }
            .distinct()
            .take(limit)

        Log.d(TAG, "Found ${similar.size} similar contacts for '$query' (limit=$limit)")
        return similar
    }

    /**
     * Collects all unique contact display names from the device.
     * Used internally by [getSimilarContactNames]; can be used by other features if needed.
     */
    fun getAllDeviceContactNames(): List<String> {
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
}
