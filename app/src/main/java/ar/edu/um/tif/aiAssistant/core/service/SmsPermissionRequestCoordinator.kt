package ar.edu.um.tif.aiAssistant.core.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates one-shot requests to show the SEND_SMS permission dialog.
 * When [SendMessageSkill] fails due to missing permission, it calls [requestSmsPermission];
 * the UI (AssistantScreen or AssistantPopupActivity) observes [requestSmsPermissionLiveData]
 * and launches the permission request, then calls [consumeRequest].
 */
@Singleton
class SmsPermissionRequestCoordinator @Inject constructor() {

    private val _requestSmsPermission = MutableLiveData(false)

    /** Observe this; when true, launch SEND_SMS permission request and then call [consumeRequest]. */
    val requestSmsPermissionLiveData: LiveData<Boolean> = _requestSmsPermission

    fun requestSmsPermission() {
        _requestSmsPermission.postValue(true)
    }

    fun consumeRequest() {
        _requestSmsPermission.value = false
    }
}
