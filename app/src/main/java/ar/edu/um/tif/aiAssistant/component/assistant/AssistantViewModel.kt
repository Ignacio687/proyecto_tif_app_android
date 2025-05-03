package ar.edu.um.tif.aiAssistant.component.assistant

import android.Manifest
import android.app.Application
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.justai.aimybox.Aimybox
import com.justai.aimybox.components.AimyboxAssistantViewModel
import com.justai.aimybox.components.widget.Button
import kotlinx.coroutines.launch

class AssistantViewModel(val aimybox: Aimybox) : ViewModel() {
    // Delegate to AimyboxAssistantViewModel for all assistant logic
    private val delegate = AimyboxAssistantViewModel(aimybox)

    val widgets = delegate.widgets
    val aimyboxState = delegate.aimyboxState
    val soundVolumeRms = delegate.soundVolumeRms

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onAssistantButtonClick() = delegate.onAssistantButtonClick()
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onButtonClick(button: Button) = delegate.onButtonClick(button)
    fun muteAimybox() = delegate.muteAimybox()
    fun unmuteAimybox() = delegate.unmuteAimybox()
    fun setInitialPhrase(text: String) = delegate.setInitialPhrase(text)
}