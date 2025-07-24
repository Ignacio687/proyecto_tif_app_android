package ar.edu.um.tif.aiAssistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import ar.edu.um.tif.aiAssistant.ui.theme.AI_AssistantTheme
import ar.edu.um.tif.aiAssistant.core.navigation.NavigationWrapper
import ar.edu.um.tif.aiAssistant.core.state.AppStateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), LifecycleEventObserver {

    @Inject
    lateinit var appStateManager: AppStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register lifecycle observer
        lifecycle.addObserver(this)

        // Check if we need to navigate directly to assistant
        val navigateToAssistant = intent.getBooleanExtra("navigate_to_assistant", false)

        setContent {
            AI_AssistantTheme {
                NavigationWrapper(navigateToAssistant = navigateToAssistant)
            }
        }
    }

    override fun onDestroy() {
        lifecycle.removeObserver(this)
        super.onDestroy()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                // App comes to foreground
                appStateManager.setAppInForeground(true)
            }
            Lifecycle.Event.ON_STOP -> {
                // App goes to background
                appStateManager.setAppInForeground(false)
            }
            else -> {
                // Other lifecycle events we don't need to handle
            }
        }
    }
}