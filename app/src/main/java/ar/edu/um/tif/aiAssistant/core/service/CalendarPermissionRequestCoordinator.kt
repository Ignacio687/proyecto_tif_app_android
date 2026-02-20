package ar.edu.um.tif.aiAssistant.core.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates one-shot requests to show the READ_CALENDAR / WRITE_CALENDAR permission dialog.
 * When [CreateReminderSkill] fails due to missing calendar permission, it calls [requestCalendarPermission];
 * the UI observes [requestCalendarPermissionLiveData] and launches the permission request, then calls [consumeRequest].
 */
@Singleton
class CalendarPermissionRequestCoordinator @Inject constructor() {

    private val _requestCalendarPermission = MutableLiveData(false)

    /** Observe this; when true, launch READ_CALENDAR and WRITE_CALENDAR permission request and then call [consumeRequest]. */
    val requestCalendarPermissionLiveData: LiveData<Boolean> = _requestCalendarPermission

    fun requestCalendarPermission() {
        _requestCalendarPermission.postValue(true)
    }

    /** Safe to call from any thread; posts to main thread. */
    fun consumeRequest() {
        _requestCalendarPermission.postValue(false)
    }
}
