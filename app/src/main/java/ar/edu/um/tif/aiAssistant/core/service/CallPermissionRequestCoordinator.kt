package ar.edu.um.tif.aiAssistant.core.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates one-shot requests to show the CALL_PHONE permission dialog.
 * When [CallContactSkill] fails due to missing permission, it calls [requestCallPermission];
 * the UI (AssistantScreen or AssistantPopupActivity) observes [requestCallPermissionLiveData]
 * and launches the permission request, then calls [consumeRequest].
 */
@Singleton
class CallPermissionRequestCoordinator @Inject constructor() {

    private val _requestCallPermission = MutableLiveData(false)

    /** Observe this; when true, launch CALL_PHONE permission request and then call [consumeRequest]. */
    val requestCallPermissionLiveData: LiveData<Boolean> = _requestCallPermission

    fun requestCallPermission() {
        _requestCallPermission.postValue(true)
    }

    /** Safe to call from any thread; posts to main thread. */
    fun consumeRequest() {
        _requestCallPermission.postValue(false)
    }
}
