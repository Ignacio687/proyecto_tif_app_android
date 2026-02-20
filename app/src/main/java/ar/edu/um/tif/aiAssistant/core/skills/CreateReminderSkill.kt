package ar.edu.um.tif.aiAssistant.core.skills

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.CreateReminderParams
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.CreateReminderSkillResponse
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.ServerResponse
import ar.edu.um.tif.aiAssistant.core.data.model.ApiAssistantModels.UserRequest
import ar.edu.um.tif.aiAssistant.core.service.CalendarPermissionRequestCoordinator
import com.justai.aimybox.Aimybox
import com.justai.aimybox.core.CustomSkill
import com.justai.aimybox.model.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * CustomSkill that creates a reminder (calendar event with alarm) directly in the background,
 * like [SendMessageSkill]. Uses [CalendarContract] and ContentResolver to insert the event and
 * a reminder; no redirect to the calendar app. If calendar permission is missing, requests it
 * and defers to default reply; if direct insert fails (e.g. no calendar), falls back to opening
 * the calendar app intent.
 */
class CreateReminderSkill(
    private val context: Context,
    private val calendarPermissionRequestCoordinator: CalendarPermissionRequestCoordinator
) : CustomSkill<UserRequest, ServerResponse> {

    companion object {
        private const val TAG = "CreateReminderSkill"

        // Thread-local formatters: SimpleDateFormat is not thread-safe.
        private val ISO_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }
        }
        private val ISO_FORMAT_MS = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }
        }
        private val ISO_FORMAT_OFFSET = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT)
        }
        private val DATE_TIME_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }
        }
        private val DATE_TIME_FORMAT_SECONDS = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }
        }
        private val DATE_TIME_FORMAT_OFFSET = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX", Locale.ROOT)
        }
        private val DATE_ONLY_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }
        }

        /** Default event duration in milliseconds (15 minutes). */
        private const val DEFAULT_EVENT_DURATION_MS = 15 * 60 * 1000L

        /** Reminder fires at event time (0 minutes before). */
        private const val REMINDER_MINUTES_BEFORE = 0
    }

    override fun canHandle(response: ServerResponse): Boolean {
        return response.skills?.any { it is CreateReminderSkillResponse } == true
    }

    private fun ServerResponse.createReminderSkill(): CreateReminderSkillResponse? =
        skills?.filterIsInstance<CreateReminderSkillResponse>()?.firstOrNull()

    override suspend fun onResponse(
        response: ServerResponse,
        aimybox: Aimybox,
        defaultHandler: suspend (Response) -> Unit
    ) {
        val skill = response.createReminderSkill() ?: run {
            defaultHandler(response)
            return
        }
        val params = skill.params

        val title = params.title.takeIf { it.isNotBlank() }
        if (title.isNullOrBlank()) {
            Log.w(TAG, "CreateReminderSkill: title is missing or empty")
            defaultHandler(response)
            return
        }

        val beginTimeMs = computeBeginTimeMs(params)
        if (beginTimeMs == null || beginTimeMs <= 0) {
            Log.w(TAG, "CreateReminderSkill: could not resolve reminder time (datetime or delay_minutes required)")
            defaultHandler(response)
            return
        }

        val endTimeMs = beginTimeMs + DEFAULT_EVENT_DURATION_MS

        if (!hasCalendarPermission()) {
            Log.w(TAG, "CreateReminderSkill: calendar permission not granted, requesting")
            calendarPermissionRequestCoordinator.requestCalendarPermission()
            defaultHandler(response)
            return
        }

        val inserted = withContext(Dispatchers.IO) {
            insertEventAndReminderDirectly(title, params.description, beginTimeMs, endTimeMs)
        }

        if (inserted) {
            Log.d(TAG, "CreateReminderSkill: created reminder '$title' at $beginTimeMs")
            val speeches = listOf(com.justai.aimybox.model.TextSpeech(response.serverReply))
            aimybox.speak(speeches, nextAction = com.justai.aimybox.Aimybox.NextAction.NOTHING)
            aimybox.standby()
        } else {
            Log.w(TAG, "CreateReminderSkill: direct insert failed, falling back to calendar intent")
            try {
                openCalendarInsertIntent(title, params.description, beginTimeMs, endTimeMs)
                val speeches = listOf(com.justai.aimybox.model.TextSpeech(response.serverReply))
                aimybox.speak(speeches, nextAction = com.justai.aimybox.Aimybox.NextAction.NOTHING)
                aimybox.standby()
            } catch (e: Exception) {
                Log.e(TAG, "CreateReminderSkill: fallback intent also failed", e)
                defaultHandler(response)
            }
        }
    }

    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Inserts a calendar event and a reminder (alarm at event time) via ContentResolver.
     * @return true if both event and reminder were inserted, false otherwise.
     */
    private fun insertEventAndReminderDirectly(
        title: String,
        description: String?,
        beginTimeMs: Long,
        endTimeMs: Long
    ): Boolean {
        val cr = context.contentResolver
        val calendarId = getDefaultCalendarId(cr) ?: run {
            Log.w(TAG, "No writable calendar found")
            return false
        }

        val eventValues = android.content.ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.DTSTART, beginTimeMs)
            put(CalendarContract.Events.DTEND, endTimeMs)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (!description.isNullOrBlank()) {
                put(CalendarContract.Events.DESCRIPTION, description)
            }
        }

        val eventUri = try {
            cr.insert(CalendarContract.Events.CONTENT_URI, eventValues)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert calendar event", e)
            return false
        } ?: return false

        val eventId = ContentUris.parseId(eventUri)

        val reminderValues = android.content.ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, REMINDER_MINUTES_BEFORE)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }

        return try {
            cr.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert reminder for event $eventId", e)
            false
        }
    }

    /**
     * Returns the calendar ID to use: prefers the account's primary calendar (same as "default"
     * in calendar app settings), otherwise the first visible calendar.
     */
    private fun getDefaultCalendarId(cr: android.content.ContentResolver): Long? {
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val visible = "${CalendarContract.Calendars.VISIBLE} = 1"
        // Prefer primary calendar (the one configured as default for the account)
        val primarySelection = "$visible AND ${CalendarContract.Calendars.IS_PRIMARY} = 1"
        cr.query(uri, projection, primarySelection, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        // Fallback: first visible calendar
        cr.query(uri, projection, visible, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }

    /**
     * Computes begin time in milliseconds since epoch.
     * Exactly one of [CreateReminderParams.datetime] or [CreateReminderParams.delayMinutes] should be set.
     * When both are present, [delay_minutes] takes precedence (relative time from now).
     */
    private fun computeBeginTimeMs(params: CreateReminderParams): Long? {
        val fromDelay = params.delayMinutes?.takeIf { it > 0 }?.let { min ->
            System.currentTimeMillis() + min * 60 * 1000L
        }
        if (fromDelay != null) return fromDelay
        val datetime = params.datetime?.takeIf { it.isNotBlank() } ?: return null
        return parseDatetimeToMillis(datetime)
    }

    /**
     * Parses a datetime string to millis since epoch. Supports ISO 8601, "yyyy-MM-dd HH:mm[:ss]",
     * with optional timezone offset (e.g. -03:00). Date-only (yyyy-MM-dd) is interpreted as
     * 00:00 in the device's default timezone.
     */
    private fun parseDatetimeToMillis(datetime: String): Long? {
        return try {
            val trimmed = datetime.trim()
            when {
                // With timezone offset (e.g. 2026-02-23 11:00:00-03:00 or 2026-02-23T11:00:00-03:00)
                trimmed.length >= 25 && (trimmed[19] == '+' || trimmed[19] == '-') -> {
                    if (trimmed[10] == 'T') ISO_FORMAT_OFFSET.get().parse(trimmed)?.time
                    else DATE_TIME_FORMAT_OFFSET.get().parse(trimmed)?.time
                }
                trimmed.length >= 23 && trimmed[19] == '.' -> ISO_FORMAT_MS.get().parse(trimmed)?.time
                trimmed.length >= 19 && trimmed[10] == 'T' -> ISO_FORMAT.get().parse(trimmed.substring(0, 19))?.time
                trimmed.length >= 19 && trimmed[10] == ' ' -> DATE_TIME_FORMAT_SECONDS.get().parse(trimmed.substring(0, 19))?.time
                trimmed.length >= 16 -> DATE_TIME_FORMAT.get().parse(trimmed.substring(0, 16))?.time
                trimmed.length >= 10 -> DATE_ONLY_FORMAT.get().parse(trimmed.substring(0, 10))?.time
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse datetime: $datetime", e)
            null
        }
    }

    /**
     * Fallback: opens the default calendar app with INSERT intent when direct insert fails.
     */
    private fun openCalendarInsertIntent(title: String, description: String?, beginTimeMs: Long, endTimeMs: Long) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.item/event")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(CalendarContract.Events.TITLE, title)
            if (!description.isNullOrBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, description)
            }
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTimeMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMs)
            putExtra(CalendarContract.Events.HAS_ALARM, true)
        }
        context.startActivity(intent)
    }
}
